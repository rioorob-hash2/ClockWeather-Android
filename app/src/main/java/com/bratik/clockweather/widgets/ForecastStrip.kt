package com.bratik.clockweather.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bratik.clockweather.R
import com.bratik.clockweather.data.WeatherCodes
import com.bratik.clockweather.data.WeatherSnapshot
import com.bratik.clockweather.util.Format

/**
 * Полоса прогноза «5 дней» рисуется в Bitmap и ставится в ImageView.
 * Причина: пять одинаковых колонок в RemoteViews требуют layout_weight,
 * а он на части лаунчеров теряет содержимое. Bitmap — предсказуемо.
 *
 * Размеры заданы в dp (не в долях высоты): иконки должны быть крупными и
 * одинаковыми на любой высоте виджета.
 */
object ForecastStrip {

    private const val COLUMNS = 5

    private const val DAY_TEXT_SP = 12.5f
    private const val RANGE_TEXT_SP = 12.5f
    private const val ICON_DP = 54f

    fun render(
        context: Context,
        snapshot: WeatherSnapshot?,
        fahrenheit: Boolean,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val density = context.resources.displayMetrics.density
        val medium = font(context, R.font.roboto_medium, Typeface.DEFAULT_BOLD)
        val regular = font(context, R.font.roboto_medium, Typeface.DEFAULT)

        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium
            textSize = DAY_TEXT_SP * density * 1.05f
            color = 0xF2FFFFFF.toInt()
            textAlign = Paint.Align.CENTER
        }
        val dayDimPaint = Paint(dayPaint).apply {
            typeface = regular
            color = 0xCCFFFFFF.toInt()
        }
        val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textSize = RANGE_TEXT_SP * density * 1.05f
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
        }

        val days = snapshot?.days.orEmpty()
        val colW = widthPx.toFloat() / COLUMNS
        val iconSize = (ICON_DP * density).toInt()

        // Раскладка по вертикали: подпись дня, иконка по центру, min/max внизу.
        val dayBaseline = dayPaint.textSize
        val iconTop = dayBaseline + density * 4f
        val iconBottom = iconTop + iconSize
        val rangeBaseline = minOf(
            heightPx - density * 2f,
            iconBottom + rangePaint.textSize + density * 2f,
        )

        for (i in 0 until COLUMNS) {
            val cx = colW * (i + 0.5f)
            val day = days.getOrNull(i)

            if (day == null) {
                canvas.drawText("--", cx, dayBaseline, dayPaint)
                continue
            }

            canvas.drawText(
                Format.dayShort(day.date),
                cx, dayBaseline,
                if (i == 0) dayPaint else dayDimPaint,
            )

            val drawable = ContextCompat.getDrawable(
                context, WeatherCodes.drawableFor(day.code, true),
            )
            if (drawable != null) {
                val left = (cx - iconSize / 2f).toInt()
                drawable.bounds = Rect(left, iconTop.toInt(), left + iconSize, iconTop.toInt() + iconSize)
                drawable.draw(canvas)
            }

            val range = "${Format.temp(day.maxC, fahrenheit)} / ${Format.temp(day.minC, fahrenheit)}"
            canvas.drawText(range, cx, rangeBaseline, rangePaint)
        }

        return bmp
    }

    private fun font(context: Context, resId: Int, fallback: Typeface): Typeface =
        try {
            ResourcesCompat.getFont(context, resId) ?: fallback
        } catch (t: Throwable) {
            fallback
        }
}
