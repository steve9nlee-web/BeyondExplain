package com.beyondexplain.hazeindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Every feed parser is exercised against a captured response shape. The build machine
 * cannot reach these APIs, so this is where the parsing is actually verified.
 */
class ParserTest {

    private val singapore = Cities.SINGAPORE

    // ---------------------------------------------------------------- aqicn / WAQI

    private val waqiOk = """
        {"status":"ok","data":{
          "aqi":54,"idx":5773,
          "attributions":[{"url":"http://www.nea.gov.sg/","name":"National Environment Agency"}],
          "city":{"geo":[1.35735,103.82],"name":"Singapore","url":"https://aqicn.org/city/singapore"},
          "dominentpol":"pm25",
          "iaqi":{"co":{"v":2.3},"h":{"v":79.9},"no2":{"v":5.9},"o3":{"v":10.9},
                  "p":{"v":1008.3},"pm10":{"v":17},"pm25":{"v":54},"so2":{"v":1.4},"t":{"v":28.4}},
          "time":{"s":"2026-09-15 21:00:00","tz":"+08:00","v":1789477200,
                  "iso":"2026-09-15T21:00:00+08:00"},
          "forecast":{"daily":{"pm25":[{"avg":50,"day":"2026-09-15","max":60,"min":40}]}}
        }}
    """.trimIndent()

    @Test
    fun `waqi feed yields a measured station reading`() {
        val report = WaqiParser.parse(waqiOk, singapore)

        assertEquals(54, report.indexValue)
        assertEquals(Band.MODERATE, report.band)
        assertEquals("AQI", report.indexName)
        assertTrue(report.measured)
        assertEquals("Singapore", report.stationName)
        assertEquals("pm25", report.dominantPollutant)
        assertEquals(1789477200L, report.observedAtEpochSeconds)
        assertEquals(8 * 3600, report.utcOffsetSeconds)
        // iaqi carries AQI sub-indices, not concentrations — the UI must label them as such.
        assertEquals(PollutantUnit.AQI, report.pollutantUnit)
        assertEquals(54.0, report.pollutants.pm25!!, 0.001)
        assertEquals(17.0, report.pollutants.pm10!!, 0.001)
        // Station is a few hundred metres from the reference point.
        assertTrue("was ${report.stationDistanceMetres}", report.stationDistanceMetres!! < 1_500)
    }

    @Test
    fun `waqi rejects a bad token with a message worth showing`() {
        try {
            WaqiParser.parse("""{"status":"error","data":"Invalid key"}""", singapore)
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("aqicn.org token is not valid", e.message)
        }
    }

    @Test
    fun `waqi over-quota error is surfaced verbatim`() {
        try {
            WaqiParser.parse("""{"status":"error","data":"Over quota"}""", singapore)
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("Over quota", e.message)
        }
    }

    // ------------------------------------------------------------------- IQAir

    private val iqAirOk = """
        {"status":"success","data":{
          "city":"Bedok","state":"Singapore","country":"Singapore",
          "location":{"type":"Point","coordinates":[103.9273,1.3236]},
          "current":{
            "pollution":{"ts":"2026-09-15T13:00:00.000Z","aqius":78,"mainus":"p2",
                         "aqicn":40,"maincn":"p2"},
            "weather":{"ts":"2026-09-15T13:00:00.000Z","tp":28,"pr":1008,"hu":80,"ws":2.1}
          }}}
    """.trimIndent()

    @Test
    fun `iqair feed yields the nearest city index`() {
        val report = IqAirParser.parse(iqAirOk, singapore)

        assertEquals(78, report.indexValue)
        assertEquals(Band.MODERATE, report.band)
        assertTrue(report.measured)
        assertEquals("Bedok, Singapore", report.stationName)
        assertEquals("p2", report.dominantPollutant)
        assertEquals(1789477200L, report.observedAtEpochSeconds)
        // GeoJSON is longitude-first; reading it the other way round would be a wild distance.
        assertTrue("was ${report.stationDistanceMetres}", report.stationDistanceMetres!! in 8_000.0..16_000.0)
        // The free tier carries no concentrations.
        assertEquals(Pollutants(), report.pollutants)
    }

