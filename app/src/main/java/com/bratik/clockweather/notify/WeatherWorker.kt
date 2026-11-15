package com.bratik.clockweather.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bratik.clockweather.data.OpenMeteo
import com.bratik.clockweather.data.Prefs
import com.bratik.clockweather.widgets.WidgetRender
import kotlinx.coroutines.CancellationException

/**
 * Фоновая загрузка погоды (WorkManager): сеть → кэш в Prefs → виджеты → уведомление.
 * Сеть сама по себе не падает приложением: любая ошибка — Result.retry() до 3 попыток.
 */
class WeatherWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs.get(applicationContext)
        Log.i(TAG, "start: ${prefs.cityName} (${prefs.lat}, ${prefs.lon}), attempt=$runAttemptCount")

        val snapshot = try {
            OpenMeteo.fetch(prefs.lat, prefs.lon)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed: ${e.javaClass.simpleName}: ${e.message}", e)
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

        prefs.cachedWeather = snapshot.toJson()
        prefs.lastUpdateAt = snapshot.updatedAt
        Log.i(
            TAG,
            "ok: ${snapshot.tempC}°C code=${snapshot.code} rain=${snapshot.rainProb}% " +
                "precip=${snapshot.precipMm}mm",
        )

        // Падение рендера виджета не должно отменять успешное обновление данных.
        try {
            WidgetRender.updateAll(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "widget update failed: ${e.message}", e)
        }

        val now = System.currentTimeMillis()
        val kind = AlertRules.evaluate(snapshot, prefs.alertConfig(), now, prefs.lastAlertAt)
        if (kind != null) {
            Log.i(TAG, "alert: $kind")
            if (Notifier.show(applicationContext, kind, snapshot, prefs)) {
                prefs.lastAlertAt = now
            } else {
                Log.i(TAG, "alert not shown (disabled or no permission)")
            }
        }

        return Result.success()
    }

    private companion object {
        const val TAG = "WeatherWorker"

        /** runAttemptCount начинается с 0 → 0,1,2 = три попытки, дальше failure. */
        const val MAX_ATTEMPTS = 3
    }
}
