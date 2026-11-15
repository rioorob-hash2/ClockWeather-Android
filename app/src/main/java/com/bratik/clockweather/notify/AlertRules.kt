package com.bratik.clockweather.notify

import com.bratik.clockweather.data.AlertConfig
import com.bratik.clockweather.data.WeatherSnapshot

enum class AlertKind { RAIN, SNOW }

/**
 * Чистая логика решения «пора ли уведомлять». Ни Context, ни Android-API —
 * чтобы это можно было покрыть юнит-тестами.
 */
object AlertRules {

    fun isQuietHour(hour: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start < end) hour in start until end
        else hour >= start || hour < end
    }

    fun spamBlocked(nowMs: Long, lastAlertMs: Long, antiSpamHours: Int): Boolean {
        if (lastAlertMs <= 0L) return false
        return nowMs - lastAlertMs < antiSpamHours * 3_600_000L
    }

    fun evaluate(
        snapshot: WeatherSnapshot,
        cfg: AlertConfig,
        nowMs: Long,
        lastAlertMs: Long,
        hourOfDay: Int = hourFrom(nowMs),
    ): AlertKind? {
        if (!cfg.enabled) return null
        if (isQuietHour(hourOfDay, cfg.quietStart, cfg.quietEnd)) return null
        if (spamBlocked(nowMs, lastAlertMs, cfg.antiSpamHours)) return null

        val snow = snapshot.tempC <= 0.5 &&
            (snapshot.precipMm > cfg.snowMmThreshold || snapshot.rainProb >= cfg.snowProbThreshold)
        if (snow) return AlertKind.SNOW

        if (snapshot.rainProb >= cfg.rainProbThreshold) return AlertKind.RAIN
        return null
    }

    /** Час суток по локальному времени — вынесено, чтобы тест мог подставить своё. */
    fun hourFrom(ms: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }
}