    @Test
    fun `iqair failure carries the API message`() {
        try {
            IqAirParser.parse(
                """{"status":"fail","data":{"message":"incorrect_api_key"}}""",
                singapore
            )
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("incorrect_api_key", e.message)
        }
    }

    // -------------------------------------------------------------- Open-Meteo

    private fun openMeteoBody(includeCurrent: Boolean): String {
        val base = 1789477200L
        val times = (0 until 26).map { base - (25 - it) * 3600 }
        val pm25 = (0 until 26).map { 10.0 + it }
        val current = if (includeCurrent) {
            """"current":{"time":$base,"us_aqi":72,"pm2_5":22.5,"pm10":38.1,"ozone":41.0,
                 "nitrogen_dioxide":8.2,"sulphur_dioxide":1.9,"carbon_monoxide":240.0},"""
        } else {
            ""
        }
        return """
            {"latitude":1.35,"longitude":103.81,"utc_offset_seconds":28800,"timezone":"Asia/Singapore",
             $current
             "hourly":{"time":${times.joinToString(",", "[", "]")},
                       "pm2_5":${pm25.joinToString(",", "[", "]")},
                       "us_aqi":${pm25.map { it * 2 }.joinToString(",", "[", "]")}}}
        """.trimIndent()
    }

    @Test
    fun `open-meteo current block is parsed and marked as modelled`() {
        val report = OpenMeteoParser.parse(openMeteoBody(includeCurrent = true), singapore)

        assertEquals(72, report.indexValue)
        assertEquals(28800, report.utcOffsetSeconds)
        assertEquals(22.5, report.pollutants.pm25!!, 0.001)
        // Carbon monoxide is served in µg/m³ and shown in mg/m³.
        assertEquals(0.24, report.pollutants.coMilligrams!!, 0.001)
        assertEquals(PollutantUnit.MICROGRAMS, report.pollutantUnit)
        assertEquals(false, report.measured)
        assertNull(report.stationName)
        // 24 hours ending at the observation, nothing from the future.
        assertEquals(24, report.trend.size)
        assertEquals(1789477200L, report.trend.last().epochSeconds)
        assertTrue(report.trend.all { it.epochSeconds <= report.observedAtEpochSeconds })
    }

    @Test
    fun `open-meteo falls back to the newest hourly slot when current is missing`() {
        val report = OpenMeteoParser.parse(openMeteoBody(includeCurrent = false), singapore)

        assertNotNull(report.pollutants.pm25)
        assertTrue(report.indexValue > 0)
        assertTrue(report.trend.isNotEmpty())
    }

    @Test
    fun `open-meteo error body becomes an exception`() {
        try {
            OpenMeteoParser.parse("""{"error":true,"reason":"Value out of range"}""", singapore)
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("Value out of range", e.message)
        }
    }

    // ---------------------------------------------------------------- NEA PSI

    private val neaV2 = """
        {"code":0,"errorMsg":"","data":{
          "regionMetadata":[{"name":"west","labelLocation":{"latitude":1.35,"longitude":103.7}}],
          "items":[{"date":"2026-09-15","updatedTimestamp":"2026-09-15T21:07:00+08:00",
            "timestamp":"2026-09-15T21:00:00+08:00",
            "readings":{
              "psi_twenty_four_hourly":{"west":61,"national":64,"east":58,"central":64,"south":60,"north":57},
              "pm25_twenty_four_hourly":{"west":19,"national":21,"east":18,"central":21,"south":20,"north":17},
              "pm10_twenty_four_hourly":{"west":32,"national":35,"east":30,"central":35,"south":33,"north":29}
            }}]}}
    """.trimIndent()

