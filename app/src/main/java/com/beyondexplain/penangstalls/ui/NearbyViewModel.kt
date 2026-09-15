package com.beyondexplain.penangstalls.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondexplain.penangstalls.data.AppSettings
import com.beyondexplain.penangstalls.data.CoordinateCache
import com.beyondexplain.penangstalls.data.DistanceBand
import com.beyondexplain.penangstalls.data.Fix
import com.beyondexplain.penangstalls.data.GeoPoint
import com.beyondexplain.penangstalls.data.NearbyStall
import com.beyondexplain.penangstalls.data.StallCatalog
import com.beyondexplain.penangstalls.data.StallRepository
import com.beyondexplain.penangstalls.data.acceptCorrection
import com.beyondexplain.penangstalls.location.LocationProvider
import com.beyondexplain.penangstalls.location.LocationUnavailable
import com.beyondexplain.penangstalls.location.StallGeocoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Status { NeedsPermission, Locating, Ready, Failed }

data class NearbyUiState(
    val status: Status = Status.NeedsPermission,
    val bands: List<Pair<DistanceBand, List<NearbyStall>>> = emptyList(),
    val fix: Fix? = null,
    /** The fix is still being sharpened; distances may shift. */
    val refining: Boolean = false,
    val message: String? = null,
    val radiusMeters: Double = AppSettings.DEFAULT_RADIUS,
    val feedUrl: String = "",
    val sourceLabel: String = "",
    val catalogSize: Int = 0,
    /** False when only "Approximate" location was granted — fixes will be km-scale. */
    val preciseLocation: Boolean = true,
    val usingBundledCatalogue: Boolean = true,
    val verifyPositions: Boolean = true,
    /** How many stall positions came back from Google rather than the catalogue. */
    val verifiedCount: Int = 0,
    val verifying: Boolean = false,
    val geocoderAvailable: Boolean = true,
) {
    val matchCount: Int get() = bands.sumOf { it.second.size }

    val coarseFix: Boolean get() = fix?.isCoarse == true
}

class NearbyViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StallRepository(app)
    private val locationProvider = LocationProvider(app)
    private val geocoder = StallGeocoder(app)
    private val coordinates = CoordinateCache(app)
    private val settings = AppSettings(app)

    private var job: Job? = null
    private var catalog: StallCatalog = StallCatalog.EMPTY
    private var fix: Fix? = null

    private val _state = MutableStateFlow(
        NearbyUiState(
            radiusMeters = settings.radiusMeters,
            feedUrl = settings.feedUrl,
            verifyPositions = settings.verifyPositions,
            geocoderAvailable = geocoder.isAvailable,
        )
    )
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    init {
        if (locationProvider.hasPermission()) refresh()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            refresh()
        } else {
            _state.update {
                it.copy(
                    status = Status.NeedsPermission,
                    message = "Location access is needed to work out which stalls are near you.",
                )
            }
        }
    }

    fun onReturnToForeground() {
        if (!locationProvider.hasPermission()) return
        val precise = locationProvider.hasPrecisePermission()
        val upgraded = precise && !_state.value.preciseLocation
        _state.update { it.copy(preciseLocation = precise) }
        if (upgraded) refresh()
    }

    fun setRadius(meters: Double) {
        settings.radiusMeters = meters
        _state.update { it.copy(radiusMeters = meters) }
        if (locationProvider.hasPermission()) refresh()
    }

    fun setFeedUrl(url: String) {
        settings.feedUrl = url
        _state.update { it.copy(feedUrl = settings.feedUrl) }
        if (locationProvider.hasPermission()) refresh(forceReload = true)
    }

    fun setVerifyPositions(enabled: Boolean) {
        settings.verifyPositions = enabled
        _state.update { it.copy(verifyPositions = enabled) }
        if (locationProvider.hasPermission()) refresh()
    }

    /** Drops every resolved coordinate so names are looked up again. */
    fun clearResolvedPositions() {
        coordinates.clear()
        if (locationProvider.hasPermission()) refresh()
    }

    fun refresh(forceReload: Boolean = false) {
        job?.cancel()
        _state.update {
            it.copy(
                status = Status.Locating,
                message = null,
                refining = true,
                preciseLocation = locationProvider.hasPrecisePermission(),
            )
        }
        job = viewModelScope.launch {
            try {
                catalog = repository.withResolvedCoordinates(
                    repository.load(settings.feedUrl, forceRefresh = forceReload),
                    coordinates,
                )
                fix = locationProvider.bestFix { partial ->
                    fix = partial
                    publish(refining = true)
                }
                publish(refining = false)
                if (settings.verifyPositions) resolveNames()
            } catch (error: LocationUnavailable) {
                _state.update { it.copy(status = Status.Failed, refining = false, message = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        status = Status.Failed,
                        refining = false,
                        message = error.message ?: "Something went wrong.",
                    )
                }
            }
        }
    }

    /**
     * Asks Google where each stall actually is, by name. Runs after the list is
     * already on screen and republishes as answers arrive, so a slow or absent
     * geocoder never blocks the results.
     */
    private suspend fun resolveNames() {
        if (!geocoder.isAvailable) return
        val pending = catalog.stalls.filter { !it.verified && !coordinates.isUnresolvable(it.id) }
        if (pending.isEmpty()) return

        _state.update { it.copy(verifying = true) }
        try {
            pending.forEach { stall ->
                val fallback = GeoPoint(stall.latitude, stall.longitude)
                val resolved = geocoder.resolve(stall)
                if (resolved != null && acceptCorrection(resolved, fallback)) {
                    coordinates.put(stall.id, resolved)
                    catalog = repository.withResolvedCoordinates(catalog, coordinates)
                    publish(refining = false)
                } else {
                    // Either no match, or a match somewhere else entirely - keep
                    // the catalogue guess and stop asking about this one.
                    coordinates.markUnresolvable(stall.id)
                }
                delay(GEOCODE_SPACING_MS)
            }
        } finally {
            _state.update { it.copy(verifying = false) }
        }
    }

    private fun publish(refining: Boolean) {
        val current = fix ?: return
        val nearby = repository.nearby(catalog, current, settings.radiusMeters)
        _state.update {
            it.copy(
                status = Status.Ready,
                bands = nearby.groupBy { stall -> stall.band }
                    .toList()
                    .sortedBy { (band, _) -> band.ordinal },
                fix = current,
                refining = refining,
                message = null,
                sourceLabel = catalog.sourceLabel,
                catalogSize = catalog.stalls.size,
                usingBundledCatalogue = settings.feedUrl.isBlank(),
                verifiedCount = catalog.stalls.count { stall -> stall.verified },
            )
        }
    }

    private companion object {
        /** Gentle spacing so the geocoder is not rate limited. */
        const val GEOCODE_SPACING_MS = 250L
    }
}
