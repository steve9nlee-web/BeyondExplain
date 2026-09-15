package com.beyondexplain.hazeindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleAndParsingTest {

    @Test
    fun `psi bands follow NEA thresholds`() {
        assertEquals(Band.GOOD, IndexScale.forPsi(50))
        assertEquals(Band.MODERATE, IndexScale.forPsi(51))
        assertEquals(Band.UNHEALTHY, IndexScale.forPsi(101))
        assertEquals(Band.VERY_UNHEALTHY, IndexScale.forPsi(201))
        assertEquals(Band.HAZARDOUS, IndexScale.forPsi(301))
    }

    @Test
    fun `us aqi bands include the sensitive group range`() {
        assertEquals(Band.GOOD, IndexScale.forUsAqi(0))
        assertEquals(Band.MODERATE, IndexScale.forUsAqi(100))
        assertEquals(Band.SENSITIVE, IndexScale.forUsAqi(101))
        assertEquals(Band.UNHEALTHY, IndexScale.forUsAqi(151))
        assertEquals(Band.HAZARDOUS, IndexScale.forUsAqi(500))
    }

    @Test
    fun `aqi from pm25 matches EPA anchor points`() {
        assertEquals(0, UsAqi.fromPm25(0.0))
        assertEquals(50, UsAqi.fromPm25(9.0))
        assertEquals(100, UsAqi.fromPm25(35.4))
        assertEquals(150, UsAqi.fromPm25(55.4))
        assertEquals(200, UsAqi.fromPm25(125.4))
        assertEquals(300, UsAqi.fromPm25(225.4))
        assertEquals(500, UsAqi.fromPm25(900.0))
    }

    @Test
    fun `iso timestamps with an offset parse to the right instant`() {
        // 2026-09-15T21:00:00+08:00 == 2026-09-15T13:00:00Z
        assertEquals(1789477200L, parseIso8601Seconds("2026-09-15T21:00:00+08:00"))
        assertEquals(1789477200L, parseIso8601Seconds("2026-09-15T13:00:00+00:00"))
        assertNull(parseIso8601Seconds(null))
        assertNull(parseIso8601Seconds("not a timestamp"))
    }
}

class DeviceLocationLogicTest {

    @Test
    fun `distance between two points is right to within a few metres`() {
        // One degree of latitude is ~111.19 km anywhere on the globe.
        assertEquals(111_195.0, Geo.distanceMetres(1.0, 103.8, 2.0, 103.8), 50.0)
        // Singapore city centre to Johor Bahru, straight line.
        val metres = Geo.distanceMetres(1.3521, 103.8198, 1.4927, 103.7414)
        assertTrue("was $metres", metres in 17_500.0..18_500.0)
        assertEquals(0.0, Geo.distanceMetres(1.3521, 103.8198, 1.3521, 103.8198), 0.001)
    }

    @Test
    fun `a device inside Singapore still gets the official PSI feed`() {
        assertTrue(Cities.fromCoordinates("Bedok", 1.3236, 103.9273).useNeaPsi)
        assertFalse(Cities.fromCoordinates("Johor Bahru", 1.4927, 103.7414).useNeaPsi)
    }

    @Test
    fun `the device city keeps a stable id so its cached reading survives a move`() {
        val first = Cities.fromCoordinates("Bedok", 1.3236, 103.9273)
        val second = Cities.fromCoordinates("Jurong", 1.3329, 103.7436)
        assertEquals(first.id, second.id)
        assertTrue(Cities.isDevice(first))
        assertFalse(Cities.isResolved(Cities.LOCATING))
        assertTrue(Cities.isResolved(first))
    }
}
