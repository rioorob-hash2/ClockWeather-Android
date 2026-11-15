package com.bratik.clockweather.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Минутный тик для часов в виджетах: будильник на границу следующей минуты.
 * Используем setExactAndAllowWhileIdle — иначе под Doze часы отстают.
 */
object ClockTicker {

    const val ACTION_TICK = "com.bratik.clockweather.TICK"

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ClockTickReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context, 7, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val next = now - (now % 60_000L) + 60_000L + 300L
        val pi = pending(context)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            } else {
                am.set(AlarmManager.RTC, next, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, next, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context))
    }
}
