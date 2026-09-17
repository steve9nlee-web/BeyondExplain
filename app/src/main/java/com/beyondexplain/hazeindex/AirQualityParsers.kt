package com.beyondexplain.hazeindex

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Response parsing, kept apart from the HTTP so every feed can be unit tested
 * against a captured response instead of only being exercised on a phone.
 */

/** Singapore's official PSI, before it is folded into a report. */
data class PsiSnapshot(
    val national: Int,
    val regions: List<RegionReading>,
    val pm25: Double?,
    val pm10: Double?,
    val observedAtEpochSeconds: Long,
    /** The same regions positioned on the map, when the feed carries label locations. */
    val regionPoints: List<AreaReading> = emptyList()
)

/**
 * aqicn.org / World Air Quality Index. Reports the nearest real monitoring
 * station, which is why it tracks local haze better than a gridded model.
 *
 * Note the `iaqi` values are per-pollutant AQI sub-indices, not concentrations.
 */
object WaqiParser {

    fun parse(body: String, city: City): HazeReport {
        val root = JSONObject(body)
        when (root.optString("status")) {
            "ok" -> Unit
            // On failure `data` is a plain string such as "Invalid key".
            else -> throw IOException(
                root.optString("data").ifBlank { "aqicn.org rejected the request" }
                    .let { if (it == "Invalid key") "aqicn.org token is not valid" else it }
            )
        }

        val data = root.optJSONObject("data") ?: throw IOException("aqicn.org returned no data")
        val aqi = data.optionalDouble("aqi")?.toInt()
            ?: throw IOException("No station index available near this location")

        val iaqi = data.optJSONObject("iaqi")
        val time = data.optJSONObject("time")
        val iso = time?.optString("iso").orEmpty()
        val observedAt = parseIso8601Seconds(iso) ?: (System.currentTimeMillis() / 1000)
        val offset = parseIso8601OffsetSeconds(time?.optString("tz"))
            ?: parseIso8601OffsetSeconds(iso)
            ?: 0

        val stationCity = data.optJSONObject("city")
        val geo = stationCity?.optJSONArray("geo")
        val distance = geo?.takeIf { it.length() >= 2 }?.let { coordinates ->
            Geo.distanceMetres(
                city.latitude, city.longitude,
                coordinates.optDouble(0), coordinates.optDouble(1)
            )
        }

        return HazeReport(
            city = city,
            indexName = "AQI",
            indexValue = aqi,
            band = IndexScale.forUsAqi(aqi),
            pollutants = Pollutants(
                pm25 = iaqi.subIndex("pm25"),
                pm10 = iaqi.subIndex("pm10"),
                ozone = iaqi.subIndex("o3"),
                nitrogenDioxide = iaqi.subIndex("no2"),
                sulphurDioxide = iaqi.subIndex("so2"),
                coMilligrams = iaqi.subIndex("co")
            ),
            observedAtEpochSeconds = observedAt,
            utcOffsetSeconds = offset,
            fetchedAtEpochMillis = System.currentTimeMillis(),
            sourceLabel = "aqicn.org (World Air Quality Index)",
            measured = true,
            stationName = stationCity?.optString("name")?.takeIf { it.isNotBlank() },
            stationDistanceMetres = distance,
            dominantPollutant = data.optString("dominentpol").takeIf { it.isNotBlank() },
            pollutantUnit = PollutantUnit.AQI
        )
    }

    private fun JSONObject?.subIndex(key: String): Double? =
        this?.optJSONObject(key)?.optionalDouble("v")
}

/**
 * IQAir AirVisual. The free tier returns the nearest station's overall AQI and the
 * dominant pollutant, but no concentrations — those are filled in from elsewhere.
 */
object IqAirParser {

