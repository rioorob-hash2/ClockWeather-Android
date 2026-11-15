package com.bratik.clockweather

import android.app.Application
import com.bratik.clockweather.notify.Notifier
import com.bratik.clockweather.notify.WeatherScheduler
import com.bratik.clockweather.widgets.ClockTicker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        ClockTicker.scheduleNext(this)
        WeatherScheduler.ensurePeriodic(this)
    }
}
