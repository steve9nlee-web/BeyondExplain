package com.beyondexplain.penangstalls.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Distance maths, in pure Kotlin so it can be tested off-device. */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * Great-circle distance in metres (haversine). Within about half a metre of
     * the ellipsoidal figure at the ranges this app cares about.
     */
    fun distanceMeters(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLng / 2) * sin(dLng / 2) * cos(lat1) * cos(lat2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
