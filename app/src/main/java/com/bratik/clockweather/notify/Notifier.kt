package com.bratik.clockweather.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bratik.clockweather.R
import com.bratik.clockweather.data.Prefs
import com.bratik.clockweather.data.WeatherCodes
import com.bratik.clockweather.data.WeatherSnapshot
import com.bratik.clockweather.ui.SettingsActivity
import com.bratik.clockweather.util.Format

object Notifier {

    const val CHANNEL_ID = "weather_alerts"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Предупреждения о погоде",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Дождь и сильный снегопад по данным Open-Meteo"
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }

    /** Показывает уведомление. Возвращает false, если нельзя (нет разрешения / отключено). */
    fun show(context: Context, kind: AlertKind, snapshot: WeatherSnapshot, prefs: Prefs): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val isRain = kind == AlertKind.RAIN
        val title = if (isRain) "Скоро дождь" else "Сильный снегопад"
        val city = prefs.cityName
        val temp = Format.temp(snapshot.tempC, prefs.fahrenheit)
        val body = buildString {
            append(WeatherCodes.description(snapshot.code))
            append(" · ")
            append(temp)
            if (isRain) {
                append(" · вероятность осадков ")
                append(snapshot.rainProb)
                append("%")
            } else {
                append(" · ")
                append(String.format(java.util.Locale.US, "%.1f", snapshot.precipMm))
                append(" мм/ч")
            }
            append(" · ")
            append(city)
        }

        val intent = Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private const val NOTIF_ID = 41
}
