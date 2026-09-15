package com.beyondexplain.penangstalls.data

/** A single food stall / hawker spot that can be shown on the nearby list. */
data class Stall(
    val id: String,
    val name: String,
    val category: String,
    val area: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String = "",
    /** What to search for on the Penang Foodie page. Falls back to the stall name. */
    val facebookQuery: String = "",
) {
    val searchTerm: String get() = facebookQuery.ifBlank { name }
}

/**
 * Distance rings the results are grouped into. The first two rings are the
 * "100m to 200m" the app is built around; the rest are the "and up".
 */
enum class DistanceBand(val label: String, val maxMeters: Double) {
    WITHIN_100("Right here — within 100 m", 100.0),
    FROM_100_TO_200("100 m – 200 m away", 200.0),
    FROM_200_TO_500("200 m – 500 m away", 500.0),
    FROM_500_TO_1000("500 m – 1 km away", 1000.0),
    BEYOND_1KM("Over 1 km away", Double.MAX_VALUE);

    companion object {
        fun of(meters: Double): DistanceBand = entries.first { meters <= it.maxMeters }
    }
}

/** A stall paired with how far it is from the device's current fix. */
data class NearbyStall(
    val stall: Stall,
    val meters: Double,
) {
    val band: DistanceBand get() = DistanceBand.of(meters)

    /** "85 m" below a kilometre, "1.4 km" above it. */
    val readableDistance: String
        get() = if (meters < 1000) "${meters.toInt()} m" else String.format("%.1f km", meters / 1000)
}
