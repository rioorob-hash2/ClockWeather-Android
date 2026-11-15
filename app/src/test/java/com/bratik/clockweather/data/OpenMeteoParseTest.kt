package com.bratik.clockweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Тесты контракта OpenMeteoParser:
 *   fun parseGeocode(json: String): List<Place>
 *   fun parseForecast(json: String, nowMs: Long): WeatherSnapshot
 *
 * ВНИМАНИЕ: файл app/src/main/java/com/bratik/clockweather/data/OpenMeteo.kt
 * пишет другой помощник параллельно. Если его ещё нет — этот тест просто не соберётся,
 * пока парсер не появится (это ожидаемо, контракт согласован).
 *
 * Таймзона фиксирована: hourly-время в JSON — локальное (Europe/Kiev), как отдаёт API
 * при timezone=auto, а nowMs считается по той же таймзоне.
 */
class OpenMeteoParseTest {

    companion object {
        init {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"))
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }

        private val TZ = TimeZone.getTimeZone("Europe/Kiev")
    }

    @Before
    fun setUp() {
        TimeZone.setDefault(TZ)
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    /** Локальное время (Europe/Kiev) -> epoch ms. */
    private fun localMs(local: String): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        fmt.timeZone = TZ
        return fmt.parse(local)!!.time
    }

    // ------------------------------------------------------------------ forecast

    private val hourlyStart = "2025-11-15T12:00"

    /**
     * Окно из 3 часов начинается с hourlyStart, значит индексы 0..2.
     * Максимумы намеренно на 2-м и 3-м элементе окна (80 на индексе 2),
     * а значения-приманки вне окна (95 на индексе 3 и 9.9 мм) не должны попасть в снимок.
     */
    private val forecastJson = """
        {
          "latitude": 50.45,
          "longitude": 30.52,
          "timezone": "Europe/Kiev",
          "current": {
            "time": "$hourlyStart",
            "temperature_2m": 14.3,
            "weather_code": 61,
            "is_day": 1,
            "precipitation": 0.2
          },
          "hourly": {
            "time": [
              "2025-11-15T12:00",
              "2025-11-15T13:00",
              "2025-11-15T14:00",
              "2025-11-15T15:00",
              "2025-11-15T16:00"
            ],
            "precipitation_probability": [10, 55, 80, 95, 20],
            "precipitation": [0.0, 1.5, 0.4, 9.9, 0.1],
            "temperature_2m": [14.3, 14.0, 13.5, 13.0, 12.5]
          },
          "daily": {
            "time": ["2025-11-15", "2025-11-16", "2025-11-17"],
            "temperature_2m_max": [15.0, 9.0, 8.0],
            "temperature_2m_min": [-1.0, 2.0, 3.0],
            "weather_code": [61, 3, 2]
          }
        }
    """.trimIndent()

    private fun parsedForecast(): WeatherSnapshot =
        // !! — чтобы тест собирался и если парсер объявит возврат nullable.
        OpenMeteoParser.parseForecast(forecastJson, localMs(hourlyStart))!!

    @Test
    fun parseForecastTakesCurrentConditions() {
        val s = parsedForecast()
        assertEquals(14.3, s.tempC, 1e-6)
        assertEquals(61, s.code)
        assertTrue("is_day=1 должен давать день", s.isDay)
    }

    @Test
    fun parseForecastTakesDayMinAndMax() {
        val s = parsedForecast()
        assertEquals(15.0, s.maxC, 1e-6)
        assertEquals(-1.0, s.minC, 1e-6)
    }

    @Test
    fun parseForecastRainProbIsMaxOverNextThreeHours() {
        // окно = [10, 55, 80]; 95 лежит уже вне окна (4-й час)
        assertEquals(80, parsedForecast().rainProb)
    }

    @Test
    fun parseForecastPrecipMmIsMaxOverNextThreeHours() {
        // окно = [0.0, 1.5, 0.4]; 9.9 лежит вне окна
        assertEquals(1.5, parsedForecast().precipMm, 1e-6)
    }

