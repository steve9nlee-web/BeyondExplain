package com.beyondexplain.penangstalls.data

import android.content.Context

/** User-tunable bits: how far to look, and an optional curated feed URL. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("penang_stalls", Context.MODE_PRIVATE)

    var radiusMeters: Double
        get() = prefs.getFloat(KEY_RADIUS, DEFAULT_RADIUS.toFloat()).toDouble()
        set(value) = prefs.edit().putFloat(KEY_RADIUS, value.toFloat()).apply()

    var feedUrl: String
        get() = prefs.getString(KEY_FEED_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FEED_URL, value.trim()).apply()

    companion object {
        const val DEFAULT_RADIUS = 3000.0
        val RADIUS_CHOICES = listOf(200.0, 500.0, 1000.0, 3000.0, 10_000.0)
        private const val KEY_RADIUS = "radius_meters"
        private const val KEY_FEED_URL = "feed_url"
    }
}
