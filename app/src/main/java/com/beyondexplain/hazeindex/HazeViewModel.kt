package com.beyondexplain.hazeindex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

data class UiState(
    val city: City,
    val report: HazeReport? = null,
    val isRefreshing: Boolean = false,
    /** Set when the most recent live fetch failed; any [report] shown is then cached. */
    val error: String? = null
)

class HazeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HazeRepository()
    private val cache = ReportCache(application)

    private val _state = MutableLiveData<UiState>()
    val state: LiveData<UiState> = _state

    private var inFlight: Job? = null

    init {
        val city = Cities.byId(cache.lastCityId) ?: Cities.SINGAPORE
        _state.value = UiState(city = city, report = cache.load(city))
    }

    val currentCity: City get() = _state.value?.city ?: Cities.SINGAPORE

    fun selectCity(city: City) {
        if (city.id == currentCity.id) return
        cache.lastCityId = city.id
        _state.value = UiState(city = city, report = cache.load(city))
        refresh()
    }

    /** Pull-to-refresh and first load both land here: always a live network read. */
    fun refresh() {
        if (inFlight?.isActive == true) return
        val city = currentCity
        _state.value = (_state.value ?: UiState(city)).copy(isRefreshing = true, error = null)

        inFlight = viewModelScope.launch {
            try {
                val report = repository.load(city)
                cache.save(report)
                _state.value = UiState(city = city, report = report, isRefreshing = false)
            } catch (e: Exception) {
                _state.value = UiState(
                    city = city,
                    report = _state.value?.report ?: cache.load(city),
                    isRefreshing = false,
                    error = describe(e)
                )
            }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection. Pull down to try again."
        is IOException -> e.message ?: "Could not reach the air quality service."
        else -> e.message ?: "Something went wrong while refreshing."
    }
}
