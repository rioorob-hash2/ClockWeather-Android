package com.bratik.clockweather.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bratik.clockweather.data.Prefs
import java.util.concurrent.TimeUnit

object WeatherScheduler {

    private const val PERIODIC = "weather_periodic"
    private const val ONESHOT = "weather_now"

    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Периодическое обновление погоды с интервалом из настроек. */
    fun ensurePeriodic(context: Context) {
        val interval = Prefs.get(context).intervalMin.toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Разовое обновление «сейчас» — по тапу на виджете или при открытии настроек. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
