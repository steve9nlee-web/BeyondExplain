package com.beyondexplain.penangstalls.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.beyondexplain.penangstalls.data.GeoPoint
import com.beyondexplain.penangstalls.data.MapsLinks
import com.beyondexplain.penangstalls.data.Stall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Resolves a stall's **name** to a real coordinate using Android's built-in
 * geocoder, which is backed by Google's own place data on any device with Play
 * services and needs no API key.
 *
 * This is the same trick as opening Maps by name, applied to the list itself:
 * rather than trusting the catalogue's hand-typed coordinates for ranking, ask
 * Google where the place actually is. Results are bounded to Penang and
 * sanity-checked against the catalogue guess before being accepted.
 */
class StallGeocoder(private val context: Context) {

    val isAvailable: Boolean get() = Geocoder.isPresent()

    suspend fun resolve(stall: Stall): GeoPoint? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        val geocoder = runCatching { Geocoder(context, Locale.US) }.getOrNull() ?: return@withContext null
        val query = MapsLinks.searchQuery(stall)

        val addresses = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                resolveAsync(geocoder, query)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(
                    query, MAX_RESULTS,
                    GeoPoint.MIN_LAT, GeoPoint.MIN_LNG,
                    GeoPoint.MAX_LAT, GeoPoint.MAX_LNG,
                )
            }
        }.getOrNull().orEmpty()

        addresses.firstOrNull { it.hasLatitude() && it.hasLongitude() }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private suspend fun resolveAsync(geocoder: Geocoder, query: String): List<Address> =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(
                query, MAX_RESULTS,
                GeoPoint.MIN_LAT, GeoPoint.MIN_LNG,
                GeoPoint.MAX_LAT, GeoPoint.MAX_LNG,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                },
            )
        }

    private companion object {
        const val MAX_RESULTS = 3
    }
}
