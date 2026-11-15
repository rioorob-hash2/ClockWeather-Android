package com.bratik.clockweather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Сеть Open-Meteo. Никаких сторонних библиотек — только HttpURLConnection и org.json.
 * Вся логика разбора вынесена в [OpenMeteoParser], чтобы её можно было покрыть юнит-тестами.
 */
object OpenMeteo {

    private const val GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "ClockWeather/1.0"

    private const val CURRENT_FIELDS = "temperature_2m,weather_code,is_day,precipitation"
    private const val HOURLY_FIELDS = "precipitation_probability,precipitation,temperature_2m,weather_code"
    private const val DAILY_FIELDS =
        "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max"

    /** Поиск города по названию. Пустой запрос или пустой ответ — пустой список. */
    suspend fun geocode(query: String): List<Place> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val url = buildString {
            append(GEOCODE_URL)
            append("?name=").append(encode(q))
            append("&count=5")
            append("&language=ru")
            append("&format=json")
        }
        return OpenMeteoParser.parseGeocode(httpGet(url))
    }

    /** Текущая погода + минимумы/максимумы и окно дождя на 3 часа. */
    suspend fun fetch(lat: Double, lon: Double): WeatherSnapshot {
        val url = buildString {
            append(FORECAST_URL)
            append("?latitude=").append(lat)
            append("&longitude=").append(lon)
            append("&current=").append(CURRENT_FIELDS)
            append("&hourly=").append(HOURLY_FIELDS)
            append("&daily=").append(DAILY_FIELDS)
            append("&timezone=auto")
            append("&forecast_days=7")
            append("&temperature_unit=celsius")
            append("&wind_speed_unit=ms")
        }
        val body = httpGet(url)
        return OpenMeteoParser.parseForecast(body, System.currentTimeMillis())
    }

    private fun encode(raw: String): String =
        URLEncoder.encode(raw, "UTF-8").replace("+", "%20")

    /** GET с таймаутами 15 с. На любой статус кроме 200 — IOException. */
    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // Тело ошибки не нужно, но поток надо закрыть, иначе течёт сокет.
                conn.errorStream?.use { it.readBytes() }
                throw IOException("HTTP $code")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Чистый разбор ответов Open-Meteo: ни Context, ни сети.
 * org.json входит в Android SDK, поэтому в тестах достаточно testImplementation("org.json:json").
 */
object OpenMeteoParser {

    private const val WINDOW_HOURS = 3

