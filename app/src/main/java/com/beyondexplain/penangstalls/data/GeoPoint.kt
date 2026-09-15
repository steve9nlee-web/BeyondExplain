package com.beyondexplain.penangstalls.data

/** A resolved coordinate, and how far it may be trusted. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    companion object {
        /** Penang state, generously bounded. Anything outside is a bad match. */
        const val MIN_LAT = 5.10
        const val MAX_LAT = 5.62
        const val MIN_LNG = 100.10
        const val MAX_LNG = 100.62

        /**
         * A geocoded position further than this from the catalogue's own guess
         * is treated as a mis-match (a same-named place elsewhere) rather than
         * a correction. Wide enough to fix the hundreds-of-metres errors the
         * bundled coordinates actually have.
         */
        const val MAX_CORRECTION_M = 2_500.0
    }
}

/** Is this somewhere in Penang at all? */
fun GeoPoint.inPenang(): Boolean =
    latitude in GeoPoint.MIN_LAT..GeoPoint.MAX_LAT &&
        longitude in GeoPoint.MIN_LNG..GeoPoint.MAX_LNG

/**
 * Accepts [candidate] as a correction to [fallback] only when it lands in
 * Penang and is close enough to be plausibly the same place.
 */
fun acceptCorrection(candidate: GeoPoint, fallback: GeoPoint): Boolean {
    if (!candidate.inPenang()) return false
    val drift = Geo.distanceMeters(
        fallback.latitude, fallback.longitude,
        candidate.latitude, candidate.longitude,
    )
    return drift <= GeoPoint.MAX_CORRECTION_M
}
