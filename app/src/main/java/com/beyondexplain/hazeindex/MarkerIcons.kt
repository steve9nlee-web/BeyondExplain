package com.beyondexplain.hazeindex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Map pins drawn at runtime: a coloured pill carrying the index value. Drawing them
 * here rather than shipping a sprite sheet means any value and any band just works.
 */
object MarkerIcons {

    fun pill(context: Context, value: Int, band: Band, emphasised: Boolean = false): Drawable {
        val density = context.resources.displayMetrics.density
        val textSize = (if (emphasised) 15f else 13f) * density
        val paddingX = (if (emphasised) 12f else 9f) * density
        val paddingY = (if (emphasised) 7f else 5f) * density
        val ringWidth = if (emphasised) 3f * density else 1.5f * density

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            color = ContextCompat.getColor(context, R.color.background)
        }

        val label = value.toString()
        val textWidth = textPaint.measureText(label)
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent

        val width = max(textWidth + paddingX * 2, textHeight + paddingX).toInt() + (ringWidth * 2).toInt()
        val height = (textHeight + paddingY * 2 + ringWidth * 2).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val body = RectF(ringWidth, ringWidth, width - ringWidth, height - ringWidth)
        val radius = body.height() / 2f

        // A ring in the surface colour keeps the pin legible over any tile.
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radius + ringWidth,
            radius + ringWidth,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (emphasised) Color.WHITE else ContextCompat.getColor(context, R.color.background)
            }
        )
        canvas.drawRoundRect(
            body,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BandColors.of(context, band) }
        )

        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, width / 2f, baseline, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