    /** results[]: name + admin1 (fallback country) + координаты. Мусорные записи отбрасываем. */
    fun parseGeocode(json: String): List<Place> {
        val results: JSONArray = try {
            JSONObject(json).optJSONArray("results") ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<Place>(results.length())
        for (n in 0 until results.length()) {
            val item = results.optJSONObject(n) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            val lat = item.optDouble("latitude", Double.NaN)
            val lon = item.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val region = firstNonBlank(
                item.optString("admin1", ""),
                item.optString("country", ""),
            )
            out += Place(name = name, region = region, lat = lat, lon = lon)
        }
        return out
    }

    /**
     * Разбор forecast. Тестовая точка входа: [nowMs] подставляется вместо System.currentTimeMillis(),
     * чтобы окно «следующие 3 часа» было детерминированным.
     */
    fun parseForecast(json: String, nowMs: Long): WeatherSnapshot {
        val root = JSONObject(json)
        val current = root.optJSONObject("current")
        val tempC = current?.optDouble("temperature_2m", 0.0) ?: 0.0
        val code = current?.optInt("weather_code", 0) ?: 0
        // is_day == null трактуем как «день».
        val isDay = if (current == null || current.isNull("is_day")) true
        else current.optInt("is_day", 1) == 1

        val daily = root.optJSONObject("daily")
        val minC = daily?.optJSONArray("temperature_2m_min").firstValue() ?: tempC
        val maxC = daily?.optJSONArray("temperature_2m_max").firstValue() ?: tempC

        val hourly = root.optJSONObject("hourly")
        val probs = hourly?.optJSONArray("precipitation_probability")
        val precs = hourly?.optJSONArray("precipitation")
        val start = findHourIndex(hourly?.optJSONArray("time"), nowMs)

        var maxProb = 0.0
        var maxPrec = 0.0
        var hasProb = false
        var hasPrec = false
        for (offset in 0 until WINDOW_HOURS) {
            val idx = start + offset
            val p = probs.valueAt(idx)
            if (p != null) {
                hasProb = true
                if (p > maxProb) maxProb = p
            }
            val mm = precs.valueAt(idx)
            if (mm != null) {
                hasPrec = true
                if (mm > maxPrec) maxPrec = mm
            }
        }

        return WeatherSnapshot(
            tempC = tempC,
            minC = minC,
            maxC = maxC,
            code = code,
            isDay = isDay,
            rainProb = if (hasProb) maxProb.roundToInt() else 0,
            precipMm = if (hasPrec) maxPrec else 0.0,
            updatedAt = nowMs,
            days = parseDays(daily),
            hours = parseHours(hourly, start),
        )
    }

    /** hourly.* -> 24 часа начиная с текущего (для экрана «Ближайшие 12 часов»). */
    private fun parseHours(hourly: JSONObject?, start: Int): List<HourForecast> {
        if (hourly == null) return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m")
        val codes = hourly.optJSONArray("weather_code")
        val pops = hourly.optJSONArray("precipitation_probability")
        val out = ArrayList<HourForecast>(24)
        var idx = start
        while (idx < times.length() && out.size < 24) {
            val iso = times.optString(idx, "")
            if (iso.isNotEmpty()) {
                out += HourForecast(
                    hourIso = iso,
                    tempC = temps.valueAt(idx) ?: 0.0,
                    code = codes.valueAt(idx)?.toInt() ?: 0,
                    pop = pops.valueAt(idx)?.toInt() ?: 0,
                )
            }
            idx++
        }
        return out
    }

    /** daily.* -> список дней (первый — сегодня). Битые/короткие массивы просто обрезаются. */
    private fun parseDays(daily: JSONObject?): List<DayForecast> {
        if (daily == null) return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val maxs = daily.optJSONArray("temperature_2m_max")
        val mins = daily.optJSONArray("temperature_2m_min")
        val pops = daily.optJSONArray("precipitation_probability_max")
        val out = ArrayList<DayForecast>(times.length())
        for (i in 0 until times.length()) {
            val date = times.optString(i, "")
            if (date.isEmpty()) continue
            out += DayForecast(
                date = date,
                code = codes.valueAt(i)?.toInt() ?: 0,
                maxC = maxs.valueAt(i) ?: 0.0,
                minC = mins.valueAt(i) ?: 0.0,
                pop = pops.valueAt(i)?.toInt() ?: 0,
            )
        }
        return out
    }

    /**
     * Индекс текущего часа в hourly.time. Ищем по локальному времени (так приходит timezone=auto),
     * затем по UTC — на случай если тест собран на UTC-значениях. Не нашли — 0.
     */
    private fun findHourIndex(times: JSONArray?, nowMs: Long): Int {
        if (times == null || times.length() == 0) return 0
        val keys = hourKeys(nowMs)
        for (key in keys) {
            for (n in 0 until times.length()) {
                if (times.optString(n, "") == key) return n
            }
        }
        // Последний шанс: совпадение только по дате-часу (без минут).
        val prefix = keys.firstOrNull()?.take(13) ?: return 0
        for (n in 0 until times.length()) {
            if (times.optString(n, "").startsWith(prefix)) return n
        }
        return 0
    }

    private fun hourKeys(nowMs: Long): List<String> {
        val pattern = "yyyy-MM-dd'T'HH:mm"
        val date = Date(nowMs)
        val local = SimpleDateFormat(pattern, Locale.US).format(date)
        val utcFmt = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val utc = utcFmt.format(date)
        return if (local == utc) listOf(local) else listOf(local, utc)
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (v in values) {
            val t = v?.trim().orEmpty()
            if (t.isNotEmpty() && t != "null") return t
        }
        return ""
    }

    private fun JSONArray?.firstValue(): Double? = valueAt(0)

    /** Значение по индексу или null, если индекс за границей массива либо значение null. */
    private fun JSONArray?.valueAt(index: Int): Double? {
        if (this == null || index < 0 || index >= length()) return null
        if (isNull(index)) return null
        val v = optDouble(index, Double.NaN)
        return if (v.isNaN()) null else v
    }
}
