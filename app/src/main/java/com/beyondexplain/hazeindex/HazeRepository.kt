package com.beyondexplain.hazeindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Pulls live readings off the public air quality feeds. Every call here goes to the
 * network; nothing is served from memory, so a pull-to-refresh really does refresh.
 *
 * Accuracy order, best first:
 *  1. NEA / data.gov.sg PSI in Singapore — the official ground network, no key.
 *  2. aqicn.org (WAQI) or IQAir — the nearest real monitoring station, free key.
 *  3. Open-Meteo — CAMS model output on a ~11 km grid. Keyless and global, but a
 *     simulation, so it can be well off what a station down the road is measuring.
 */
class HazeRepository(private val settings: Settings) {

    suspend fun load(city: City): HazeReport = withContext(Dispatchers.IO) {
        var notice: String? = null

        val measured = when (settings.source) {
            // An explicitly chosen source is not silently swapped out: if it fails the
            // user gets told why rather than quietly being shown something else.
            SourceChoice.WAQI -> fetchWaqi(city)
            SourceChoice.IQAIR -> fetchIqAir(city)
            SourceChoice.OPEN_METEO -> null
            SourceChoice.AUTO -> {
                val attempts = listOfNotNull(
                    settings.waqiToken?.let { { fetchWaqi(city) } },
                    settings.iqAirKey?.let { { fetchIqAir(city) } }
                )
                var result: HazeReport? = null
                for (attempt in attempts) {
                    val outcome = runCatching { attempt() }
                    result = outcome.getOrNull()
                    if (result != null) break
                    notice = outcome.exceptionOrNull()?.message
                }
                result
            }
        }

        // Always worth having: the hourly trend, and concentrations for sources that
        // only publish an index. Never fatal when a station reading already succeeded.
        val modelled = if (measured == null) fetchOpenMeteo(city) else {
            runCatching { fetchOpenMeteo(city) }.getOrNull()
        }

        val combined = combineReports(measured, modelled, notice)
        if (!city.useNeaPsi) return@withContext combined

        val psi = runCatching { fetchNeaPsi() }.getOrNull() ?: return@withContext combined
        applyNeaPsi(combined, psi)
    }

    // ------------------------------------------------------------------- fetching

    private fun fetchWaqi(city: City): HazeReport {
        val token = settings.waqiToken
            ?: throw IOException("Add a free aqicn.org token in Settings to use station data")
        val url = "https://api.waqi.info/feed/geo:${city.latitude};${city.longitude}/" +
            "?token=${URLEncoder.encode(token, "UTF-8")}"
        return WaqiParser.parse(httpGet(url), city)
    }

    private fun fetchIqAir(city: City): HazeReport {
        val key = settings.iqAirKey
            ?: throw IOException("Add a free IQAir API key in Settings to use station data")
        val url = "https://api.airvisual.com/v2/nearest_city" +
            "?lat=${city.latitude}&lon=${city.longitude}" +
            "&key=${URLEncoder.encode(key, "UTF-8")}"
        return IqAirParser.parse(httpGet(url), city)
    }

    private fun fetchOpenMeteo(city: City): HazeReport {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&current=us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide" +
            "&hourly=pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,us_aqi" +
            "&past_days=1&forecast_days=1&timezone=auto&timeformat=unixtime"
        return OpenMeteoParser.parse(httpGet(url), city)
    }

    private fun fetchNeaPsi(): PsiSnapshot {
        // v2 is the current endpoint; v1 is kept as a fallback for older deployments.
        val body = runCatching { httpGet(NEA_PSI_V2) }.getOrElse { httpGet(NEA_PSI_V1) }
        return NeaPsiParser.parse(body)
    }

    // ----------------------------------------------------------------------- HTTP

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
            // These APIs describe key and quota problems in the body of a 4xx.
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299 && body.isBlank()) throw IOException("Server returned HTTP $code")
            if (code == 401 || code == 403) throw IOException("API key was rejected (HTTP $code)")
            if (code == 429) throw IOException("Rate limit reached for this API key")
            if (code !in 200..299 && !body.trimStart().startsWith("{")) {
                throw IOException("Server returned HTTP $code")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val NEA_PSI_V2 = "https://api-open.data.gov.sg/v2/real-time/api/psi"
        const val NEA_PSI_V1 = "https://api.data.gov.sg/v1/environment/psi"
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 12_000
    }
}

/** Station reading wins the headline; model data fills the gaps it leaves. */
internal fun combineReports(measured: HazeReport?, modelled: HazeReport?, notice: String?): HazeReport {
    if (measured == null) {
        return (modelled ?: throw IOException("No air quality data available"))
            .copy(notice = notice)
    }
    if (modelled == null) return measured.copy(notice = notice)

    val hasOwnPollutants = measured.pollutants != Pollutants()
    return measured.copy(
        trend = modelled.trend,
        // IQAir does not report a time zone; the model knows the city's own offset.
        utcOffsetSeconds = if (measured.utcOffsetSeconds == 0) {
            modelled.utcOffsetSeconds
        } else {
            measured.utcOffsetSeconds
        },
        pollutants = if (hasOwnPollutants) measured.pollutants else modelled.pollutants,
        pollutantUnit = if (hasOwnPollutants) measured.pollutantUnit else PollutantUnit.MICROGRAMS,
        sourceLabel = if (hasOwnPollutants) {
            "${measured.sourceLabel} + Open-Meteo (trend)"
        } else {
            "${measured.sourceLabel} + Open-Meteo (concentrations, trend)"
        },
        notice = notice
    )
}

/**
 * Singapore: the NEA network publishes the PSI that local advisories quote, so it
 * takes the headline wherever the rest of the report came from.
 */
internal fun applyNeaPsi(report: HazeReport, psi: PsiSnapshot): HazeReport =
    report.copy(
        indexName = "PSI",
        indexValue = psi.national,
        band = IndexScale.forPsi(psi.national),
        observedAtEpochSeconds = psi.observedAtEpochSeconds,
        utcOffsetSeconds = SINGAPORE_UTC_OFFSET_SECONDS,
        sourceLabel = "NEA / data.gov.sg" +
            if (report.measured) " + ${report.sourceLabel}" else "",
        regions = psi.regions,
        measured = true,
        stationName = report.stationName ?: "NEA national network",
        pollutants = if (report.pollutantUnit == PollutantUnit.MICROGRAMS) {
            report.pollutants.copy(
                pm25 = psi.pm25 ?: report.pollutants.pm25,
                pm10 = psi.pm10 ?: report.pollutants.pm10
            )
        } else {
            report.pollutants
        }
    )

private const val SINGAPORE_UTC_OFFSET_SECONDS = 8 * 3600

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
