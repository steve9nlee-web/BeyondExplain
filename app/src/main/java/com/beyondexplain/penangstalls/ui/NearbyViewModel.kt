package com.beyondexplain.penangstalls.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondexplain.penangstalls.data.AppSettings
import com.beyondexplain.penangstalls.data.DistanceBand
import com.beyondexplain.penangstalls.data.NearbyStall
import com.beyondexplain.penangstalls.data.StallRepository
import com.beyondexplain.penangstalls.location.LocationProvider
import com.beyondexplain.penangstalls.location.LocationUnavailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Status { NeedsPermission, Locating, Ready, Failed }

data class NearbyUiState(
    val status: Status = Status.NeedsPermission,
    val bands: List<Pair<DistanceBand, List<NearbyStall>>> = emptyList(),
    val location: Location? = null,
    val message: String? = null,
    val radiusMeters: Double = AppSettings.DEFAULT_RADIUS,
    val feedUrl: String = "",
    val sourceLabel: String = "",
    val catalogSize: Int = 0,
) {
    val matchCount: Int get() = bands.sumOf { it.second.size }
}

class NearbyViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StallRepository(app)
    private val locationProvider = LocationProvider(app)
    private val settings = AppSettings(app)

    private val _state = MutableStateFlow(
        NearbyUiState(radiusMeters = settings.radiusMeters, feedUrl = settings.feedUrl)
    )
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    init {
        if (locationProvider.hasPermission()) refresh()
    }

    /** Called after the permission dialog resolves. */
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
        _state.update { it.copy(status = Status.Locating, message = null) }
        viewModelScope.launch {
            try {
                val here = locationProvider.currentLocation()
                val catalog = repository.load(settings.feedUrl, forceRefresh = forceReload)
                val nearby = repository.nearby(catalog, here, settings.radiusMeters)
                _state.update {
                    it.copy(
                        status = Status.Ready,
                        bands = nearby.groupBy { stall -> stall.band }
                            .toList()
                            .sortedBy { (band, _) -> band.ordinal },
                        location = here,
                        message = null,
                        sourceLabel = catalog.sourceLabel,
                        catalogSize = catalog.stalls.size,
                    )
                }
            } catch (error: LocationUnavailable) {
                _state.update { it.copy(status = Status.Failed, message = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(status = Status.Failed, message = error.message ?: "Something went wrong.")
                }
            }
        }
    }
}
