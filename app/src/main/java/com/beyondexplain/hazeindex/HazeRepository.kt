package com.beyondexplain.hazeindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pulls live readings off the public air quality feeds. Every call here goes to the
 * network; nothing is served from memory, so a pull-to-refresh really does refresh.
 *
 * Sources:
 *  - Open-Meteo Air Quality API (global, no API key) for the pollutant concentrations,
 *    the US AQI headline and the 24 hour PM2.5 trend.
 *  - data.gov.sg / NEA for Singapore's official PSI, which is the number local news
 *    and advisories quote during a haze episode.
 */
class HazeRepository {

    suspend fun load(city: City): HazeReport = withContext(Dispatchers.IO) {
        val base = fetchOpenMeteo(city)
        if (!city.useNeaPsi) return@withContext base

        // Singapore: prefer the official PSI as the headline, keeping Open-Meteo's
        // trend line. If NEA is unreachable we still have a usable report.
        val psi = runCatching { fetchNeaPsi() }.getOrNull() ?: return@withContext base
        base.copy(
            indexName = "PSI",
            indexValue = psi.national,
            band = IndexScale.forPsi(psi.national),
            observedAtEpochSeconds = psi.observedAtEpochSeconds,
            utcOffsetSeconds = SINGAPORE_UTC_OFFSET_SECONDS,
            sourceLabel = "NEA / data.gov.sg + Open-Meteo",
            regions = psi.regions,
            pollutants = base.pollutants.copy(
                pm25 = psi.pm25 ?: base.pollutants.pm25,
                pm10 = psi.pm10 ?: base.pollutants.pm10
            )
        )
    }

    // ---------------------------------------------------------------- Open-Meteo

    private fun fetchOpenMeteo(city: City): HazeReport {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&current=us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide" +
            "&hourly=pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,us_aqi" +
            "&past_days=1&forecast_days=1&timezone=auto&timeformat=unixtime"

        val root = JSONObject(httpGet(url))
        if (root.has("error") && root.optBoolean("error")) {
            throw IOException(root.optString("reason", "Air quality service rejected the request"))
        }

        // `current` is the normal path; falling back to the most recent hourly slot keeps
        // the app working if the feed ever stops serving a current block.
        val current = root.optJSONObject("current")
            ?: latestHourlyAsCurrent(root.optJSONObject("hourly"))
            ?: throw IOException("Air quality service returned no current reading")
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

        // The feed usually carries us_aqi directly; if that field is missing for this
        // grid cell, derive it from PM2.5 so the headline is never blank.
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
            sourceLabel = "Open-Meteo Air Quality",
            trend = parseTrend(root.optJSONObject("hourly"), observedAt)
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
    private fun parseTrend(hourly: JSONObject?, observedAtEpochSeconds: Long): List<HourPoint> {
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

    // ----------------------------------------------------------------- NEA PSI

    private data class PsiSnapshot(
        val national: Int,
        val regions: List<RegionReading>,
        val pm25: Double?,
        val pm10: Double?,
        val observedAtEpochSeconds: Long
    )

    private fun fetchNeaPsi(): PsiSnapshot {
        // v2 is the current endpoint; v1 is kept as a fallback for older deployments.
        val body = runCatching { httpGet(NEA_PSI_V2) }
            .getOrElse { httpGet(NEA_PSI_V1) }

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
            psi.optionalDouble(key)?.let { RegionReading(key.replaceFirstChar(Char::uppercase), it.toInt()) }
        }

        val timestamp = latest.optString("timestamp").ifEmpty { latest.optString("updatedTimestamp") }
            .ifEmpty { latest.optString("update_timestamp") }

        return PsiSnapshot(
            national = national,
            regions = regions,
            pm25 = readings.optJSONObject("pm25_twenty_four_hourly")?.optionalDouble("national"),
            pm10 = readings.optJSONObject("pm10_twenty_four_hourly")?.optionalDouble("national"),
            observedAtEpochSeconds = parseIso8601Seconds(timestamp)
                ?: (System.currentTimeMillis() / 1000)
        )
    }

    // -------------------------------------------------------------------- HTTP

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HazeIndex-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Server returned HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val NEA_PSI_V2 = "https://api-open.data.gov.sg/v2/real-time/api/psi"
        const val NEA_PSI_V1 = "https://api.data.gov.sg/v1/environment/psi"
        const val SINGAPORE_UTC_OFFSET_SECONDS = 8 * 3600
        const val TREND_HOURS = 24
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 12_000
        val REGION_ORDER = listOf("north", "south", "east", "west", "central")
    }
}

/** US EPA AQI from a PM2.5 concentration, using the 2024 breakpoints. */
object UsAqi {
    private val breakpoints = listOf(
        doubleArrayOf(0.0, 9.0, 0.0, 50.0),
        doubleArrayOf(9.1, 35.4, 51.0, 100.0),
        doubleArrayOf(35.5, 55.4, 101.0, 150.0),
        doubleArrayOf(55.5, 125.4, 151.0, 200.0),
        doubleArrayOf(125.5, 225.4, 201.0, 300.0),
        doubleArrayOf(225.5, 500.4, 301.0, 500.0)
    )

    fun fromPm25(concentration: Double): Int {
        val c = concentration.coerceAtLeast(0.0)
        val row = breakpoints.firstOrNull { c <= it[1] } ?: breakpoints.last()
        val (cLow, cHigh, iLow, iHigh) = row
        if (cHigh == cLow) return iLow.toInt()
        return (((iHigh - iLow) / (cHigh - cLow)) * (c - cLow) + iLow).toInt().coerceIn(0, 500)
    }
}

/** JSON helpers: the feeds use `null` for "no reading", which optDouble turns into 0.0. */
internal fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

internal fun JSONArray.optionalDouble(index: Int): Double? =
    if (isNull(index)) null else optDouble(index).takeIf { !it.isNaN() }

/** Parses "2026-09-15T21:00:00+08:00" style timestamps without needing API 26. */
internal fun parseIso8601Seconds(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss")
    val candidates = listOf(raw, raw.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2"))
    for (pattern in patterns) {
        for (candidate in candidates) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(candidate)
            }.getOrNull()
            if (parsed != null) return parsed.time / 1000
        }
    }
    return null
}
