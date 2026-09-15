package com.beyondexplain.hazeindex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * The index ruler IQAir puts under the headline: one coloured step per band, with a
 * marker sitting where the current reading falls.
 *
 * Steps are drawn equal width rather than to scale. The bands cover wildly different
 * numeric spans (50 wide at the bottom, 200 at the top), so a true-to-scale ruler would
 * squeeze the part people actually live in down to nothing.
 */
class ScaleBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = dp(10f)
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
    }
    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.background)
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rect = RectF()
    private val markerPath = Path()

    private var segments: List<ScaleSegment> = IndexScale.US_AQI_SEGMENTS
    private var value: Int? = null

    fun setScale(segments: List<ScaleSegment>, value: Int?) {
        this.segments = segments.ifEmpty { IndexScale.US_AQI_SEGMENTS }
        this.value = value
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize((MARKER_HEIGHT + GAP + BAR_HEIGHT + GAP + LABEL_HEIGHT).let { dp(it).toInt() }, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (segments.isEmpty()) return

        val sidePadding = dp(SIDE_PADDING)
        val left = paddingLeft + sidePadding
        val right = width - paddingRight - sidePadding
        val usable = right - left
        if (usable <= 0f) return

        val barTop = dp(MARKER_HEIGHT + GAP)
        val barBottom = barTop + dp(BAR_HEIGHT)
        val gap = dp(2f)
        val stepWidth = usable / segments.size
        val radius = dp(BAR_HEIGHT) / 2f

        segments.forEachIndexed { index, segment ->
            segmentPaint.color = BandColors.of(context, segment.band)
            rect.set(
                left + index * stepWidth,
                barTop,
                left + (index + 1) * stepWidth - gap,
                barBottom
            )
            canvas.drawRoundRect(rect, radius, radius, segmentPaint)
        }

        drawBoundaryLabels(canvas, left, stepWidth, barBottom)
        value?.let { drawMarker(canvas, it, left, stepWidth, barTop) }
    }

    /** 0 / 50 / 100 … printed at the step edges they belong to. */
    private fun drawBoundaryLabels(canvas: Canvas, left: Float, stepWidth: Float, barBottom: Float) {
        val baseline = barBottom + dp(GAP) + labelPaint.textSize
        val boundaries = listOf(segments.first().lower) + segments.map { it.upper }

        boundaries.forEachIndexed { index, boundary ->
            val text = boundary.toString()
            val textWidth = labelPaint.measureText(text)
            val centre = left + index * stepWidth
            // Clamp the end labels so they stay inside the view instead of clipping.
            val x = (centre - textWidth / 2f).coerceIn(0f, width - textWidth)
            canvas.drawText(text, x, baseline, labelPaint)
        }
    }

    /** A rounded bubble with the reading in it, pointing down at its place on the bar. */
    private fun drawMarker(canvas: Canvas, reading: Int, left: Float, stepWidth: Float, barTop: Float) {
        val index = segments.indexOfFirst { reading <= it.upper }
            .let { if (it < 0) segments.lastIndex else it }
        val segment = segments[index]

        // How far through its own step the reading sits.
        val span = (segment.upper - segment.lower).coerceAtLeast(1)
        val within = ((reading - segment.lower).toFloat() / span).coerceIn(0f, 1f)
        val centre = left + (index + within) * stepWidth

        val text = reading.toString()
        val bubbleWidth = maxOf(markerTextPaint.measureText(text) + dp(14f), dp(30f))
        val bubbleHeight = dp(BUBBLE_HEIGHT)
        val tip = barTop - dp(1f)
        val bubbleBottom = tip - dp(TIP_HEIGHT)
        val bubbleLeft = (centre - bubbleWidth / 2f).coerceIn(0f, width - bubbleWidth)

        rect.set(bubbleLeft, bubbleBottom - bubbleHeight, bubbleLeft + bubbleWidth, bubbleBottom)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), markerPaint)

        markerPath.reset()
        markerPath.moveTo(centre - dp(5f), bubbleBottom)
        markerPath.lineTo(centre + dp(5f), bubbleBottom)
        markerPath.lineTo(centre, tip)
        markerPath.close()
        canvas.drawPath(markerPath, markerPaint)

        val textBaseline = rect.centerY() - (markerTextPaint.descent() + markerTextPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), textBaseline, markerTextPaint)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val BAR_HEIGHT = 12f
        const val BUBBLE_HEIGHT = 20f
        const val TIP_HEIGHT = 5f
        const val MARKER_HEIGHT = BUBBLE_HEIGHT + TIP_HEIGHT
        const val GAP = 6f
        const val LABEL_HEIGHT = 14f
        const val SIDE_PADDING = 2f
    }
}
