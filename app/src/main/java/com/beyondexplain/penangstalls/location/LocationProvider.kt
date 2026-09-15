package com.beyondexplain.penangstalls.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.beyondexplain.penangstalls.data.Fix
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Why a location fix could not be produced, in words the UI can show. */
class LocationUnavailable(message: String) : Exception(message)

/**
 * Produces the most accurate fix it can within a time budget.
 *
 * The first location a phone hands you is almost always the coarse one — a
 * cell-tower or wifi estimate that can be hundreds of metres out. GPS needs a
 * few seconds to settle. So rather than returning the first fix, this listens
 * to every available source at once, keeps whichever is most accurate, and
 * returns early only when the fix is good enough to tell a 100 m ring from a
 * 200 m one.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun locationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * @param onProgress called each time the fix improves, so the UI can show
     *   accuracy tightening rather than freezing on a spinner.
     */
    suspend fun bestFix(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        onProgress: (Fix) -> Unit = {},
    ): Fix {
        if (!hasPermission()) throw LocationUnavailable("Location permission has not been granted.")
        if (!locationEnabled()) throw LocationUnavailable("Location is switched off. Turn it on and try again.")

        // A recent, reasonably tight last-known fix gives the list something to
        // show immediately. It is only a seed — live updates still override it.
        val best = AtomicReference(seedFix()?.also(onProgress))

        val converged = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val registrations = Registrations()

                fun offer(fix: Fix) {
                    // Callbacks arrive from several providers, so swap atomically.
                    var accepted = false
                    while (true) {
                        val current = best.get()
                        if (!fix.isBetterThan(current)) break
                        if (best.compareAndSet(current, fix)) {
                            accepted = true
                            break
                        }
                    }
                    if (!accepted) return
                    onProgress(fix)
                    if (fix.accuracyMeters <= Fix.TARGET_ACCURACY_M) {
                        registrations.stop()
                        if (continuation.isActive) continuation.resume(fix)
                    }
                }

                registrations.add(startFused(::offer))
                registrations.add(startPlatform(::offer))
                continuation.invokeOnCancellation { registrations.stop() }

                if (registrations.isEmpty) {
                    registrations.stop()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

        val result = converged ?: best.get()
        return result ?: throw LocationUnavailable(
            "Could not get a location fix. Move somewhere with a clearer view of the sky and try again."
        )
    }

    /** Best recent last-known fix across providers — by accuracy, not just recency. */
    @SuppressLint("MissingPermission")
    private fun seedFix(): Fix? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        return manager.allProviders
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { now - it.time <= Fix.MAX_SEED_AGE_MS }
            .map { it.toFix() }
            .filter { it.accuracyMeters <= Fix.MAX_SEED_ACCURACY_M }
            .minByOrNull { it.accuracyMeters }
    }

    /** Streams from the fused provider. Returns null when Play services are absent. */
    @SuppressLint("MissingPermission")
    private fun startFused(onFix: (Fix) -> Unit): (() -> Unit)? = try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { onFix(it.toFix()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(true)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        ({ client.removeLocationUpdates(callback) })
    } catch (_: Throwable) {
        null
    }

    /**
     * Streams from the platform providers. GPS and network are requested
     * together and the better one wins, rather than preferring whichever
     * answers first.
     */
    @SuppressLint("MissingPermission")
    private fun startPlatform(onFix: (Fix) -> Unit): (() -> Unit)? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location.toFix())

            @Deprecated("Required on API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
        }

        val started = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .count { provider ->
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.isSuccess
            }

        if (started == 0) return null
        return { runCatching { manager.removeUpdates(listener) } }
    }

    /** Holds teardown callbacks so listeners are removed exactly once. */
    private class Registrations {
        private val stops = mutableListOf<() -> Unit>()
        private val stopped = AtomicBoolean(false)

        val isEmpty: Boolean get() = synchronized(stops) { stops.isEmpty() }

        fun add(stop: (() -> Unit)?) {
            if (stop != null) synchronized(stops) { stops += stop }
        }

        fun stop() {
            if (stopped.compareAndSet(false, true)) {
                synchronized(stops) { stops.toList() }.forEach { runCatching { it() } }
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        /** Android reports no accuracy on some older providers; treat that as useless. */
        const val UNKNOWN_ACCURACY_M = 9_999.0
    }

    private fun Location.toFix(): Fix = Fix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else UNKNOWN_ACCURACY_M,
        elapsedMillis = SystemClock.elapsedRealtime(),
        provider = provider.orEmpty(),
    )
}
