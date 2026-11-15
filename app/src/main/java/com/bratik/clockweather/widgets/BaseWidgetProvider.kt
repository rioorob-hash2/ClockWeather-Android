package com.bratik.clockweather.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.bratik.clockweather.notify.WeatherScheduler

/**
 * Общая часть трёх провайдеров: перерисовка всех экземпляров своего типа,
 * обработка тапа «обновить» и изменения размера.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    abstract val type: WidgetType

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        redraw(context)
        // Страховка: после первого добавления/переустановки минутный тик может быть не запланирован.
        try {
            ClockTicker.scheduleNext(context)
        } catch (t: Throwable) {
            // будильник недоступен — часы просто обновятся при следующем системном событии
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        redraw(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            try {
                WeatherScheduler.refreshNow(context)
            } catch (t: Throwable) {
                // WorkManager может быть недоступен во время обновления приложения
            }
            redraw(context)
        }
    }

    private fun redraw(context: Context) {
        try {
            WidgetRender.updateAll(context)
        } catch (t: Throwable) {
            // never crash the host launcher process
        }
    }

    companion object {
        /** Action тапа по виджету. */
        const val ACTION_REFRESH = "com.bratik.clockweather.REFRESH"
    }
}
