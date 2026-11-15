package com.bratik.clockweather.widgets

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import android.widget.RemoteViews
import com.bratik.clockweather.R
import com.bratik.clockweather.data.Prefs
import com.bratik.clockweather.data.WeatherCodes
import com.bratik.clockweather.data.WeatherSnapshot
import com.bratik.clockweather.util.Format
import java.util.Locale
import kotlin.math.max

/** Три типоразмера виджета. */
enum class WidgetType { SMALL_2X1, WIDE_4X1, BIG_4X2, BIG_4X3 }

/**
 * Сборка RemoteViews для всех экземпляров виджетов.
 * Никакого состояния: каждый вызов читает Prefs и кэш погоды заново.
 */
object WidgetRender {

    private const val MIN_ALPHA = 0.15f

    /** Перерисовать все экземпляры всех трёх провайдеров. */
    fun updateAll(context: Context) {
        val mgr = try {
            AppWidgetManager.getInstance(context)
        } catch (t: Throwable) {
            null
        } ?: return

        WidgetType.entries.forEach { type ->
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(context, providerClass(type)))
            } catch (t: Throwable) {
                IntArray(0)
            }
            ids.forEach { id ->
                try {
                    mgr.updateAppWidget(id, render(context, type, id))
                } catch (t: Throwable) {
                    // launcher может отвалиться посреди обновления — не роняем процесс
                }
            }
        }
    }

    /** Готовый RemoteViews для одного экземпляра. */
    fun render(context: Context, type: WidgetType, widgetId: Int): RemoteViews {
        val prefs = Prefs.get(context)
        val snapshot = WeatherSnapshot.fromJson(prefs.cachedWeather)
        val fahrenheit = prefs.fahrenheit
        val now = System.currentTimeMillis()

        val rv = RemoteViews(context.packageName, layoutFor(type))

        val tempText = snapshot?.let { Format.temp(it.tempC, fahrenheit) } ?: "--°"
        val minMaxText = snapshot?.let { Format.minMax(it.minC, it.maxC, fahrenheit) } ?: ""
        val descText = snapshot?.let { WeatherCodes.description(it.code) } ?: "нет данных"
        val iconRes = snapshot?.let { WeatherCodes.drawableFor(it.code, it.isDay) }
            ?: R.drawable.wx_cloud

        // Прозрачность подложки: 0..100 из настроек -> alpha 1..0, но не ниже MIN_ALPHA.
        val alpha = max(MIN_ALPHA, 1f - prefs.transparency / 100f)
        rv.setFloat(R.id.bg, "setAlpha", alpha)

        when (type) {
            WidgetType.SMALL_2X1 -> {
                rv.setTextViewText(R.id.city, prefs.cityName)
                rv.setTextViewText(R.id.temp, tempText)
                rv.setTextViewText(R.id.minmax, minMaxText)
                rv.setTextViewText(R.id.desc, descText)
                rv.setImageViewResource(R.id.icon, iconRes)
            }

            WidgetType.WIDE_4X1 -> {
                rv.setTextViewText(R.id.time, Format.time(now))
                rv.setTextViewText(R.id.date, Format.dateShort(now))
                rv.setTextViewText(R.id.temp, tempText)
                rv.setTextViewText(R.id.minmax, minMaxText)
                rv.setTextViewText(R.id.desc, descText)
                rv.setImageViewResource(R.id.icon, iconRes)
            }

            WidgetType.BIG_4X2 -> {
                rv.setTextViewText(R.id.city, prefs.cityName)
                rv.setTextViewText(R.id.time, Format.time(now))
                rv.setTextViewText(R.id.date, Format.dateShort(now))
                rv.setTextViewText(R.id.temp, tempText)
                rv.setTextViewText(R.id.minmax, minMaxText)
                rv.setTextViewText(R.id.desc, descText)
                rv.setImageViewResource(R.id.icon, iconRes)
                bindSensors(context, rv, prefs)
            }

            WidgetType.BIG_4X3 -> {
                rv.setTextViewText(R.id.city, prefs.cityName)
                rv.setTextViewText(R.id.time, Format.time(now))
                rv.setTextViewText(R.id.date, Format.dateShort(now))
                rv.setTextViewText(R.id.temp, tempText)
                rv.setTextViewText(R.id.minmax, minMaxText)
                rv.setTextViewText(R.id.desc, descText)
                rv.setImageViewResource(R.id.icon, iconRes)
                val todayPop = snapshot?.days?.firstOrNull()?.pop
                if (todayPop != null) {
                    rv.setTextViewText(R.id.today_pop, "Осадки сегодня $todayPop%")
                    rv.setViewVisibility(R.id.today_pop, View.VISIBLE)
                } else {
                    rv.setViewVisibility(R.id.today_pop, View.GONE)
                }
                bindForecast(context, rv, widgetId, snapshot, fahrenheit)
            }
        }

        // Тап по виджету — открыть экран погоды (как в оригинале).
        val pi = clickPendingIntent(context, type, widgetId)
        rv.setOnClickPendingIntent(R.id.widget_root, pi)
        rv.setOnClickPendingIntent(R.id.bg, pi)

        return rv
    }

    fun layoutFor(type: WidgetType): Int = when (type) {
        WidgetType.SMALL_2X1 -> R.layout.widget_2x1
        WidgetType.WIDE_4X1 -> R.layout.widget_4x1
        WidgetType.BIG_4X2 -> R.layout.widget_4x2
        WidgetType.BIG_4X3 -> R.layout.widget_4x3
    }

    fun providerClass(type: WidgetType): Class<out BaseWidgetProvider> = when (type) {
        WidgetType.SMALL_2X1 -> Widget2x1::class.java
        WidgetType.WIDE_4X1 -> Widget4x1::class.java
        WidgetType.BIG_4X2 -> Widget4x2::class.java
        WidgetType.BIG_4X3 -> Widget4x3::class.java
    }

    /** Полоса «5 дней»: рисуем Bitmap шириной с виджет (иначе текст в колонках не влезает). */
    private fun bindForecast(
        context: Context,
        rv: RemoteViews,
        widgetId: Int,
        snapshot: WeatherSnapshot?,
        fahrenheit: Boolean,
    ) {
        val density = context.resources.displayMetrics.density
        val widthDp = try {
            AppWidgetManager.getInstance(context)
                .getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        } catch (t: Throwable) {
            0
        }
        val widthPx = ((if (widthDp in 120..1200) widthDp else 320) * density).toInt()
        val heightPx = (104 * density).toInt()
        val bmp = ForecastStrip.render(context, snapshot, fahrenheit, widthPx, heightPx)
        if (bmp != null) {
            rv.setImageViewBitmap(R.id.forecast, bmp)
        } else {
            rv.setViewVisibility(R.id.forecast, View.GONE)
            rv.setViewVisibility(R.id.forecast_divider, View.GONE)
        }
    }

    private fun clickPendingIntent(
        context: Context,
        type: WidgetType,
        widgetId: Int,
    ): PendingIntent {
        val intent = Intent(context, com.bratik.clockweather.ui.WeatherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("from_widget", type.name)
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // --- ДАТЧИКИ (только 4×2) -------------------------------------------------

    private fun bindSensors(context: Context, rv: RemoteViews, prefs: Prefs) {
        if (!prefs.showSensors) {
            rv.setViewVisibility(R.id.sensors_row, View.GONE)
            rv.setViewVisibility(R.id.sensors_divider, View.GONE)
            return
        }
        rv.setViewVisibility(R.id.sensors_row, View.VISIBLE)
        rv.setViewVisibility(R.id.sensors_divider, View.VISIBLE)
        rv.setTextViewText(R.id.sensor_battery, "БАТАРЕЯ " + batteryPercent(context))
        rv.setTextViewText(R.id.sensor_ram, "RAM " + ramText(context))
        rv.setTextViewText(R.id.sensor_device, "УСТРОЙСТВО " + deviceTempText(context))
    }

    private fun batteryPercent(context: Context): String {
        try {
            val bm = context.getSystemService(BatteryManager::class.java)
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 0..100) return "$pct%"
        } catch (t: Throwable) {
            // ниже — запасной путь через sticky-интент
        }
        val sticky = batterySticky(context) ?: return "--"
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "--"
    }

    private fun ramText(context: Context): String {
        return try {
            val am = context.getSystemService(ActivityManager::class.java) ?: return "--"
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            Format.gb(info.availMem)
        } catch (t: Throwable) {
            "--"
        }
    }

    /** «Температура устройства» = EXTRA_TEMPERATURE / 10 (°C). Нет датчика — прочерк. */
    private fun deviceTempText(context: Context): String {
        val tenths = try {
            batterySticky(context)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
        } catch (t: Throwable) {
            Int.MIN_VALUE
        }
        if (tenths == Int.MIN_VALUE || tenths <= -1000 || tenths >= 1000) return "--"
        return String.format(Locale.US, "%.0f °C", tenths / 10.0)
    }

    @Suppress("UnspecifiedRegisterReceiverFlag", "Deprecation")
    private fun batterySticky(context: Context): Intent? = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (t: Throwable) {
        null
    }
}
