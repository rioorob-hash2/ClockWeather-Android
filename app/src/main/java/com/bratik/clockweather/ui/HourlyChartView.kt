package com.bratik.clockweather.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bratik.clockweather.R
import com.bratik.clockweather.data.HourForecast
import com.bratik.clockweather.data.WeatherCodes
import com.bratik.clockweather.util.Format
import kotlin.math.roundToInt

/**
 * Горизонтальная лента часов: подпись часа, иконка, вероятность осадков
 * и ломаная температура. Ширина = 12 колонок, поэтому живёт в HorizontalScrollView.
 */
class HourlyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var hours: List<HourForecast> = emptyList()
    private var fahrenheit = false

    private val density = resources.displayMetrics.density
    private val colW = 84f * density

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        color = Color.parseColor("#CFE6F2")
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.roboto_medium) ?: Typeface.DEFAULT
    }
    private val nowPaint = Paint(labelPaint).apply {
        color = Color.parseColor("#FFB74D")
        typeface = ResourcesCompat.getFont(context, R.font.roboto_medium) ?: Typeface.DEFAULT_BOLD
    }
    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        color = Color.parseColor("#8FB3C7")
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.roboto_regular) ?: Typeface.DEFAULT
    }
    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.roboto_light) ?: Typeface.DEFAULT
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66D9885A")
        strokeWidth = 2.2f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    fun setData(hours: List<HourForecast>, fahrenheit: Boolean) {
        this.hours = hours.take(12)
        this.fahrenheit = fahrenheit
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (colW * hours.size.coerceAtLeast(1)).roundToInt()
        setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(0))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hours.isEmpty()) {
            canvas.drawText("нет данных", width / 2f, height / 2f, labelPaint)
            return
        }

        val iconSize = (30 * density).toInt()
        val chartTop = 96f * density
        val chartBottom = height - 10f * density

        val temps = hours.map { it.tempC }
        val minT = temps.min()
        val maxT = temps.max()
        val span = (maxT - minT).coerceAtLeast(1.0)

        val path = Path()
        var lastX = 0f
        var lastY = 0f

        hours.forEachIndexed { i, hour ->
            val cx = colW * i + colW / 2f
            val isNow = i == 0
            val label = if (isNow) "сейчас" else Format.hourShort(hour.hourIso)
            canvas.drawText(label, cx, 16f * density, if (isNow) nowPaint else labelPaint)

            val drawable = ContextCompat.getDrawable(
                context, WeatherCodes.drawableFor(hour.code, true),
            )
            if (drawable != null) {
                val left = (cx - iconSize / 2f).toInt()
                drawable.bounds = android.graphics.Rect(
                    left, (24 * density).toInt(),
                    left + iconSize, (24 * density).toInt() + iconSize,
                )
                drawable.draw(canvas)
            }

            canvas.drawText("${hour.pop}%", cx, 72f * density, popPaint)

            val k = ((hour.tempC - minT) / span).toFloat()
            val y = chartBottom - k * (chartBottom - chartTop)
            if (i == 0) {
                path.moveTo(cx, y)
                dotPaint.color = Color.parseColor("#FFB74D")
            } else {
                path.lineTo(cx, y)
                dotPaint.color = Color.WHITE
            }
            lastX = cx
            lastY = y
            canvas.drawText(
                Format.temp(hour.tempC, fahrenheit),
                cx, y - 8f * density, tempPaint,
            )
        }

        canvas.drawPath(path, linePaint)
        hours.forEachIndexed { i, hour ->
            val cx = colW * i + colW / 2f
            val k = ((hour.tempC - minT) / span).toFloat()
            val y = chartBottom - k * (chartBottom - chartTop)
            canvas.drawCircle(cx, y, 3.2f * density, dotPaint)
        }
        // «хвост» — чтобы линия не выглядела обрезанной
        if (hours.size > 1) canvas.drawPoint(lastX, lastY, dotPaint)
    }
}
