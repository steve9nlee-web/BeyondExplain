package com.beyondexplain.hazeindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The data behind the map: station bounds, the modelled grid, and viewport maths. */
class MapDataTest {

    // ------------------------------------------------------- aqicn map bounds

    private val boundsBody = """
        {"status":"ok","data":[
          {"lat":1.3521,"lon":103.8198,"uid":5773,"aqi":"54",
           "station":{"name":"Singapore","time":"2026-09-15T21:00:00+08:00"}},
          {"lat":1.4927,"lon":103.7414,"uid":8190,"aqi":"112",
           "station":{"name":"Johor Bahru","time":"2026-09-15T21:00:00+08:00"}},
          {"lat":3.1390,"lon":101.6869,"uid":9001,"aqi":"-",
           "station":{"name":"Offline station","time":""}}
        ]}
    """.trimIndent()

    @Test
    fun `station bounds become plottable readings`() {
        val readings = WaqiBoundsParser.parse(boundsBody)

        // The station reporting "-" has no data and must not appear as a zero.
        assertEquals(2, readings.size)
        assertEquals(listOf("Singapore", "Johor Bahru"), readings.map { it.name })
        assertEquals(54, readings[0].indexValue)
        assertEquals(Band.MODERATE, readings[0].band)
        assertEquals(112, readings[1].indexValue)
        assertEquals(Band.SENSITIVE, readings[1].band)
        assertTrue(readings.all { it.measured })
        assertEquals(1789477200L, readings[0].observedAtEpochSeconds)
    }

    @Test
    fun `station bounds surface a bad token`() {
        try {
            WaqiBoundsParser.parse("""{"status":"error","data":"Invalid key"}""")
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("aqicn.org token is not valid", e.message)
        }
    }

    @Test
    fun `an empty bounding box is not an error`() {
        assertTrue(WaqiBoundsParser.parse("""{"status":"ok","data":[]}""").isEmpty())
    }

    // --------------------------------------------------------- modelled grid

    private val gridBody = """
        [{"latitude":1.2,"longitude":103.6,"current":{"time":1789477200,"us_aqi":45,"pm2_5":9.1}},
         {"latitude":1.2,"longitude":103.9,"current":{"time":1789477200,"us_aqi":88,"pm2_5":30.2}},
         {"latitude":1.5,"longitude":103.6,"current":{"time":1789477200,"us_aqi":null,"pm2_5":60.0}},
         {"latitude":1.5,"longitude":103.9,"current":{"time":1789477200}}]
    """.trimIndent()

    @Test
    fun `the multi-point grid parses and falls back to pm25 where the index is missing`() {
        val readings = OpenMeteoGridParser.parse(gridBody)

        // The fourth point has neither index nor pm2.5 and is dropped.
        assertEquals(3, readings.size)
        assertEquals(45, readings[0].indexValue)
        assertEquals(88, readings[1].indexValue)
        // 60 µg/m³ of PM2.5 derives an AQI in the "unhealthy" band.
        assertEquals(Band.UNHEALTHY, readings[2].band)
        assertTrue(readings.none { it.measured })
    }

    @Test
    fun `a single coordinate comes back as an object and still parses`() {
        val readings = OpenMeteoGridParser.parse(
            """{"latitude":1.2,"longitude":103.6,"current":{"time":1789477200,"us_aqi":45}}"""
        )
        assertEquals(1, readings.size)
        assertEquals(45, readings[0].indexValue)
    }

    // ------------------------------------------------------ NEA region points

    private val neaWithRegions = """
        {"code":0,"data":{
          "regionMetadata":[
            {"name":"west","labelLocation":{"latitude":1.35735,"longitude":103.7}},
            {"name":"east","labelLocation":{"latitude":1.35735,"longitude":103.94}},
            {"name":"central","labelLocation":{"latitude":1.35735,"longitude":103.82}},
            {"name":"north","labelLocation":{"latitude":1.41803,"longitude":103.82}},
            {"name":"south","labelLocation":{"latitude":1.29587,"longitude":103.82}}],
          "items":[{"timestamp":"2026-09-15T21:00:00+08:00","readings":{
            "psi_twenty_four_hourly":{"west":61,"national":64,"east":58,"central":64,
                                      "south":60,"north":57}}}]}}
    """.trimIndent()

    @Test
    fun `nea regions carry their map coordinates`() {
        val psi = NeaPsiParser.parse(neaWithRegions)

        assertEquals(5, psi.regionPoints.size)
        val north = psi.regionPoints.first { it.name.startsWith("North") }
        assertEquals(57, north.indexValue)
        assertEquals("PSI", north.indexName)
        assertTrue(north.measured)
        assertEquals(1.41803, north.latitude, 0.00001)
        assertEquals(103.82, north.longitude, 0.00001)
        assertEquals(1789477200L, north.observedAtEpochSeconds)
    }

    @Test
    fun `a feed without label locations simply has no map points`() {
        val psi = NeaPsiParser.parse(
            """{"data":{"items":[{"timestamp":"2026-09-15T21:00:00+08:00",
               "readings":{"psi_twenty_four_hourly":{"national":64,"west":61}}}]}}"""
        )
        assertEquals(64, psi.national)
        assertTrue(psi.regionPoints.isEmpty())
        assertNull(psi.pm25)
    }

    // ------------------------------------------------------------ viewport maths

    @Test
    fun `the sample lattice stays inside the viewport`() {
        val bounds = MapBounds(south = 1.0, west = 103.0, north = 2.0, east = 104.0)
        val points = bounds.samplePoints()

        assertEquals(25, points.size)
        assertTrue(points.all { it.first > 1.0 && it.first < 2.0 })
        assertTrue(points.all { it.second > 103.0 && it.second < 104.0 })
        // Inset by half a cell, so no sample sits on the edge.
        assertEquals(1.1, points.first().first, 0.0001)
        assertEquals(103.1, points.first().second, 0.0001)
        assertEquals(1.9, points.last().first, 0.0001)
        assertEquals(103.9, points.last().second, 0.0001)
    }

    @Test
    fun `cells are half a lattice step across`() {
        val bounds = MapBounds(south = 1.0, west = 103.0, north = 2.0, east = 104.0)
        // One fifth of a degree of latitude is ~22.2 km, so half a cell is ~11.1 km.
        assertEquals(11_120.0, bounds.cellRadiusMetres(), 200.0)
    }

    @Test
    fun `a degenerate viewport yields no samples rather than crashing`() {
        assertTrue(MapBounds(2.0, 104.0, 1.0, 103.0).samplePoints().isEmpty())
        assertTrue(MapBounds(1.0, 103.0, 1.0, 103.0).samplePoints().isEmpty())
    }

    @Test
    fun `singapore is detected only when it is actually in view`() {
        assertTrue(MapBounds(1.2, 103.6, 1.5, 104.0).intersectsSingapore())
        // A wide regional view that contains Singapore.
        assertTrue(MapBounds(-2.0, 100.0, 6.0, 108.0).intersectsSingapore())
        // Bangkok.
        assertFalse(MapBounds(13.5, 100.3, 14.0, 100.8).intersectsSingapore())
    }
}
