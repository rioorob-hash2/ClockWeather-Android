package com.bratik.clockweather.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Принимает минутный тик, смену времени/зоны, загрузку — и перерисовывает все виджеты. */
class ClockTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WidgetRender.updateAll(context)
        ClockTicker.scheduleNext(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            com.bratik.clockweather.notify.WeatherScheduler.ensurePeriodic(context)
        }
    }
}
