package com.beyondexplain.hazeindex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * A compact 24 hour PM2.5 history: one bar per hour, coloured by how bad that hour was.
 * Deliberately hand-drawn rather than pulling in a charting library — it is a few
 * rectangles and keeps the APK small.
 */
class TrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        strokeWidth = dp(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = dp(11f)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
    }

    private var points: List<HourPoint> = emptyList()
    private var utcOffsetSeconds: Int = 0
    private val barRect = RectF()

    fun setData(points: List<HourPoint>, utcOffsetSeconds: Int) {
        this.points = points
        this.utcOffsetSeconds = utcOffsetSeconds
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val labelRowHeight = dp(18f)
        val plotBottom = height - labelRowHeight
        val plotTop = dp(14f)

        if (points.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.trend_unavailable),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        // Scale to the worst hour, with a floor so a clean day does not look alarming.
        val peak = max(points.maxOf { it.pm25 }, 20.0)
        canvas.drawLine(0f, plotBottom, width.toFloat(), plotBottom, gridPaint)

        val slot = width.toFloat() / points.size
        val barWidth = slot * 0.62f
        val radius = barWidth / 2.5f

        points.forEachIndexed { index, point ->
            val fraction = (point.pm25 / peak).coerceIn(0.0, 1.0).toFloat()
            val barHeight = max(fraction * (plotBottom - plotTop), dp(2f))
            val left = index * slot + (slot - barWidth) / 2f
            barRect.set(left, plotBottom - barHeight, left + barWidth, plotBottom)
            barPaint.color = BandColors.of(context, IndexScale.forPm25(point.pm25))
            canvas.drawRoundRect(barRect, radius, radius, barPaint)
        }

        // Peak value, then the first and last hour along the bottom.
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.getString(R.string.trend_peak, Numbers.concentration(peak)),
            0f,
            plotTop - dp(3f),
            labelPaint
        )
        canvas.drawText(
            Times.hourLabel(points.first().epochSeconds, utcOffsetSeconds),
            0f,
            height - dp(4f),
            labelPaint
        )
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            Times.hourLabel(points.last().epochSeconds, utcOffsetSeconds),
            width.toFloat(),
            height - dp(4f),
            labelPaint
        )
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
