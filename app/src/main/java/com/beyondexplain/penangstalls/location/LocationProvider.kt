package com.beyondexplain.penangstalls.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Why a location fix could not be produced, in words the UI can show. */
class LocationUnavailable(message: String) : Exception(message)

/**
 * Gets a single "where am I right now" fix.
 *
 * Tries Google Play services' fused provider first (best accuracy, and it will
 * warm up the GPS if needed). If Play services are missing — plenty of devices
 * in the wild — it falls back to the platform [LocationManager].
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun locationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location {
        if (!hasPermission()) throw LocationUnavailable("Location permission has not been granted.")
        if (!locationEnabled()) throw LocationUnavailable("Location is switched off. Turn it on and try again.")

        fused()?.let { return it }
        lastKnown()?.let { return it }
        singleUpdate()?.let { return it }
        throw LocationUnavailable("Could not get a location fix. Step outside or try again in a moment.")
    }

    @SuppressLint("MissingPermission")
    private suspend fun fused(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setDurationMillis(15_000)
                .setMaxUpdateAgeMillis(60_000)
                .build()
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        } catch (_: Throwable) {
            // No Play services on this device — the caller falls through to LocationManager.
            continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.allProviders
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < FRESH_ENOUGH_MS }
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleUpdate(): Location? = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(location)
            }

            @Deprecated("Required on API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
        }

        runCatching {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }.onFailure { continuation.resume(null) }

        continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
    }

    private companion object {
        const val FRESH_ENOUGH_MS = 5 * 60 * 1000L
    }
}
