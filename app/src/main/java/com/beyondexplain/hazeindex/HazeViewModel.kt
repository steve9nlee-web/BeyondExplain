package com.beyondexplain.hazeindex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

data class UiState(
    val city: City,
    val report: HazeReport? = null,
    val isRefreshing: Boolean = false,
    /** True while the app is tracking the device's own position. */
    val followingDevice: Boolean = false,
    /** True while waiting on a position fix, before the network call starts. */
    val isLocating: Boolean = false,
    /** Set when the most recent live fetch failed; any [report] shown is then cached. */
    val error: String? = null
)

class HazeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = Settings(application)
    private val repository = HazeRepository(settings)
    private val cache = ReportCache(application)
    private val deviceLocation = DeviceLocation(application)

    private val _state = MutableLiveData<UiState>()
    val state: LiveData<UiState> = _state

    private var inFlight: Job? = null
    private var tracking: Job? = null
    private var lastAutoRefreshAt = 0L
    private var isForeground = false

    init {
        val following = cache.followDevice
        val city = when {
            following -> cache.loadDeviceCity() ?: Cities.LOCATING
            else -> Cities.byId(cache.lastCityId) ?: Cities.SINGAPORE
        }
        _state.value = UiState(
            city = city,
            report = cache.load(city),
            followingDevice = following
        )
    }

    val currentCity: City get() = _state.value?.city ?: Cities.SINGAPORE

    val isFollowingDevice: Boolean get() = _state.value?.followingDevice == true

    /** Exposed so the settings dialog can read and write the keys. */
    fun settings(): Settings = settings

    /** Called after the settings dialog changes a key or the preferred source. */
    fun onSourceSettingsChanged() = refresh()

    // ------------------------------------------------------------ mode switching

    /** Pin the app to one city and stop tracking. */
    /** Leave follow mode and go back to the last city the user picked by hand. */
    fun stopFollowingDevice() {
        selectCity(Cities.byId(cache.lastCityId) ?: Cities.SINGAPORE)
    }

    fun selectCity(city: City) {
        stopTracking()
        cache.followDevice = false
        cache.lastCityId = city.id
        _state.value = UiState(city = city, report = cache.load(city), followingDevice = false)
        refresh()
    }

    /**
     * Switch to the device's own position and keep following it. Called once the
     * location permission is granted.
     */
    fun followDeviceLocation() {
        cache.followDevice = true
        val city = cache.loadDeviceCity() ?: Cities.LOCATING
        _state.value = UiState(
            city = city,
            report = cache.load(city),
            followingDevice = true
        )
        refresh()
        if (isForeground) startTracking()
    }

    // ------------------------------------------------------------------ lifecycle

    fun onForeground() {
        isForeground = true
        if (isFollowingDevice) startTracking()
    }

    fun onBackground() {
        isForeground = false
        stopTracking()
    }

    // -------------------------------------------------------------------- loading

    /** Pull-to-refresh and first load both land here: always a live network read. */
    fun refresh() {
        if (inFlight?.isActive == true) return
        val following = isFollowingDevice
        _state.value = (_state.value ?: UiState(currentCity)).copy(
            isRefreshing = true,
            isLocating = following,
            error = null
        )

        inFlight = viewModelScope.launch {
            val city = if (following) resolveDeviceCity() else currentCity
            if (city == null) {
                _state.value = UiState(
                    city = currentCity,
                    report = _state.value?.report,
                    followingDevice = true,
                    error = locationProblem()
                )
                return@launch
            }

            _state.value = (_state.value ?: UiState(city)).copy(
                city = city,
                isRefreshing = true,
                isLocating = false
            )

            try {
                val report = repository.load(city)
                cache.save(report)
                _state.value = UiState(
                    city = city,
                    report = report,
                    followingDevice = following
                )
            } catch (e: Exception) {
                _state.value = UiState(
                    city = city,
                    report = _state.value?.report ?: cache.load(city),
                    followingDevice = following,
                    error = describe(e)
                )
            }
        }
    }

    /** Returns the device's position as a city, or null if no fix could be had. */
    private suspend fun resolveDeviceCity(): City? {
        val fix = deviceLocation.current() ?: return null
        val city = Cities.fromCoordinates(deviceLocation.placeName(fix), fix.latitude, fix.longitude)
        cache.saveDeviceCity(city)
        return city
    }

    // -------------------------------------------------------------------- tracking

    private fun startTracking() {
        if (tracking?.isActive == true) return
        if (!deviceLocation.hasPermission()) return

        tracking = viewModelScope.launch {
            deviceLocation.updates().collectLatest { fix ->
                val city = currentCity
                val moved = !Cities.isResolved(city) || Geo.distanceMetres(
                    city.latitude, city.longitude, fix.latitude, fix.longitude
                ) >= SIGNIFICANT_MOVE_METRES
                val settled = System.currentTimeMillis() - lastAutoRefreshAt >= MIN_AUTO_REFRESH_MILLIS

                // refresh() re-reads the fix and the place name, so nothing is resolved twice.
                if (isFollowingDevice && moved && settled) {
                    lastAutoRefreshAt = System.currentTimeMillis()
                    refresh()
                }
            }
        }
    }

    private fun stopTracking() {
        tracking?.cancel()
        tracking = null
    }

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }

    // ---------------------------------------------------------------------- errors

    private fun locationProblem(): String {
        val context = getApplication<Application>()
        return when {
            !deviceLocation.hasPermission() -> context.getString(R.string.location_permission_denied)
            !deviceLocation.isLocationEnabled() -> context.getString(R.string.location_services_off)
            else -> context.getString(R.string.location_no_fix)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection. Pull down to try again."
        is IOException -> e.message ?: "Could not reach the air quality service."
        else -> e.message ?: "Something went wrong while refreshing."
    }

    private companion object {
        /** Air quality is published on a coarse grid; below this a refresh tells you nothing new. */
        const val SIGNIFICANT_MOVE_METRES = 3_000.0
        const val MIN_AUTO_REFRESH_MILLIS = 5 * 60 * 1000L
    }
}
