package com.beyondexplain.penangstalls.data

/**
 * A location fix, kept deliberately free of Android types so the selection
 * rules below can be unit tested.
 *
 * [accuracyMeters] is the radius of 68% confidence that Android reports. A fix
 * with 500 m accuracy cannot tell a 100 m ring from a 200 m one, which is why
 * the app converges on the best fix it can get instead of taking the first.
 */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val elapsedMillis: Long,
    val provider: String = "",
) {
    /**
     * True when this fix should replace [other].
     *
     * Accuracy wins, with one exception: a fix that has gone stale is worth
     * replacing even by something slightly looser, because the person has
     * probably moved since.
     */
    fun isBetterThan(other: Fix?): Boolean {
        if (other == null) return true
        val ageGap = elapsedMillis - other.elapsedMillis
        if (ageGap > STALE_AFTER_MS) return accuracyMeters <= other.accuracyMeters * 2
        return accuracyMeters < other.accuracyMeters
    }

    /** Too coarse to trust the 100 m / 200 m rings. */
    val isCoarse: Boolean get() = accuracyMeters > USABLE_ACCURACY_M

    companion object {
        /** Stop early once a fix is at least this good; typical of a settled GPS lock. */
        const val TARGET_ACCURACY_M = 20.0

        /** Beyond this the near rings are noise, and the UI says so. */
        const val USABLE_ACCURACY_M = 50.0

        /** A last-known fix looser than this is not worth seeding with. */
        const val MAX_SEED_ACCURACY_M = 500.0

        /** A last-known fix older than this is discarded outright. */
        const val MAX_SEED_AGE_MS = 120_000L

        private const val STALE_AFTER_MS = 30_000L
    }
}
