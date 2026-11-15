package com.bratik.clockweather.notify

import com.bratik.clockweather.data.AlertConfig
import com.bratik.clockweather.data.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Чистые JVM-тесты решателя «пора ли уведомлять» + сериализации снимка погоды.
 * Локаль/таймзона фиксируются, чтобы hourFrom() не зависел от машины.
 */
class AlertRulesTest {

    companion object {
        init {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"))
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }

        /** 2025-11-15T17:00:00Z — «полдень-плюс» по Киеву, чтобы тихие часы не мешали. */
        private const val NOW = 1_763_226_000_000L

        private const val HOUR_MS = 3_600_000L
    }

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"))
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    private fun cfg(
        enabled: Boolean = true,
        rainProbThreshold: Int = 60,
        snowMmThreshold: Float = 2f,
        snowProbThreshold: Int = 70,
        quietStart: Int = 22,
        quietEnd: Int = 7,
        antiSpamHours: Int = 3,
    ) = AlertConfig(
        enabled = enabled,
        rainProbThreshold = rainProbThreshold,
        snowMmThreshold = snowMmThreshold,
        snowProbThreshold = snowProbThreshold,
        quietStart = quietStart,
        quietEnd = quietEnd,
        antiSpamHours = antiSpamHours,
    )

    private fun snap(
        tempC: Double = 15.0,
        rainProb: Int = 0,
        precipMm: Double = 0.0,
        code: Int = 0,
    ) = WeatherSnapshot(
        tempC = tempC,
        minC = tempC - 5.0,
        maxC = tempC + 5.0,
        code = code,
        isDay = true,
        rainProb = rainProb,
        precipMm = precipMm,
        updatedAt = NOW,
    )

    // ---------- isQuietHour ----------

    @Test
    fun quietHourInsideWindowAcrossMidnight() {
        assertTrue(AlertRules.isQuietHour(23, 22, 7))
        assertTrue(AlertRules.isQuietHour(3, 22, 7))
    }

    @Test
    fun quietHourBoundariesAreHalfOpen() {
        assertTrue(AlertRules.isQuietHour(22, 22, 7))
        assertFalse(AlertRules.isQuietHour(7, 22, 7))
    }

    @Test
    fun quietHourOutsideWindowIsNotQuiet() {
        assertFalse(AlertRules.isQuietHour(12, 22, 7))
    }

    @Test
    fun quietHourWithEqualStartAndEndIsDisabled() {
        assertFalse(AlertRules.isQuietHour(10, 10, 10))
        assertFalse(AlertRules.isQuietHour(0, 0, 0))
    }

    // ---------- spamBlocked ----------

    @Test
    fun spamNotBlockedWhenNoPreviousAlert() {
        assertFalse(AlertRules.spamBlocked(NOW, 0L, 3))
    }

    @Test
    fun spamBlockedWithinAntiSpamWindow() {
        assertTrue(AlertRules.spamBlocked(NOW, NOW - 30 * 60_000L, 3))
    }

    @Test
    fun spamNotBlockedAfterAntiSpamWindow() {
        assertFalse(AlertRules.spamBlocked(NOW, NOW - 4 * HOUR_MS, 3))
    }

    // ---------- evaluate ----------

    @Test
    fun evaluateReturnsNullWhenAlertsDisabled() {
        val s = snap(tempC = 15.0, rainProb = 90)
        assertNull(AlertRules.evaluate(s, cfg(enabled = false), NOW, 0L, 12))
    }

    @Test
    fun evaluateReturnsNullDuringQuietHours() {
        val s = snap(tempC = 15.0, rainProb = 90)
        assertNull(AlertRules.evaluate(s, cfg(), NOW, 0L, 23))
    }

    @Test
    fun evaluateRainAboveThreshold() {
        val s = snap(tempC = 15.0, rainProb = 75)
        assertEquals(AlertKind.RAIN, AlertRules.evaluate(s, cfg(), NOW, 0L, 12))
    }

    @Test
    fun evaluateRainBelowThresholdIsNull() {
        val s = snap(tempC = 15.0, rainProb = 55)
        assertNull(AlertRules.evaluate(s, cfg(), NOW, 0L, 12))
    }

    @Test
    fun evaluateSnowByPrecipitationAmount() {
        val s = snap(tempC = -2.0, precipMm = 3.0, rainProb = 10)
        assertEquals(AlertKind.SNOW, AlertRules.evaluate(s, cfg(), NOW, 0L, 12))
    }

    @Test
    fun evaluateSnowByProbabilityAtFreezing() {
        val s = snap(tempC = -2.0, precipMm = 1.0, rainProb = 80)
        assertEquals(AlertKind.SNOW, AlertRules.evaluate(s, cfg(), NOW, 0L, 12))
    }

    @Test
    fun evaluateWarmSoSnowIsNotReportedOnlyRain() {
        val s = snap(tempC = 5.0, precipMm = 3.0, rainProb = 80)
        assertEquals(AlertKind.RAIN, AlertRules.evaluate(s, cfg(), NOW, 0L, 12))
    }

    @Test
    fun evaluateIsBlockedByRecentAlert() {
        val s = snap(tempC = 15.0, rainProb = 90)
        assertNull(AlertRules.evaluate(s, cfg(), NOW, NOW - 30 * 60_000L, 12))
    }

    // ---------- сериализация снимка ----------

    @Test
    fun snapshotJsonRoundTripKeepsAllFields() {
        val original = WeatherSnapshot(
            tempC = -2.5,
            minC = -4.0,
            maxC = 1.5,
            code = 71,
            isDay = false,
            rainProb = 80,
            precipMm = 1.2,
            updatedAt = NOW,
        )
        val restored = WeatherSnapshot.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun snapshotFromJsonRejectsEmptyInput() {
        assertNull(WeatherSnapshot.fromJson(null))
        assertNull(WeatherSnapshot.fromJson(""))
        assertNull(WeatherSnapshot.fromJson("   "))
    }
}
