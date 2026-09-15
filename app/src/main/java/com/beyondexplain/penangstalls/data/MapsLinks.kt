package com.beyondexplain.penangstalls.data

import android.net.Uri

/**
 * Links into Google Maps **by place name**, not by coordinate.
 *
 * Handing Maps a `geo:lat,lng` pin would carry this app's own coordinate error
 * straight through to navigation — you would be walked to a point near the
 * stall rather than to the stall. Searching the name instead lets Google
 * resolve it against its own record of the place, which is authoritative.
 * The coordinates here are only ever used to rank the list.
 */
object MapsLinks {

    /** Keeps the search in the right city when stall names are generic. */
    private const val LOCALITY = "Penang, Malaysia"

    fun searchQuery(stall: Stall): String =
        listOf(stall.name, stall.area, LOCALITY)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

    /** Opens the place in Google Maps, resolved by name. */
    fun place(stall: Stall): Uri =
        Uri.parse("https://www.google.com/maps/search/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("query", searchQuery(stall))
            .build()

    /** Walking directions to the place, again resolved by name. */
    fun walkingDirections(stall: Stall): Uri =
        Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", searchQuery(stall))
            .appendQueryParameter("travelmode", "walking")
            .build()
}
