package com.bratik.clockweather.data

import androidx.annotation.DrawableRes
import com.bratik.clockweather.R
import org.json.JSONArray
import org.json.JSONObject

/** Один день прогноза для полосы «5 дней» и экрана «7 дней». */
data class DayForecast(
    /** Дата ISO «2026-11-14». */
    val date: String,
    val code: Int,
    val maxC: Double,
    val minC: Double,
    /** Вероятность осадков за день, %. */
    val pop: Int = 0,
)

/** Один час прогноза для экрана «Ближайшие 12 часов». */
data class HourForecast(
    /** Локальное «2026-11-14T22:00». */
    val hourIso: String,
    val tempC: Double,
    val code: Int,
    /** Вероятность осадков, %. */
    val pop: Int,
)

/** Снимок текущей погоды, который виджеты и уведомления используют как источник правды. */
data class WeatherSnapshot(
    val tempC: Double,
    val minC: Double,
    val maxC: Double,
    /** WMO weather code из Open-Meteo. */
    val code: Int,
    val isDay: Boolean,
    /** Максимальная вероятность осадков в ближайшие 3 часа, %. */
    val rainProb: Int,
    /** Максимум осадков в ближайшие 3 часа, мм/ч. */
    val precipMm: Double,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Прогноз по дням (первый — сегодня). */
    val days: List<DayForecast> = emptyList(),
    /** Почасовой прогноз начиная с текущего часа (до 24 записей). */
    val hours: List<HourForecast> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("tempC", tempC)
        put("minC", minC)
        put("maxC", maxC)
        put("code", code)
        put("isDay", isDay)
        put("rainProb", rainProb)
        put("precipMm", precipMm)
        put("updatedAt", updatedAt)
        put(
            "days",
            JSONArray().apply {
                days.forEach { d ->
                    put(
                        JSONObject().apply {
                            put("date", d.date)
                            put("code", d.code)
                            put("maxC", d.maxC)
                            put("minC", d.minC)
                            put("pop", d.pop)
                        },
                    )
                }
            },
        )
        put(
            "hours",
            JSONArray().apply {
                hours.forEach { h ->
                    put(
                        JSONObject().apply {
                            put("hourIso", h.hourIso)
                            put("tempC", h.tempC)
                            put("code", h.code)
                            put("pop", h.pop)
                        },
                    )
                }
            },
        )
    }.toString()

    companion object {
        fun fromJson(raw: String?): WeatherSnapshot? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                WeatherSnapshot(
                    tempC = o.optDouble("tempC", 0.0),
                    minC = o.optDouble("minC", 0.0),
                    maxC = o.optDouble("maxC", 0.0),
                    code = o.optInt("code", 0),
                    isDay = o.optBoolean("isDay", true),
                    rainProb = o.optInt("rainProb", 0),
                    precipMm = o.optDouble("precipMm", 0.0),
                    updatedAt = o.optLong("updatedAt", 0L),
                    days = parseDays(o.optJSONArray("days")),
                    hours = parseHours(o.optJSONArray("hours")),
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun parseDays(arr: JSONArray?): List<DayForecast> {
            if (arr == null || arr.length() == 0) return emptyList()
            val out = ArrayList<DayForecast>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val date = o.optString("date", "")
                if (date.isEmpty()) continue
                out += DayForecast(
                    date = date,
                    code = o.optInt("code", 0),
                    maxC = o.optDouble("maxC", 0.0),
                    minC = o.optDouble("minC", 0.0),
                    pop = o.optInt("pop", 0),
                )
            }
            return out
        }

        private fun parseHours(arr: JSONArray?): List<HourForecast> {
            if (arr == null || arr.length() == 0) return emptyList()
            val out = ArrayList<HourForecast>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val iso = o.optString("hourIso", "")
                if (iso.isEmpty()) continue
                out += HourForecast(
                    hourIso = iso,
                    tempC = o.optDouble("tempC", 0.0),
                    code = o.optInt("code", 0),
                    pop = o.optInt("pop", 0),
                )
            }
            return out
        }
    }
}

/** Расшифровка WMO-кодов Open-Meteo + выбор иконки. */
object WeatherCodes {

    fun description(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снег"
        75 -> "Сильный снег"
        77 -> "Снежные зёрна"
        80, 81, 82 -> "Ливень"
        85, 86 -> "Снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "Погода"
    }

    /** Ключ иконки: sun / partly / cloud / rain / snow / storm / fog. */
    fun iconKey(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "sun" else "sun"
        1, 2 -> "partly"
        3 -> "cloud"
        45, 48 -> "fog"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "storm"
        else -> "cloud"
    }

    @DrawableRes
    fun drawableFor(code: Int, isDay: Boolean): Int = when (iconKey(code, isDay)) {
        "sun" -> R.drawable.wx_sun
        "partly" -> R.drawable.wx_partly
        "rain" -> R.drawable.wx_rain
        "snow" -> R.drawable.wx_snow
        "storm" -> R.drawable.wx_storm
        "fog" -> R.drawable.wx_fog
        else -> R.drawable.wx_cloud
    }

    @DrawableRes
    fun drawableForKey(key: String): Int = when (key) {
        "sun" -> R.drawable.wx_sun
        "partly" -> R.drawable.wx_partly
        "rain" -> R.drawable.wx_rain
        "snow" -> R.drawable.wx_snow
        "storm" -> R.drawable.wx_storm
        "fog" -> R.drawable.wx_fog
        else -> R.drawable.wx_cloud
    }
}

/** Место из геокодера Open-Meteo. */
data class Place(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
) {
    val label: String get() = if (region.isBlank() || region.equals(name, true)) name else "$name, $region"
}
