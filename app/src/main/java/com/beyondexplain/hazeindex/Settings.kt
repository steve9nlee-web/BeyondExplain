package com.beyondexplain.hazeindex

import android.content.Context

/** Which feed the app should try first. */
enum class SourceChoice(val id: String, val label: String) {
    /** Best available: ground stations when a key is configured, model data otherwise. */
    AUTO("auto", "Automatic (best available)"),
    WAQI("waqi", "aqicn.org / WAQI stations"),
    IQAIR("iqair", "IQAir AirVisual stations"),
    OPEN_METEO("open_meteo", "Open-Meteo model");

    companion object {
        fun byId(id: String?): SourceChoice = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * User-supplied API keys and source preference. Both services hand out free keys;
 * without one the app falls back to the keyless model feed.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("haze_settings", Context.MODE_PRIVATE)

    var waqiToken: String?
        get() = prefs.getString(KEY_WAQI, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_WAQI, value?.trim()).apply()

    var iqAirKey: String?
        get() = prefs.getString(KEY_IQAIR, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_IQAIR, value?.trim()).apply()

    var source: SourceChoice
        get() = SourceChoice.byId(prefs.getString(KEY_SOURCE, null))
        set(value) = prefs.edit().putString(KEY_SOURCE, value.id).apply()

    val hasStationKey: Boolean get() = waqiToken != null || iqAirKey != null

    private companion object {
        const val KEY_WAQI = "waqi_token"
        const val KEY_IQAIR = "iqair_key"
        const val KEY_SOURCE = "source"
    }
}
