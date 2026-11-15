package com.bratik.clockweather.data

import com.bratik.clockweather.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** Прогноз по дням: разбор daily и сериализация снимка вместе с днями. */
class ForecastTest {

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"))
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    private val json = """
        {
          "current": {"temperature_2m": 3.6, "weather_code": 2, "is_day": 0, "precipitation": 0.0},
          "hourly": {
            "time": ["2026-11-14T20:00", "2026-11-14T21:00"],
            "precipitation_probability": [0, 3],
            "precipitation": [0.0, 0.0]
          },
          "daily": {
            "time": ["2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19"],
            "weather_code": [3, 2, 3, 61, 61, 61],
            "temperature_2m_max": [6.4, 6.1, 5.0, 6.2, 12.3, 10.4],
            "temperature_2m_min": [2.1, 1.0, -0.4, 3.0, 6.1, 2.2]
          }
        }
    """.trimIndent()

    @Test
    fun parsesAllDailyDays() {
        val snap = OpenMeteoParser.parseForecast(json, 1763154000000L)
        assertEquals(6, snap.days.size)
        assertEquals("2026-11-14", snap.days[0].date)
        assertEquals(3, snap.days[0].code)
        assertEquals(6.4, snap.days[0].maxC, 0.001)
        assertEquals(2.1, snap.days[0].minC, 0.001)
    }

    @Test
    fun dailyMaxMinKeepDecimals() {
        val snap = OpenMeteoParser.parseForecast(json, 1763154000000L)
        assertEquals(12.3, snap.days[4].maxC, 0.001)
        assertEquals(-0.4, snap.days[2].minC, 0.001)
    }

    @Test
    fun missingDailyGivesEmptyDays() {
        val noDaily = """{"current":{"temperature_2m":1.0,"weather_code":0,"is_day":1,"precipitation":0.0}}"""
        val snap = OpenMeteoParser.parseForecast(noDaily, 1763154000000L)
        assertTrue(snap.days.isEmpty())
    }

    @Test
    fun forecastSurvivesJsonRoundTrip() {
        val snap = OpenMeteoParser.parseForecast(json, 1763154000000L)
        val restored = WeatherSnapshot.fromJson(snap.toJson())
        assertEquals(snap, restored)
        assertEquals(6, restored?.days?.size)
    }

    @Test
    fun snapshotWithoutDaysStillParses() {
        val old = """{"tempC":1.0,"minC":0.0,"maxC":2.0,"code":0,"isDay":true,
            "rainProb":0,"precipMm":0.0,"updatedAt":1763154000000}""".trimIndent()
        val snap = WeatherSnapshot.fromJson(old)
        assertEquals(1.0, snap!!.tempC, 0.001)
        assertTrue(snap.days.isEmpty())
    }

    @Test
    fun garbageJsonGivesNull() {
        assertNull(WeatherSnapshot.fromJson("не json"))
    }

    @Test
    fun dayShortIsRussianTwoLetters() {
        assertEquals("СБ", Format.dayShort("2026-11-14"))
        assertEquals("ВС", Format.dayShort("2026-11-15"))
        assertEquals("ПН", Format.dayShort("2026-11-16"))
    }

    @Test
    fun dayShortHandlesBrokenInput() {
        assertEquals("", Format.dayShort(""))
        assertEquals("", Format.dayShort("не дата"))
    }
}
