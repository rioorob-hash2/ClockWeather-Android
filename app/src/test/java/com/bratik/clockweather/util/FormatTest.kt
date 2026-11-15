package com.bratik.clockweather.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Чистые JVM-тесты форматирования. Таймзона и локаль фиксируются жёстко,
 * иначе тест на время/дату зависит от машины.
 *
 * Важно: внутри `object Format` лежит `private val timeFmt = SimpleDateFormat("HH:mm", ...)`,
 * который захватывает TimeZone.getDefault() в момент создания объекта. Поэтому таймзону
 * ставим ещё до первого обращения к Format — в init-блоке companion object.
 */
class FormatTest {

    companion object {
        init {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"))
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }

        private const val NOW = 1_765_000_000_000L
    }

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"))
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    // ---------- temp ----------

    @Test
    fun tempCelsiusRoundsHalfUpToInteger() {
        assertEquals("14°", Format.temp(14.4, false))
        assertEquals("15°", Format.temp(14.6, false))
    }

    @Test
    fun tempCelsiusNegativeZeroBecomesZero() {
        // -0.4 при округлении вверх даёт 0, а не "-0"
        assertEquals("0°", Format.temp(-0.4, false))
    }

    @Test
    fun tempCelsiusNegativeKeepsMinus() {
        assertEquals("-5°", Format.temp(-5.0, false))
    }

    @Test
    fun tempFahrenheitConvertsAndRounds() {
        assertEquals("32°", Format.temp(0.0, true))
        assertEquals("212°", Format.temp(100.0, true))
    }

    // ---------- minMax ----------

    @Test
    fun minMaxPutsMaxFirst() {
        assertEquals("19° / 12°", Format.minMax(12.0, 19.4, false))
    }

    @Test
    fun minMaxWorksInFahrenheit() {
        // 19.4C -> 66.92 -> 67; 12C -> 53.6 -> 54
        assertEquals("67° / 54°", Format.minMax(12.0, 19.4, true))
    }

    // ---------- time ----------

    @Test
    fun timeIs24HourFormat() {
        // 1763226000000 = 2025-11-15T17:00:00Z = 19:00 по Киеву (EET, UTC+2).
        // 12-часовой формат дал бы "07:00" — тест ловит такую регрессию.
        assertEquals("19:00", Format.time(1763226000000L))
    }

    // ---------- dateShort ----------

    @Test
    fun dateShortIsRussianShortWeekdayAndDayOfMonth() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Kiev")).apply {
            set(2026, Calendar.NOVEMBER, 14, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 2026-11-14 — суббота
        assertEquals("сб, 14 ноября", Format.dateShort(cal.timeInMillis))
    }

    // ---------- gb ----------

    @Test
    fun gbUsesOneDecimalAndRussianUnit() {
        assertEquals("3.2 ГБ", Format.gb(3_435_973_836L))
        assertEquals("1.0 ГБ", Format.gb(1_073_741_824L))
    }

    // ---------- updatedAgo ----------

    @Test
    fun updatedAgoWithoutData() {
        assertEquals("нет данных", Format.updatedAgo(0L, NOW))
    }

    @Test
    fun updatedAgoUnderOneMinuteIsJustNow() {
        assertEquals("только что", Format.updatedAgo(NOW - 59_000L, NOW))
        assertEquals("только что", Format.updatedAgo(NOW, NOW))
    }

    @Test
    fun updatedAgoCountsMinutes() {
        assertEquals("5 мин назад", Format.updatedAgo(NOW - 5 * 60_000L, NOW))
        assertEquals("60 мин назад", Format.updatedAgo(NOW - 60 * 60_000L, NOW))
    }

    @Test
    fun updatedAgoInFutureDoesNotShowNegativeMinutes() {
        assertEquals("только что", Format.updatedAgo(NOW + 60_000L, NOW))
    }
}
