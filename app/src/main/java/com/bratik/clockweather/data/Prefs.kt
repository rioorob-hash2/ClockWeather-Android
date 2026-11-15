package com.bratik.clockweather.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Настройки приложения в одном SharedPreferences.
 * Все ключи — приватные, наружу торчит только типизированный API.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("cw_settings", Context.MODE_PRIVATE)

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
    }

    /** Название города для показа в виджете. */
    var cityName: String
        get() = sp.getString(K_CITY, "Киев") ?: "Киев"
        set(v) = edit { putString(K_CITY, v) }

    var lat: Double
        get() = sp.getFloat(K_LAT, 50.4501f).toDouble()
        set(v) = edit { putFloat(K_LAT, v.toFloat()) }

    var lon: Double
        get() = sp.getFloat(K_LON, 30.5234f).toDouble()
        set(v) = edit { putFloat(K_LON, v.toFloat()) }

    /** true — градусы Фаренгейта. */
    var fahrenheit: Boolean
        get() = sp.getBoolean(K_FAHR, false)
        set(v) = edit { putBoolean(K_FAHR, v) }

    /** Период обновления погоды, минуты. */
    var intervalMin: Int
        get() = sp.getInt(K_INTERVAL, 30)
        set(v) = edit { putInt(K_INTERVAL, v.coerceIn(15, 180)) }

    /** Непрозрачность подложки виджета, 0..100. */
    var transparency: Int
        get() = sp.getInt(K_TRANSP, 20)
        set(v) = edit { putInt(K_TRANSP, v.coerceIn(0, 100)) }

    /** Показывать строку «батарея / RAM / температура устройства» в 4×2. */
    var showSensors: Boolean
        get() = sp.getBoolean(K_SENSORS, true)
        set(v) = edit { putBoolean(K_SENSORS, v) }

    var alertsEnabled: Boolean
        get() = sp.getBoolean(K_ALERTS, true)
        set(v) = edit { putBoolean(K_ALERTS, v) }

    /** Порог вероятности дождя, %. */
    var rainProbThreshold: Int
        get() = sp.getInt(K_RAIN, 60)
        set(v) = edit { putInt(K_RAIN, v.coerceIn(10, 100)) }

    /** Порог осадков для «сильного снегопада», мм/ч. */
    var snowMmThreshold: Float
        get() = sp.getFloat(K_SNOW_MM, 2f)
        set(v) = edit { putFloat(K_SNOW_MM, v) }

    /** Порог вероятности снега при отрицательной температуре, %. */
    var snowProbThreshold: Int
        get() = sp.getInt(K_SNOW_PROB, 70)
        set(v) = edit { putInt(K_SNOW_PROB, v.coerceIn(10, 100)) }

    /** Тихие часы: начало и конец (часы 0..23), окно может переходить через полночь. */
    var quietStart: Int
        get() = sp.getInt(K_QUIET_START, 22)
        set(v) = edit { putInt(K_QUIET_START, v.coerceIn(0, 23)) }

    var quietEnd: Int
        get() = sp.getInt(K_QUIET_END, 7)
        set(v) = edit { putInt(K_QUIET_END, v.coerceIn(0, 23)) }

    /** Антиспам: не чаще одного уведомления за N часов. */
    var antiSpamHours: Int
        get() = sp.getInt(K_ANTISPAM, 3)
        set(v) = edit { putInt(K_ANTISPAM, v.coerceIn(1, 24)) }

    /** Время последнего показанного уведомления, мс. */
    var lastAlertAt: Long
        get() = sp.getLong(K_LAST_ALERT, 0L)
        set(v) = edit { putLong(K_LAST_ALERT, v) }

    /** Время последнего успешного обновления погоды, мс. */
    var lastUpdateAt: Long
        get() = sp.getLong(K_LAST_UPDATE, 0L)
        set(v) = edit { putLong(K_LAST_UPDATE, v) }

    /** Кэш последнего снимка погоды (JSON). */
    var cachedWeather: String
        get() = sp.getString(K_CACHE, null) ?: ""
        set(v) = edit { putString(K_CACHE, v) }

    /** Когда последний раз проверяли обновления приложения, мс. */
    var lastUpdateCheckAt: Long
        get() = sp.getLong(K_LAST_CHECK, 0L)
        set(v) = edit { putLong(K_LAST_CHECK, v) }

    fun alertConfig(): AlertConfig = AlertConfig(
        enabled = alertsEnabled,
        rainProbThreshold = rainProbThreshold,
        snowMmThreshold = snowMmThreshold,
        snowProbThreshold = snowProbThreshold,
        quietStart = quietStart,
        quietEnd = quietEnd,
        antiSpamHours = antiSpamHours,
    )

    companion object {
        private const val K_CITY = "city_name"
        private const val K_LAT = "city_lat"
        private const val K_LON = "city_lon"
        private const val K_FAHR = "fahrenheit"
        private const val K_INTERVAL = "interval_min"
        private const val K_TRANSP = "transparency"
        private const val K_SENSORS = "show_sensors"
        private const val K_ALERTS = "alerts_enabled"
        private const val K_RAIN = "rain_prob"
        private const val K_SNOW_MM = "snow_mm"
        private const val K_SNOW_PROB = "snow_prob"
        private const val K_QUIET_START = "quiet_start"
        private const val K_QUIET_END = "quiet_end"
        private const val K_ANTISPAM = "antispam_hours"
        private const val K_LAST_ALERT = "last_alert_at"
        private const val K_LAST_UPDATE = "last_update_at"
        private const val K_CACHE = "cached_weather"
        private const val K_LAST_CHECK = "last_update_check_at"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}

/** Конфиг порогов уведомлений (вынесен отдельно — удобно тестировать). */
data class AlertConfig(
    val enabled: Boolean,
    val rainProbThreshold: Int,
    val snowMmThreshold: Float,
    val snowProbThreshold: Int,
    val quietStart: Int,
    val quietEnd: Int,
    val antiSpamHours: Int,
)
