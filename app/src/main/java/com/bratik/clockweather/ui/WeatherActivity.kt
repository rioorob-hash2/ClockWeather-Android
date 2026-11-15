package com.bratik.clockweather.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bratik.clockweather.R
import com.bratik.clockweather.data.DayForecast
import com.bratik.clockweather.data.Prefs
import com.bratik.clockweather.data.WeatherCodes
import com.bratik.clockweather.data.WeatherSnapshot
import com.bratik.clockweather.databinding.ActivityWeatherBinding
import com.bratik.clockweather.notify.WeatherScheduler
import com.bratik.clockweather.util.Format
import java.util.Locale
import kotlin.math.max

/**
 * Экран погоды: открывается тапом по любому виджету.
 * Шапка с текущей погодой, лента 12 часов и список 7 дней.
 */
class WeatherActivity : AppCompatActivity() {

    private lateinit var b: ActivityWeatherBinding
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs.get(this)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        b.refresh.setOnClickListener {
            WeatherScheduler.refreshNow(this)
            handler.postDelayed({ render() }, 2500)
        }
        render()
        // Данные старше 15 минут — обновляем сразу при открытии.
        if (System.currentTimeMillis() - prefs.lastUpdateAt > 15 * 60_000L) {
            WeatherScheduler.refreshNow(this)
            handler.postDelayed({ render() }, 3000)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val snapshot = WeatherSnapshot.fromJson(prefs.cachedWeather)
        b.city.text = prefs.cityName
        b.updated.text = "обновлено " + Format.updatedAgo(
            prefs.lastUpdateAt, System.currentTimeMillis(),
        )

        if (snapshot == null) {
            b.bigTemp.text = "--°"
            b.desc.text = "нет данных — нажмите «Обновить погоду»"
            b.minmax.text = ""
            b.hourly.setData(emptyList(), prefs.fahrenheit)
            renderDays(emptyList())
            return
        }

        b.bigTemp.text = Format.temp(snapshot.tempC, prefs.fahrenheit)
        b.desc.text = WeatherCodes.description(snapshot.code)
        b.minmax.text = Format.minMax(snapshot.minC, snapshot.maxC, prefs.fahrenheit)
        b.icon.setImageResource(WeatherCodes.drawableFor(snapshot.code, snapshot.isDay))
        b.hourly.setData(snapshot.hours, prefs.fahrenheit)
        renderDays(snapshot.days)
    }

    private fun renderDays(days: List<DayForecast>) {
        b.days.removeAllViews()
        if (days.isEmpty()) {
            b.days.addView(mutedText("Прогноз появится после первого обновления"))
            return
        }
        val weekMin = days.minOf { it.minC }
        val weekMax = days.maxOf { it.maxC }
        days.forEachIndexed { i, day ->
            b.days.addView(dayRow(day, i == 0, weekMin, weekMax))
        }
    }

    private fun dayRow(day: DayForecast, today: Boolean, weekMin: Double, weekMax: Double): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }

        val dayName = if (today) "Сегодня" else Format.dayShort(day.date)
        row.addView(TextView(this).apply {
            text = dayName
            setTextColor(ContextCompat.getColor(this@WeatherActivity, android.R.color.white))
            textSize = if (today) 12f else 14f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val dayNum = day.date.takeLast(2) + "/" + day.date.substring(5, 7)
        row.addView(TextView(this).apply {
            text = dayNum
            setTextColor(0xFF8298A8.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        row.addView(ImageView(this).apply {
            setImageResource(WeatherCodes.drawableFor(day.code, true))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        })

        row.addView(TextView(this).apply {
            text = "${day.pop}%"
            setTextColor(0xFF8FB3C7.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // температурная шкала недели
        row.addView(TemperatureBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(16), 1f)
            range = Triple(day.minC, day.maxC, weekMin to weekMax)
        })

        row.addView(TextView(this).apply {
            text = Format.temp(day.maxC, prefs.fahrenheit) + "  " +
                Format.temp(day.minC, prefs.fahrenheit)
            setTextColor(ContextCompat.getColor(this@WeatherActivity, android.R.color.white))
            textSize = 13f
            gravity = Gravity.END
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        return row
    }

    private fun mutedText(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF8298A8.toInt())
        textSize = 13f
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    /** Полоска «min—max относительно недели». */
    private class TemperatureBar(context: android.content.Context) : View(context) {
        var range: Triple<Double, Double, Pair<Double, Double>>? = null

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val (dmin, dmax, week) = range ?: return
            val (wmin, wmax) = week
            val span = max(1.0, wmax - wmin)
            val h = height.toFloat()
            val y = h / 2f
            val r = h * 0.28f

            val track = GradientDrawable().apply {
                setColor(0x22FFFFFF)
                cornerRadius = r
                setBounds(0, (y - r / 2).toInt(), width, (y + r / 2).toInt())
            }
            track.draw(canvas)

            val left = ((dmin - wmin) / span * width).toFloat()
            val right = ((dmax - wmin) / span * width).toFloat()
            val bar = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                0xFF64B5F6.toInt(), 0xFFFFB74D.toInt(),
            )).apply {
                cornerRadius = r
                setBounds(left.toInt() + 2, (y - r / 2).toInt(), right.toInt() - 2, (y + r / 2).toInt())
            }
            bar.draw(canvas)
        }
    }
}
