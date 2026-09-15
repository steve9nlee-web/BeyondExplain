package com.beyondexplain.penangstalls.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondexplain.penangstalls.data.AppSettings
import com.beyondexplain.penangstalls.data.DistanceBand
import com.beyondexplain.penangstalls.data.Fix
import com.beyondexplain.penangstalls.data.NearbyStall
import com.beyondexplain.penangstalls.data.StallCatalog
import com.beyondexplain.penangstalls.data.StallRepository
import com.beyondexplain.penangstalls.location.LocationProvider
import com.beyondexplain.penangstalls.location.LocationUnavailable
import kotlinx.coroutines.Job
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
) {
    val matchCount: Int get() = bands.sumOf { it.second.size }

    /** The fix is too loose for the near rings to mean anything. */
    val coarseFix: Boolean get() = fix?.isCoarse == true
}

class NearbyViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StallRepository(app)
    private val locationProvider = LocationProvider(app)
    private val settings = AppSettings(app)
    private var job: Job? = null

    private val _state = MutableStateFlow(
        NearbyUiState(radiusMeters = settings.radiusMeters, feedUrl = settings.feedUrl)
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

    /**
     * Re-checks the permission after the user may have changed it in system
     * settings, and re-locates if they upgraded to precise.
     */
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
                // Load the catalogue first so the list can render against the
                // provisional fix while the real one is still sharpening.
                val catalog = repository.load(settings.feedUrl, forceRefresh = forceReload)
                val fix = locationProvider.bestFix { partial -> publish(catalog, partial, refining = true) }
                publish(catalog, fix, refining = false)
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

    private fun publish(catalog: StallCatalog, fix: Fix, refining: Boolean) {
        val nearby = repository.nearby(catalog, fix, settings.radiusMeters)
        _state.update {
            it.copy(
                status = Status.Ready,
                bands = nearby.groupBy { stall -> stall.band }
                    .toList()
                    .sortedBy { (band, _) -> band.ordinal },
                fix = fix,
                refining = refining,
                message = null,
                sourceLabel = catalog.sourceLabel,
                catalogSize = catalog.stalls.size,
                usingBundledCatalogue = settings.feedUrl.isBlank(),
            )
        }
    }
}
