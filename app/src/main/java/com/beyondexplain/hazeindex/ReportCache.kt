package com.beyondexplain.hazeindex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the last successful reading on disk so the app opens with real numbers
 * (clearly marked as cached) instead of a spinner when the phone is offline.
 */
class ReportCache(context: Context) {

    private val prefs = context.getSharedPreferences("haze_cache", Context.MODE_PRIVATE)

    var lastCityId: String?
        get() = prefs.getString(KEY_CITY, null)
        set(value) = prefs.edit().putString(KEY_CITY, value).apply()

    /** Whether the app should keep tracking the device's position across restarts. */
    var followDevice: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW, false)
        set(value) = prefs.edit().putBoolean(KEY_FOLLOW, value).apply()

    /** The last resolved device position, so a restart shows a place name immediately. */
    fun saveDeviceCity(city: City) {
        val json = JSONObject()
            .put("name", city.name)
            .put("lat", city.latitude)
            .put("lon", city.longitude)
        prefs.edit().putString(KEY_DEVICE_CITY, json.toString()).apply()
    }

    fun loadDeviceCity(): City? {
        val raw = prefs.getString(KEY_DEVICE_CITY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Cities.fromCoordinates(
                name = json.getString("name"),
                latitude = json.getDouble("lat"),
                longitude = json.getDouble("lon")
            )
        }.getOrNull()
    }

    fun save(report: HazeReport) {
        prefs.edit().putString(keyFor(report.city), encode(report).toString()).apply()
    }

    fun load(city: City): HazeReport? {
        val raw = prefs.getString(keyFor(city), null) ?: return null
        return runCatching { decode(JSONObject(raw), city) }.getOrNull()
    }

    private fun keyFor(city: City) = "report_${city.id}"

    private fun encode(report: HazeReport): JSONObject = JSONObject().apply {
        put("indexName", report.indexName)
        put("indexValue", report.indexValue)
        put("observedAt", report.observedAtEpochSeconds)
        put("utcOffset", report.utcOffsetSeconds)
        put("fetchedAt", report.fetchedAtEpochMillis)
        put("source", report.sourceLabel)
        put("pm25", report.pollutants.pm25 ?: JSONObject.NULL)
        put("pm10", report.pollutants.pm10 ?: JSONObject.NULL)
        put("o3", report.pollutants.ozone ?: JSONObject.NULL)
        put("no2", report.pollutants.nitrogenDioxide ?: JSONObject.NULL)
        put("so2", report.pollutants.sulphurDioxide ?: JSONObject.NULL)
        put("co", report.pollutants.coMilligrams ?: JSONObject.NULL)
        put("trend", JSONArray().apply {
            report.trend.forEach { point ->
                put(JSONObject().put("t", point.epochSeconds).put("v", point.pm25))
            }
        })
        put("regions", JSONArray().apply {
            report.regions.forEach { region ->
                put(JSONObject().put("name", region.name).put("value", region.value))
            }
        })
    }

    private fun decode(json: JSONObject, city: City): HazeReport {
        val indexName = json.optString("indexName", "US AQI")
        val indexValue = json.getInt("indexValue")

        val trend = json.optJSONArray("trend").orEmpty().mapObjects {
            HourPoint(it.getLong("t"), it.getDouble("v"))
        }
        val regions = json.optJSONArray("regions").orEmpty().mapObjects {
            RegionReading(it.getString("name"), it.getInt("value"))
        }

        return HazeReport(
            city = city,
            indexName = indexName,
            indexValue = indexValue,
            band = if (indexName == "PSI") IndexScale.forPsi(indexValue) else IndexScale.forUsAqi(indexValue),
            pollutants = Pollutants(
                pm25 = json.optionalDouble("pm25"),
                pm10 = json.optionalDouble("pm10"),
                ozone = json.optionalDouble("o3"),
                nitrogenDioxide = json.optionalDouble("no2"),
                sulphurDioxide = json.optionalDouble("so2"),
                coMilligrams = json.optionalDouble("co")
            ),
            observedAtEpochSeconds = json.optLong("observedAt"),
            utcOffsetSeconds = json.optInt("utcOffset"),
            fetchedAtEpochMillis = json.optLong("fetchedAt"),
            sourceLabel = json.optString("source"),
            trend = trend,
            regions = regions,
            fromCache = true
        )
    }

    private companion object {
        const val KEY_CITY = "last_city"
        const val KEY_FOLLOW = "follow_device"
        const val KEY_DEVICE_CITY = "device_city"
    }
}

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let { runCatching { transform(it) }.getOrNull() }
    }
