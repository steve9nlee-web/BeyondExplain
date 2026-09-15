package com.beyondexplain.hazeindex

/** A place the app can report on. */
data class City(
    val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    /** Singapore gets the official NEA PSI as its headline index. */
    val useNeaPsi: Boolean = false
) {
    val displayName: String get() = if (country.isEmpty()) name else "$name, $country"
}

/** Severity bands shared by PSI and US AQI (PSI simply never lands on [SENSITIVE]). */
enum class Band(val label: String, val advice: String) {
    GOOD(
        "Good",
        "Air is clean. Normal outdoor activity for everyone."
    ),
    MODERATE(
        "Moderate",
        "Acceptable. Unusually sensitive people may want to limit long outdoor exertion."
    ),
    SENSITIVE(
        "Unhealthy for sensitive groups",
        "Children, the elderly and people with heart or lung conditions should reduce prolonged outdoor exertion."
    ),
    UNHEALTHY(
        "Unhealthy",
        "Avoid prolonged outdoor exertion. Keep windows shut; use an N95 mask outdoors if the haze is thick."
    ),
    VERY_UNHEALTHY(
        "Very unhealthy",
        "Minimise outdoor activity. Everyone should stay indoors with air purification where possible."
    ),
    HAZARDOUS(
        "Hazardous",
        "Health emergency. Stay indoors, seal gaps, and seek medical help for breathing difficulty."
    );
}

/** One coloured step of the index ruler shown under the headline. */
data class ScaleSegment(val band: Band, val lower: Int, val upper: Int)

object IndexScale {

    /** NEA PSI never lands on "unhealthy for sensitive groups", so it has five steps. */
    val PSI_SEGMENTS = listOf(
        ScaleSegment(Band.GOOD, 0, 50),
        ScaleSegment(Band.MODERATE, 51, 100),
        ScaleSegment(Band.UNHEALTHY, 101, 200),
        ScaleSegment(Band.VERY_UNHEALTHY, 201, 300),
        ScaleSegment(Band.HAZARDOUS, 301, 500)
    )

    val US_AQI_SEGMENTS = listOf(
        ScaleSegment(Band.GOOD, 0, 50),
        ScaleSegment(Band.MODERATE, 51, 100),
        ScaleSegment(Band.SENSITIVE, 101, 150),
        ScaleSegment(Band.UNHEALTHY, 151, 200),
        ScaleSegment(Band.VERY_UNHEALTHY, 201, 300),
        ScaleSegment(Band.HAZARDOUS, 301, 500)
    )

    fun segmentsFor(indexName: String): List<ScaleSegment> =
        if (indexName == "PSI") PSI_SEGMENTS else US_AQI_SEGMENTS

    /** NEA Pollutant Standards Index bands. */
    fun forPsi(value: Int): Band = when {
        value <= 50 -> Band.GOOD
        value <= 100 -> Band.MODERATE
        value <= 200 -> Band.UNHEALTHY
        value <= 300 -> Band.VERY_UNHEALTHY
        else -> Band.HAZARDOUS
    }

    /** US EPA AQI bands. */
    fun forUsAqi(value: Int): Band = when {
        value <= 50 -> Band.GOOD
        value <= 100 -> Band.MODERATE
        value <= 150 -> Band.SENSITIVE
        value <= 200 -> Band.UNHEALTHY
        value <= 300 -> Band.VERY_UNHEALTHY
        else -> Band.HAZARDOUS
    }

    /** Band of the PM2.5 concentration itself, used to colour the 24 hour trend bars. */
    fun forPm25(microgramsPerCubicMetre: Double): Band = when {
        microgramsPerCubicMetre <= 9.0 -> Band.GOOD
        microgramsPerCubicMetre <= 35.4 -> Band.MODERATE
        microgramsPerCubicMetre <= 55.4 -> Band.SENSITIVE
        microgramsPerCubicMetre <= 125.4 -> Band.UNHEALTHY
        microgramsPerCubicMetre <= 225.4 -> Band.VERY_UNHEALTHY
        else -> Band.HAZARDOUS
    }
}

/** Current pollutant concentrations, all in µg/m³ except [coMilligrams]. */
data class Pollutants(
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    val nitrogenDioxide: Double? = null,
    val sulphurDioxide: Double? = null,
    val coMilligrams: Double? = null
)

/**
 * The weather strip IQAir shows alongside the index. Everything is optional: the
 * forecast call is a nice-to-have and a haze reading is still useful without it.
 */
data class Weather(
    val temperatureCelsius: Double? = null,
    val humidityPercent: Int? = null,
    val windKph: Double? = null,
    /** WMO weather interpretation code, as served by Open-Meteo. */
    val weatherCode: Int? = null
) {
    val isEmpty: Boolean
        get() = temperatureCelsius == null && humidityPercent == null && windKph == null

    /**
     * Plain English for the WMO interpretation code. Grouped rather than exhaustive:
     * "light drizzle" versus "dense drizzle" is more precision than a haze app needs.
     */
    val condition: String?
        get() = when (weatherCode) {
            null -> null
            0 -> "Clear"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> null
        }
}

/** One hour of the PM2.5 trend. */
data class HourPoint(val epochSeconds: Long, val pm25: Double)

/** A sub-area reading, e.g. the five NEA reporting regions of Singapore. */
data class RegionReading(val name: String, val value: Int)

data class HazeReport(
    val city: City,
    /** "PSI" or "US AQI". */
    val indexName: String,
    val indexValue: Int,
    val band: Band,
    val pollutants: Pollutants,
    /** Source observation time, epoch seconds. */
    val observedAtEpochSeconds: Long,
    /** Offset of the city's local time from UTC, used so timestamps read as local to the city. */
    val utcOffsetSeconds: Int,
    /** When this device actually pulled the data, epoch millis. */
    val fetchedAtEpochMillis: Long,
    val sourceLabel: String,
    val trend: List<HourPoint> = emptyList(),
    val regions: List<RegionReading> = emptyList(),
    /** The pollutant driving the index, IQAir style. Null when nothing was measured. */
    val mainPollutant: Pollutant? = null,
    val weather: Weather? = null,
    /** True when the data came from the on-device cache rather than a live fetch. */
    val fromCache: Boolean = false
)