    fun parse(body: String, city: City): HazeReport {
        val root = JSONObject(body)
        if (root.optString("status") != "success") {
            val message = root.optJSONObject("data")?.optString("message").orEmpty()
            throw IOException(message.ifBlank { "IQAir rejected the request" })
        }

        val data = root.optJSONObject("data") ?: throw IOException("IQAir returned no data")
        val pollution = data.optJSONObject("current")?.optJSONObject("pollution")
            ?: throw IOException("IQAir returned no current pollution block")
        val aqi = pollution.optionalDouble("aqius")?.toInt()
            ?: throw IOException("IQAir returned no US AQI value")

        val coordinates = data.optJSONObject("location")?.optJSONArray("coordinates")
        val distance = coordinates?.takeIf { it.length() >= 2 }?.let {
            // GeoJSON order: longitude first.
            Geo.distanceMetres(city.latitude, city.longitude, it.optDouble(1), it.optDouble(0))
        }

        val stationName = listOfNotNull(
            data.optString("city").takeIf { it.isNotBlank() },
            data.optString("state").takeIf { it.isNotBlank() }
        ).distinct().joinToString(", ").takeIf { it.isNotBlank() }

        return HazeReport(
            city = city,
            indexName = "US AQI",
            indexValue = aqi,
            band = IndexScale.forUsAqi(aqi),
            pollutants = Pollutants(),
            observedAtEpochSeconds = parseIso8601Seconds(pollution.optString("ts"))
                ?: (System.currentTimeMillis() / 1000),
            utcOffsetSeconds = 0,
            fetchedAtEpochMillis = System.currentTimeMillis(),
            sourceLabel = "IQAir AirVisual",
            measured = true,
            stationName = stationName,
            stationDistanceMetres = distance,
            dominantPollutant = pollution.optString("mainus").takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Open-Meteo serves CAMS model output on a roughly 11 km grid. It needs no key and
 * covers everywhere, but it is a simulation — it is the fallback, and the source of
 * the hourly trend line.
 */
object OpenMeteoParser {

    fun parse(body: String, city: City): HazeReport {
        val root = JSONObject(body)
        if (root.optBoolean("error")) {
            throw IOException(root.optString("reason", "Open-Meteo rejected the request"))
        }

        // `current` is the normal path; falling back to the most recent hourly slot keeps
        // the app working if the feed ever stops serving a current block.
        val current = root.optJSONObject("current")
            ?: latestHourlyAsCurrent(root.optJSONObject("hourly"))
            ?: throw IOException("Open-Meteo returned no current reading")
        val utcOffset = root.optInt("utc_offset_seconds", 0)
        val observedAt = current.optLong("time", System.currentTimeMillis() / 1000)

        val pollutants = Pollutants(
            pm25 = current.optionalDouble("pm2_5"),
            pm10 = current.optionalDouble("pm10"),
            ozone = current.optionalDouble("ozone"),
            nitrogenDioxide = current.optionalDouble("nitrogen_dioxide"),
            sulphurDioxide = current.optionalDouble("sulphur_dioxide"),
            coMilligrams = current.optionalDouble("carbon_monoxide")?.div(1000.0)
        )

        val aqi = current.optionalDouble("us_aqi")?.toInt()
            ?: pollutants.pm25?.let { UsAqi.fromPm25(it) }
            ?: throw IOException("No air quality index available for this location")

        return HazeReport(
            city = city,
            indexName = "US AQI",
            indexValue = aqi,
            band = IndexScale.forUsAqi(aqi),
            pollutants = pollutants,
            observedAtEpochSeconds = observedAt,
            utcOffsetSeconds = utcOffset,
            fetchedAtEpochMillis = System.currentTimeMillis(),
            sourceLabel = "Open-Meteo (CAMS model, ~11 km grid)",
            trend = parseTrend(root.optJSONObject("hourly"), observedAt),
            measured = false
        )
    }

    /** Rebuilds a `current`-shaped object from the newest usable hourly slot. */
    private fun latestHourlyAsCurrent(hourly: JSONObject?): JSONObject? {
        val times = hourly?.optJSONArray("time") ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val fields = listOf(
            "pm2_5", "pm10", "ozone", "nitrogen_dioxide", "sulphur_dioxide", "carbon_monoxide", "us_aqi"
        )

        for (i in times.length() - 1 downTo 0) {
            val t = times.optLong(i, -1L)
            if (t <= 0L || t > nowSeconds) continue
            val pm25 = hourly.optJSONArray("pm2_5")?.optionalDouble(i) ?: continue
            val snapshot = JSONObject().put("time", t).put("pm2_5", pm25)
            fields.forEach { field ->
                hourly.optJSONArray(field)?.optionalDouble(i)?.let { snapshot.put(field, it) }
            }
            return snapshot
        }
        return null
    }

    /** Takes the 24 hours of PM2.5 ending at the current observation. */
    fun parseTrend(hourly: JSONObject?, observedAtEpochSeconds: Long): List<HourPoint> {
        val times = hourly?.optJSONArray("time") ?: return emptyList()
        val values = hourly.optJSONArray("pm2_5") ?: return emptyList()

        val points = ArrayList<HourPoint>(times.length())
        for (i in 0 until minOf(times.length(), values.length())) {
            val t = times.optLong(i, -1L)
            if (t <= 0L || t > observedAtEpochSeconds) continue
            val v = values.optionalDouble(i) ?: continue
            points += HourPoint(t, v)
        }
        return points.takeLast(TREND_HOURS)
    }

    private const val TREND_HOURS = 24
}

/** Singapore's National Environment Agency, via data.gov.sg. Ground stations, no key. */
object NeaPsiParser {

    fun parse(body: String): PsiSnapshot {
        val root = JSONObject(body)
        val container = root.optJSONObject("data") ?: root
        val items = container.optJSONArray("items")
            ?: throw IOException("PSI feed returned no items")
        val latest = items.optJSONObject(items.length() - 1)
            ?: throw IOException("PSI feed returned no items")
        val readings = latest.optJSONObject("readings")
            ?: throw IOException("PSI feed returned no readings")

        val psi = readings.optJSONObject("psi_twenty_four_hourly")
            ?: throw IOException("PSI feed is missing the 24 hour index")
        val national = psi.optionalDouble("national")?.toInt()
            ?: throw IOException("PSI feed is missing the national index")

        val regions = REGION_ORDER.mapNotNull { key ->
            psi.optionalDouble(key)?.let {
                RegionReading(key.replaceFirstChar(Char::uppercase), it.toInt())
            }
        }

        val timestamp = latest.optString("timestamp")
            .ifEmpty { latest.optString("updatedTimestamp") }
            .ifEmpty { latest.optString("update_timestamp") }

        val observedAt = parseIso8601Seconds(timestamp) ?: (System.currentTimeMillis() / 1000)
        return PsiSnapshot(
            national = national,
            regions = regions,
            regionPoints = regionPoints(container, psi, observedAt),
            pm25 = readings.optJSONObject("pm25_twenty_four_hourly")?.optionalDouble("national"),
            pm10 = readings.optJSONObject("pm10_twenty_four_hourly")?.optionalDouble("national"),
            observedAtEpochSeconds = observedAt
        )
    }

    /** v2 nests the coordinates under regionMetadata, v1 under region_metadata. */
    private fun regionPoints(
        container: JSONObject,
        psi: JSONObject,
        observedAt: Long
    ): List<AreaReading> {
        val metadata = container.optJSONArray("regionMetadata")
            ?: container.optJSONArray("region_metadata")
            ?: return emptyList()

        return (0 until metadata.length()).mapNotNull { index ->
            val entry = metadata.optJSONObject(index) ?: return@mapNotNull null
            val name = entry.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val location = entry.optJSONObject("labelLocation")
                ?: entry.optJSONObject("label_location")
                ?: return@mapNotNull null
            val latitude = location.optionalDouble("latitude") ?: return@mapNotNull null
            val longitude = location.optionalDouble("longitude") ?: return@mapNotNull null
            val value = psi.optionalDouble(name)?.toInt() ?: return@mapNotNull null

            AreaReading(
                name = name.replaceFirstChar(Char::uppercase) + " Singapore",
                latitude = latitude,
                longitude = longitude,
                indexValue = value,
                indexName = "PSI",
                measured = true,
                observedAtEpochSeconds = observedAt
            )
        }
    }

    private val REGION_ORDER = listOf("north", "south", "east", "west", "central")
}

// ------------------------------------------------------------------- JSON helpers

/** The feeds use `null` for "no reading", which optDouble turns into 0.0. */
internal fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

internal fun JSONArray.optionalDouble(index: Int): Double? =
    if (isNull(index)) null else optDouble(index).takeIf { !it.isNaN() }

/** Parses "2026-09-15T21:00:00+08:00" style timestamps without needing API 26. */
internal fun parseIso8601Seconds(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    val candidates = listOf(raw, raw.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2"))
    for (pattern in patterns) {
        for (candidate in candidates) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(candidate)
            }.getOrNull()
            if (parsed != null) return parsed.time / 1000
        }
    }
    return null
}

/** Pulls the UTC offset out of "+08:00", "+0800" or a full ISO timestamp. */
internal fun parseIso8601OffsetSeconds(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    if (raw.endsWith("Z", ignoreCase = true)) return 0
    val match = Regex("([+-])(\\d{2}):?(\\d{2})$").find(raw.trim()) ?: return null
    val sign = if (match.groupValues[1] == "-") -1 else 1
    return sign * (match.groupValues[2].toInt() * 3600 + match.groupValues[3].toInt() * 60)
}


/**
 * aqicn.org station map endpoint: every station inside a bounding box. This is what
 * turns the map from one number into a picture of where the haze actually is.
 */
object WaqiBoundsParser {

    fun parse(body: String): List<AreaReading> {
        val root = JSONObject(body)
        if (root.optString("status") != "ok") {
            throw IOException(
                root.optString("data").ifBlank { "aqicn.org rejected the request" }
                    .let { if (it == "Invalid key") "aqicn.org token is not valid" else it }
            )
        }

        val stations = root.optJSONArray("data") ?: return emptyList()
        return (0 until stations.length()).mapNotNull { index ->
            val station = stations.optJSONObject(index) ?: return@mapNotNull null
            // `aqi` arrives as a string, and is "-" for a station that is not reporting.
            val value = station.optString("aqi").toIntOrNull() ?: return@mapNotNull null
            val latitude = station.optionalDouble("lat") ?: return@mapNotNull null
            val longitude = station.optionalDouble("lon") ?: return@mapNotNull null
            val name = station.optJSONObject("station")?.optString("name")?.takeIf { it.isNotBlank() }

            AreaReading(
                name = name ?: "Station ${station.optInt("uid")}",
                latitude = latitude,
                longitude = longitude,
                indexValue = value,
                indexName = "AQI",
                measured = true,
                observedAtEpochSeconds = parseIso8601Seconds(
                    station.optJSONObject("station")?.optString("time")
                )
            )
        }
    }
}

/**
 * Open-Meteo answers a comma-separated list of coordinates in one request, which gives
 * a keyless modelled grid to shade the map with when no station feed is configured.
 */
object OpenMeteoGridParser {

    fun parse(body: String): List<AreaReading> {
        val trimmed = body.trimStart()
        // A single coordinate comes back as an object, several as an array.
        val entries = if (trimmed.startsWith("[")) {
            JSONArray(body)
        } else {
            JSONArray().put(JSONObject(body))
        }

        return (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            if (entry.optBoolean("error")) return@mapNotNull null
            val current = entry.optJSONObject("current") ?: return@mapNotNull null
            val latitude = entry.optionalDouble("latitude") ?: return@mapNotNull null
            val longitude = entry.optionalDouble("longitude") ?: return@mapNotNull null
            val value = current.optionalDouble("us_aqi")?.toInt()
                ?: current.optionalDouble("pm2_5")?.let { UsAqi.fromPm25(it) }
                ?: return@mapNotNull null

            AreaReading(
                name = "Model grid",
                latitude = latitude,
                longitude = longitude,
                indexValue = value,
                indexName = "US AQI",
                measured = false,
                observedAtEpochSeconds = current.optLong("time").takeIf { it > 0 }
            )
        }
    }
}
