package com.beyondexplain.hazeindex

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BandColors {
    fun of(context: Context, band: Band): Int = ContextCompat.getColor(
        context,
        when (band) {
            Band.GOOD -> R.color.band_good
            Band.MODERATE -> R.color.band_moderate
            Band.SENSITIVE -> R.color.band_sensitive
            Band.UNHEALTHY -> R.color.band_unhealthy
            Band.VERY_UNHEALTHY -> R.color.band_very_unhealthy
            Band.HAZARDOUS -> R.color.band_hazardous
        }
    )

    /**
     * The band colour stirred into the card surface. IQAir floods the whole headline
     * with the band colour; on this dark theme a light wash reads the same way without
     * turning the card into a torch.
     */
    fun surfaceTint(context: Context, band: Band): Int = ColorUtils.blendARGB(
        ContextCompat.getColor(context, R.color.surface),
        of(context, band),
        0.16f
    )

    /** The face that goes with the band, IQAir style. Tinted with the band colour. */
    fun face(band: Band): Int = when (band) {
        Band.GOOD -> R.drawable.ic_face_good
        Band.MODERATE -> R.drawable.ic_face_moderate
        Band.SENSITIVE -> R.drawable.ic_face_sensitive
        Band.UNHEALTHY -> R.drawable.ic_face_unhealthy
        Band.VERY_UNHEALTHY -> R.drawable.ic_face_very_unhealthy
        Band.HAZARDOUS -> R.drawable.ic_face_hazardous
    }
}

object Times {

    /** Formats an epoch second in the *city's* local time, not the phone's. */
    fun cityClock(epochSeconds: Long, utcOffsetSeconds: Int, pattern: String = "d MMM, HH:mm"): String {
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        format.timeZone = fixedZone(utcOffsetSeconds)
        return format.format(Date(epochSeconds * 1000))
    }

    fun hourLabel(epochSeconds: Long, utcOffsetSeconds: Int): String =
        cityClock(epochSeconds, utcOffsetSeconds, "HH:mm")

    /** "just now", "4 min ago", "2 h ago" — how fresh the device's own copy is. */
    fun relativeToNow(epochMillis: Long): String {
        val deltaMinutes = ((System.currentTimeMillis() - epochMillis) / 60_000L).coerceAtLeast(0)
        return when {
            deltaMinutes < 1 -> "just now"
            deltaMinutes < 60 -> "$deltaMinutes min ago"
            deltaMinutes < 60 * 24 -> "${deltaMinutes / 60} h ago"
            else -> "${deltaMinutes / (60 * 24)} d ago"
        }
    }

    private fun fixedZone(utcOffsetSeconds: Int): TimeZone {
        val sign = if (utcOffsetSeconds < 0) "-" else "+"
        val total = kotlin.math.abs(utcOffsetSeconds)
        return TimeZone.getTimeZone(
            String.format(Locale.US, "GMT%s%02d:%02d", sign, total / 3600, (total % 3600) / 60)
        )
    }
}

object Numbers {
    fun concentration(value: Double?): String = decimal(value, 1)

    fun decimal(value: Double?, digits: Int = 1): String =
        if (value == null) "\u2013" else String.format(Locale.getDefault(), "%.${digits}f", value)

    /** Whole numbers where a decimal would be noise, e.g. 29°C and 12 km/h. */
    fun rounded(value: Double?): String =
        if (value == null) "\u2013" else String.format(Locale.getDefault(), "%.0f", value)
}