    @Test
    fun parseForecastNightFlagIsRespected() {
        val nightJson = forecastJson.replace("\"is_day\": 1", "\"is_day\": 0")
        val s = OpenMeteoParser.parseForecast(nightJson, localMs(hourlyStart))!!
        assertFalse(s.isDay)
    }

    @Test
    fun parseForecastAtEndOfHourlyArrayDoesNotThrow() {
        val json = """
            {
              "current": {
                "time": "2025-11-15T13:00",
                "temperature_2m": 12.5,
                "weather_code": 3,
                "is_day": 1,
                "precipitation": 0.1
              },
              "hourly": {
                "time": ["2025-11-15T12:00", "2025-11-15T13:00"],
                "precipitation_probability": [40, 70],
                "precipitation": [0.5, 2.0],
                "temperature_2m": [13.0, 12.5]
              },
              "daily": {
                "time": ["2025-11-15"],
                "temperature_2m_max": [15.0],
                "temperature_2m_min": [-1.0],
                "weather_code": [3]
              }
            }
        """.trimIndent()

        // now — последний элемент hourly: окно из 3 часов выходит за границу массива
        val s = OpenMeteoParser.parseForecast(json, localMs("2025-11-15T13:00"))!!
        assertEquals(12.5, s.tempC, 1e-6)
        assertTrue("вероятность должна остаться в 0..100, было ${s.rainProb}", s.rainProb in 0..100)
        assertTrue("осадки не могут быть отрицательными", s.precipMm >= 0.0)
    }

    // ------------------------------------------------------------------ geocode

    private val geocodeJson = """
        {
          "results": [
            {
              "id": 703448,
              "name": "Киев",
              "latitude": 50.45466,
              "longitude": 30.5238,
              "country": "Украина",
              "admin1": "Киевская область",
              "timezone": "Europe/Kiev"
            },
            {
              "id": 700000,
              "name": "Одесса",
              "latitude": 46.4825,
              "longitude": 30.7233,
              "country": "Украина",
              "admin1": "Одесская область"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseGeocodeMapsNameRegionAndCoordinates() {
        val places = OpenMeteoParser.parseGeocode(geocodeJson)
        assertEquals(2, places.size)

        val kyiv = places[0]
        assertEquals("Киев", kyiv.name)
        assertEquals("Киевская область", kyiv.region)
        assertEquals(50.45466, kyiv.lat, 1e-6)
        assertEquals(30.5238, kyiv.lon, 1e-6)
        // label собирается в Place: "name, region"
        assertEquals("Киев, Киевская область", kyiv.label)
    }

    @Test
    fun parseGeocodeReturnsEmptyListForEmptyOrMissingResults() {
        assertTrue(OpenMeteoParser.parseGeocode("").isEmpty())
        assertTrue(OpenMeteoParser.parseGeocode("{}").isEmpty())
        assertTrue(OpenMeteoParser.parseGeocode("""{"results":null}""").isEmpty())
    }

    // ------------------------------------------------------------------ WeatherCodes

    @Test
    fun weatherCodesClearIsSun() {
        assertEquals("Ясно", WeatherCodes.description(0))
        assertEquals("sun", WeatherCodes.iconKey(0, true))
    }

    @Test
    fun weatherCodesOvercastIsCloud() {
        assertEquals("Пасмурно", WeatherCodes.description(3))
        assertEquals("cloud", WeatherCodes.iconKey(3, true))
    }

    @Test
    fun weatherCodesRainAndSnowAndStormAndFogIcons() {
        assertEquals("rain", WeatherCodes.iconKey(61, true))
        assertEquals("snow", WeatherCodes.iconKey(71, true))
        assertEquals("storm", WeatherCodes.iconKey(95, true))
        assertEquals("fog", WeatherCodes.iconKey(45, true))
        assertEquals("partly", WeatherCodes.iconKey(2, true))
    }

    @Test
    fun weatherCodesUnknownCodeFallsBackToGeneric() {
        assertEquals("Погода", WeatherCodes.description(1234))
        assertEquals("cloud", WeatherCodes.iconKey(1234, true))
    }
}
