package com.beyondexplain.hazeindex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

data class MapState(
    val city: City,
    /** The chosen location's own reading, shown in the card pinned over the map. */
    val report: HazeReport? = null,
    val area: AreaSnapshot = AreaSnapshot(),
    val loading: Boolean = false,
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = Settings(application)
    private val repository = HazeRepository(settings)
    private val cache = ReportCache(application)

    private val _state = MutableLiveData<MapState>()
    val state: LiveData<MapState> = _state

    private var reportJob: Job? = null
    private var areaJob: Job? = null
    private var lastBounds: MapBounds? = null
    private var started = false

    /** Called once by the activity with the location the user is looking at. */
    fun start(city: City) {
        if (started) return
        started = true
        _state.value = MapState(city = city, report = cache.load(city))
        loadReport(city)
    }

    private val currentState: MapState
        get() = _state.value ?: MapState(Cities.SINGAPORE)

    private fun loadReport(city: City) {
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            try {
                val report = repository.load(city)
                cache.save(report)
                _state.value = currentState.copy(report = report, error = null)
            } catch (e: Exception) {
                _state.value = currentState.copy(error = describe(e))
            }
        }
    }

    /**
     * Reload the points drawn on the map for whatever the user has panned to. Small
     * movements are ignored so a drag does not fire a request per frame.
     */
    fun onViewportChanged(bounds: MapBounds, force: Boolean = false) {
        if (!force && lastBounds?.isNearlyTheSameAs(bounds) == true) return
        if (!force && areaJob?.isActive == true) return

        lastBounds = bounds
        areaJob?.cancel()
        _state.value = currentState.copy(loading = true)

        areaJob = viewModelScope.launch {
            try {
                val area = repository.loadArea(bounds)
                _state.value = currentState.copy(area = area, loading = false, error = null)
            } catch (e: Exception) {
                _state.value = currentState.copy(loading = false, error = describe(e))
            }
        }
    }

    /** The refresh button: the headline reading and the map points together. */
    fun refresh() {
        loadReport(currentState.city)
        lastBounds?.let { onViewportChanged(it, force = true) }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection."
        is IOException -> e.message ?: "Could not reach the air quality service."
        else -> e.message ?: "Something went wrong."
    }
}

/** Panning a few pixels should not cost a network call. */
private fun MapBounds.isNearlyTheSameAs(other: MapBounds): Boolean {
    val height = (north - south).coerceAtLeast(0.0001)
    val width = (east - west).coerceAtLeast(0.0001)
    val tolerance = 0.25
    return kotlin.math.abs(north - other.north) < height * tolerance &&
        kotlin.math.abs(south - other.south) < height * tolerance &&
        kotlin.math.abs(east - other.east) < width * tolerance &&
        kotlin.math.abs(west - other.west) < width * tolerance
}
