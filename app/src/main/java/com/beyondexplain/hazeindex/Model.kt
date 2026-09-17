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

object IndexScale {
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

/**
 * What the pollutant tiles are showing. Ground stations published through WAQI report
 * per-pollutant AQI sub-indices rather than raw concentrations, so the UI has to say
 * which one it is instead of stamping µg/m³ on everything.
 */
enum class PollutantUnit { MICROGRAMS, AQI }

/** Current pollutant readings — concentrations, or sub-indices when [PollutantUnit.AQI]. */
data class Pollutants(
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    val nitrogenDioxide: Double? = null,
    val sulphurDioxide: Double? = null,
    val coMilligrams: Double? = null
)

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
    /** True when the data came from the on-device cache rather than a live fetch. */
    val fromCache: Boolean = false,
    /**
     * True when the headline came off real monitoring equipment rather than a
     * weather model. This is the difference between "measured" and "simulated".
     */
    val measured: Boolean = false,
    /** Name of the reporting station, when the source names one. */
    val stationName: String? = null,
    /** How far that station is from the location being asked about. */
    val stationDistanceMetres: Double? = null,
    /** The pollutant driving the index, e.g. "pm25". */
    val dominantPollutant: String? = null,
    val pollutantUnit: PollutantUnit = PollutantUnit.MICROGRAMS,
    /** Non-fatal thing the user should know, e.g. a configured source that fell back. */
    val notice: String? = null
)
