package com.beyondexplain.hazeindex

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Everything the app needs from the device's own position, on the platform
 * LocationManager so there is no Play Services dependency.
 */
class DeviceLocation(private val context: Context) {

    private val manager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** False when the user has location switched off system-wide. */
    fun isLocationEnabled(): Boolean =
        manager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    /**
     * A usable fix: the cached one when it is still fresh, otherwise a live update,
     * falling back to whatever stale fix exists rather than returning nothing.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(
        maxAgeMillis: Long = FRESH_FIX_MILLIS,
        timeoutMillis: Long = FIX_TIMEOUT_MILLIS
    ): Location? {
        if (!hasPermission()) return null
        val manager = manager ?: return null

        val cached = lastKnown(manager)
        if (cached != null && System.currentTimeMillis() - cached.time <= maxAgeMillis) return cached
        if (!isLocationEnabled()) return cached

        return withTimeoutOrNull(timeoutMillis) { singleUpdate(manager) } ?: cached
    }

    /**
     * Position updates while the app is in the foreground. Throttled hard — haze data
     * is published on a coarse grid, so there is nothing to gain from chasing every metre.
     */
    @SuppressLint("MissingPermission")
    fun updates(): Flow<Location> {
        if (!hasPermission()) return emptyFlow()
        val manager = manager ?: return emptyFlow()

        return callbackFlow {
            val listener = locationListener { trySend(it) }
            val providers = providers(manager)
            if (providers.isEmpty()) {
                close()
                return@callbackFlow
            }
            providers.forEach { provider ->
                runCatching {
                    manager.requestLocationUpdates(
                        provider,
                        UPDATE_INTERVAL_MILLIS,
                        UPDATE_DISTANCE_METRES,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            }
            awaitClose { runCatching { manager.removeUpdates(listener) } }
        }
    }

    /** Human-readable name for a fix; blocking, so it runs on IO. */
    @Suppress("DEPRECATION")
    suspend fun placeName(location: Location): String = withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.subLocality ?: it.adminArea ?: it.countryName }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: context.getString(R.string.location_nearby)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        providers(manager)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun singleUpdate(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = providers(manager).firstOrNull()
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            lateinit var listener: LocationListener
            listener = locationListener { location ->
                runCatching { manager.removeUpdates(listener) }
                if (continuation.isActive) continuation.resume(location)
            }
            runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        }

    /** Network first: it fixes fast indoors and is accurate enough for a haze grid cell. */
    private fun providers(manager: LocationManager): List<String> {
        val enabled = runCatching { manager.getProviders(true) }.getOrNull().orEmpty()
        return PROVIDER_PREFERENCE.filter { it in enabled }
            .ifEmpty { enabled.filter { it != LocationManager.PASSIVE_PROVIDER } }
    }

    /**
     * LocationListener only gained default methods in API 30, so every method is
     * implemented here to stay safe back to API 24.
     */
    private fun locationListener(onLocation: (Location) -> Unit) = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Required for API < 30", ReplaceWith(""))
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private companion object {
        const val FRESH_FIX_MILLIS = 5 * 60 * 1000L
        const val FIX_TIMEOUT_MILLIS = 20 * 1000L
        const val UPDATE_INTERVAL_MILLIS = 2 * 60 * 1000L
        const val UPDATE_DISTANCE_METRES = 500f
        val PROVIDER_PREFERENCE = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
    }
}
