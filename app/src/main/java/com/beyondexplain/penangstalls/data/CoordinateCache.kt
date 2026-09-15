package com.beyondexplain.penangstalls.data

import android.content.Context

/**
 * Remembers coordinates resolved from place names, so the geocoder runs once
 * per stall rather than on every refresh.
 */
class CoordinateCache(context: Context) {

    private val prefs = context.getSharedPreferences("stall_coordinates", Context.MODE_PRIVATE)

    operator fun get(stallId: String): GeoPoint? {
        val raw = prefs.getString(stallId, null) ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }

    fun put(stallId: String, point: GeoPoint) {
        prefs.edit().putString(stallId, "${point.latitude},${point.longitude}").apply()
    }

    /** Records that a name could not be resolved, so it isn't retried every time. */
    fun markUnresolvable(stallId: String) {
        prefs.edit().putBoolean(unresolvableKey(stallId), true).apply()
    }

    fun isUnresolvable(stallId: String): Boolean = prefs.getBoolean(unresolvableKey(stallId), false)

    fun clear() = prefs.edit().clear().apply()

    private fun unresolvableKey(stallId: String) = "$stallId::unresolvable"
}