    private val neaV1 = """
        {"region_metadata":[{"name":"west"}],
         "items":[{"timestamp":"2026-09-15T21:00:00+08:00","update_timestamp":"2026-09-15T21:07:00+08:00",
           "readings":{"psi_twenty_four_hourly":{"west":61,"national":64,"east":58,"central":64,
                                                 "south":60,"north":57}}}],
         "api_info":{"status":"healthy"}}
    """.trimIndent()

    @Test
    fun `nea v2 shape is parsed with all five regions`() {
        val psi = NeaPsiParser.parse(neaV2)

        assertEquals(64, psi.national)
        assertEquals(5, psi.regions.size)
        assertEquals(listOf("North", "South", "East", "West", "Central"), psi.regions.map { it.name })
        assertEquals(57, psi.regions.first { it.name == "North" }.value)
        assertEquals(21.0, psi.pm25!!, 0.001)
        assertEquals(1789477200L, psi.observedAtEpochSeconds)
    }

    @Test
    fun `nea v1 fallback shape still parses`() {
        val psi = NeaPsiParser.parse(neaV1)

        assertEquals(64, psi.national)
        assertEquals(5, psi.regions.size)
        assertNull(psi.pm25)
    }

    // ------------------------------------------------------------------ merging

    @Test
    fun `station headline keeps the model trend and gains nothing else`() {
        val station = WaqiParser.parse(waqiOk, singapore)
        val model = OpenMeteoParser.parse(openMeteoBody(includeCurrent = true), singapore)

        val merged = combineReports(station, model, notice = null)

        assertEquals(54, merged.indexValue)
        assertTrue(merged.measured)
        assertEquals(24, merged.trend.size)
        // The station's own sub-indices survive; the model's concentrations do not overwrite them.
        assertEquals(PollutantUnit.AQI, merged.pollutantUnit)
        assertEquals(54.0, merged.pollutants.pm25!!, 0.001)
    }

    @Test
    fun `iqair borrows concentrations and a time zone from the model`() {
        val station = IqAirParser.parse(iqAirOk, singapore)
        val model = OpenMeteoParser.parse(openMeteoBody(includeCurrent = true), singapore)

        val merged = combineReports(station, model, notice = null)

        assertEquals(78, merged.indexValue)
        assertEquals(28800, merged.utcOffsetSeconds)
        assertEquals(PollutantUnit.MICROGRAMS, merged.pollutantUnit)
        assertEquals(22.5, merged.pollutants.pm25!!, 0.001)
    }

    @Test
    fun `with no station the model stands alone and carries the notice`() {
        val model = OpenMeteoParser.parse(openMeteoBody(includeCurrent = true), singapore)

        val merged = combineReports(null, model, notice = "token rejected")

        assertEquals(false, merged.measured)
        assertEquals("token rejected", merged.notice)
    }

    @Test
    fun `singapore PSI takes the headline over any other source`() {
        val station = WaqiParser.parse(waqiOk, singapore)
        val psi = NeaPsiParser.parse(neaV2)

        val merged = applyNeaPsi(station, psi)

        assertEquals("PSI", merged.indexName)
        assertEquals(64, merged.indexValue)
        assertEquals(Band.MODERATE, merged.band)
        assertEquals(5, merged.regions.size)
        assertEquals(8 * 3600, merged.utcOffsetSeconds)
        assertTrue(merged.measured)
        // Sub-index tiles are left alone; PSI concentrations would be a different unit.
        assertEquals(54.0, merged.pollutants.pm25!!, 0.001)
    }

    @Test
    fun `singapore PSI fills concentrations when the report is in micrograms`() {
        val model = OpenMeteoParser.parse(openMeteoBody(includeCurrent = true), singapore)
        val psi = NeaPsiParser.parse(neaV2)

        val merged = applyNeaPsi(model, psi)

        assertEquals(21.0, merged.pollutants.pm25!!, 0.001)
        assertEquals(35.0, merged.pollutants.pm10!!, 0.001)
    }
}
