package com.beyondexplain.hazeindex

import kotlin.math.floor

/**
 * The pollutants the app reports on. IQAir headlines a "main pollutant" — the one whose
 * own sub-index is driving the overall number — so every pollutant needs a sub-index,
 * not just PM2.5.
 */
enum class Pollutant(val label: String) {
    PM25("PM2.5"),
    PM10("PM10"),
    OZONE("Ozone"),
    NITROGEN_DIOXIDE("NO₂"),
    SULPHUR_DIOXIDE("SO₂"),
    CARBON_MONOXIDE("CO");

    /** The unit the pollutant is *reported* in on screen. */
    val isMilligrams: Boolean get() = this == CARBON_MONOXIDE
}

/**
 * US EPA AQI maths.
 *
 * Every `subIndex` input is the concentration as this app carries it: µg/m³ for
 * everything except CO, which is mg/m³. The EPA tables are defined on ppm/ppb for the
 * gases, so those are converted first at 25 °C and 1 atm (molar volume 24.45 L/mol).
 *
 * One honest approximation: the EPA averages ozone and CO over 8 hours and NO₂/SO₂ over
 * 1 hour before applying the table. The feed gives a current hourly value, which is what
 * is used here — the same shortcut every live "current AQI" display makes.
 */
object UsAqi {

    /** `cLow..cHigh` of concentration maps linearly onto `iLow..iHigh` of index. */
    private data class Segment(val cLow: Double, val cHigh: Double, val iLow: Int, val iHigh: Int)

    /** PM2.5, µg/m³, 2024 EPA revision. */
    private val PM25 = listOf(
        Segment(0.0, 9.0, 0, 50),
        Segment(9.1, 35.4, 51, 100),
        Segment(35.5, 55.4, 101, 150),
        Segment(55.5, 125.4, 151, 200),
        Segment(125.5, 225.4, 201, 300),
        Segment(225.5, 500.4, 301, 500)
    )

    /** PM10, µg/m³. */
    private val PM10 = listOf(
        Segment(0.0, 54.0, 0, 50),
        Segment(55.0, 154.0, 51, 100),
        Segment(155.0, 254.0, 101, 150),
        Segment(255.0, 354.0, 151, 200),
        Segment(355.0, 424.0, 201, 300),
        Segment(425.0, 604.0, 301, 500)
    )

    /** Ozone, ppm. The table stops at 300; beyond that the app reports the cap. */
    private val OZONE = listOf(
        Segment(0.0, 0.054, 0, 50),
        Segment(0.055, 0.070, 51, 100),
        Segment(0.071, 0.085, 101, 150),
        Segment(0.086, 0.105, 151, 200),
        Segment(0.106, 0.200, 201, 300)
    )

    /** NO₂, ppb. */
    private val NITROGEN_DIOXIDE = listOf(
        Segment(0.0, 53.0, 0, 50),
        Segment(54.0, 100.0, 51, 100),
        Segment(101.0, 360.0, 101, 150),
        Segment(361.0, 649.0, 151, 200),
        Segment(650.0, 1249.0, 201, 300),
        Segment(1250.0, 2049.0, 301, 500)
    )

    /** SO₂, ppb. */
    private val SULPHUR_DIOXIDE = listOf(
        Segment(0.0, 35.0, 0, 50),
        Segment(36.0, 75.0, 51, 100),
        Segment(76.0, 185.0, 101, 150),
        Segment(186.0, 304.0, 151, 200),
        Segment(305.0, 604.0, 201, 300),
        Segment(605.0, 1004.0, 301, 500)
    )

    /** CO, ppm. */
    private val CARBON_MONOXIDE = listOf(
        Segment(0.0, 4.4, 0, 50),
        Segment(4.5, 9.4, 51, 100),
        Segment(9.5, 12.4, 101, 150),
        Segment(12.5, 15.4, 151, 200),
        Segment(15.5, 30.4, 201, 300),
        Segment(30.5, 50.4, 301, 500)
    )

