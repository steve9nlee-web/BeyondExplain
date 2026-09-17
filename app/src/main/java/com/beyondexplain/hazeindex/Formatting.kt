package com.beyondexplain.hazeindex

import android.content.Context
import androidx.core.content.ContextCompat
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
    fun concentration(value: Double?): String =
        if (value == null) "\u2013" else String.format(Locale.getDefault(), "%.1f", value)

    fun distance(metres: Double): String = when {
        metres < 1_000 -> String.format(Locale.getDefault(), "%.0f m", metres)
        metres < 10_000 -> String.format(Locale.getDefault(), "%.1f km", metres / 1_000)
        else -> String.format(Locale.getDefault(), "%.0f km", metres / 1_000)
    }

    /** Feed codes such as "pm25" into something readable. */
    fun pollutantName(code: String): String = when (code.lowercase(Locale.US)) {
        "pm25", "pm2_5", "p2" -> "PM2.5"
        "pm10", "p1" -> "PM10"
        "o3" -> "ozone"
        "no2" -> "NO\u2082"
        "so2" -> "SO\u2082"
        "co" -> "CO"
        else -> code.uppercase(Locale.US)
    }
}