    /**
     * µg/m³ -> ppb (or mg/m³ -> ppm for CO), and the decimal place the EPA truncates
     * the concentration to before reading the table off.
     */
    private data class Conversion(val factor: Double, val step: Double)

    private fun conversion(pollutant: Pollutant): Conversion = when (pollutant) {
        Pollutant.PM25 -> Conversion(1.0, 0.1)
        Pollutant.PM10 -> Conversion(1.0, 1.0)
        Pollutant.OZONE -> Conversion(24.45 / 48.00 / 1000.0, 0.001)
        Pollutant.NITROGEN_DIOXIDE -> Conversion(24.45 / 46.0055, 1.0)
        Pollutant.SULPHUR_DIOXIDE -> Conversion(24.45 / 64.066, 1.0)
        Pollutant.CARBON_MONOXIDE -> Conversion(24.45 / 28.010, 0.1)
    }

    private fun table(pollutant: Pollutant): List<Segment> = when (pollutant) {
        Pollutant.PM25 -> PM25
        Pollutant.PM10 -> PM10
        Pollutant.OZONE -> OZONE
        Pollutant.NITROGEN_DIOXIDE -> NITROGEN_DIOXIDE
        Pollutant.SULPHUR_DIOXIDE -> SULPHUR_DIOXIDE
        Pollutant.CARBON_MONOXIDE -> CARBON_MONOXIDE
    }

    /**
     * The AQI contribution of one pollutant, or null when there is no reading for it.
     * [value] is µg/m³, except [Pollutant.CARBON_MONOXIDE] which is mg/m³.
     */
    fun subIndex(pollutant: Pollutant, value: Double?): Int? {
        if (value == null || value.isNaN() || value < 0.0) return null
        val (factor, step) = conversion(pollutant)
        val concentration = truncate(value * factor, step)
        val segments = table(pollutant)
        val segment = segments.firstOrNull { concentration <= it.cHigh + EPSILON } ?: return 500
        if (segment.cHigh == segment.cLow) return segment.iLow
        val span = (segment.iHigh - segment.iLow) / (segment.cHigh - segment.cLow)
        val index = span * (concentration - segment.cLow) + segment.iLow
        return Math.round(index).toInt().coerceIn(0, 500)
    }

    /** Kept for the PM2.5-only path: the headline when the feed omits `us_aqi`. */
    fun fromPm25(concentration: Double): Int = subIndex(Pollutant.PM25, concentration) ?: 0

    /**
     * The pollutant driving the index right now, with its own sub-index. Null when
     * nothing was measured. Ties go to the earlier entry, so PM2.5 wins over PM10 at
     * equal severity — which is the right call for haze.
     */
    fun dominant(pollutants: Pollutants): Pair<Pollutant, Int>? =
        Pollutant.entries
            .mapNotNull { p -> subIndex(p, pollutants.valueOf(p))?.let { p to it } }
            .maxByOrNull { it.second }

    /**
     * EPA reads its tables off a concentration truncated to a fixed decimal place.
     *
     * The result is scrubbed of binary-fraction noise on the way out: 55.4 lands on
     * 55.400000000000006 otherwise, which is over the 55.4 band edge and would push a
     * reading sitting exactly on a boundary into the band above.
     */
    private fun truncate(value: Double, step: Double): Double {
        val steps = floor(value / step + EPSILON)
        return Math.round(steps * step * PRECISION) / PRECISION
    }

    private const val EPSILON = 1e-9
    private const val PRECISION = 1e6
}

/** Reads one pollutant out of the bundle, in the unit [UsAqi.subIndex] expects. */
fun Pollutants.valueOf(pollutant: Pollutant): Double? = when (pollutant) {
    Pollutant.PM25 -> pm25
    Pollutant.PM10 -> pm10
    Pollutant.OZONE -> ozone
    Pollutant.NITROGEN_DIOXIDE -> nitrogenDioxide
    Pollutant.SULPHUR_DIOXIDE -> sulphurDioxide
    Pollutant.CARBON_MONOXIDE -> coMilligrams
}
