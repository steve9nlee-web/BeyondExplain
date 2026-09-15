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

class SubIndexTest {

    @Test
    fun `pm10 sub index matches EPA anchor points`() {
        assertEquals(50, UsAqi.subIndex(Pollutant.PM10, 54.0))
        assertEquals(101, UsAqi.subIndex(Pollutant.PM10, 155.0))
        assertEquals(150, UsAqi.subIndex(Pollutant.PM10, 254.0))
        assertEquals(500, UsAqi.subIndex(Pollutant.PM10, 604.0))
    }

    @Test
    fun `gases are converted from micrograms before the table is read`() {
        // 100 µg/m³ of NO2 is 53 ppb at 25 °C, which is exactly the top of the good band.
        assertEquals(50, UsAqi.subIndex(Pollutant.NITROGEN_DIOXIDE, 100.0))
        // 200 µg/m³ of SO2 is 76 ppb, the first ppb in the "sensitive groups" band.
        assertEquals(101, UsAqi.subIndex(Pollutant.SULPHUR_DIOXIDE, 200.0))
        // 100 µg/m³ of ozone is 0.050 ppm, part way up the good band.
        assertEquals(46, UsAqi.subIndex(Pollutant.OZONE, 100.0))
        // CO arrives in mg/m³: 5 mg/m³ is 4.3 ppm.
        assertEquals(49, UsAqi.subIndex(Pollutant.CARBON_MONOXIDE, 5.0))
    }

    @Test
    fun `a reading sitting exactly on a band edge stays in the lower band`() {
        // Binary fractions do not land on 55.4 exactly, so these are the cases where a
        // sloppy truncation quietly reports the band above.
        assertEquals(50, UsAqi.subIndex(Pollutant.PM25, 9.0))
        assertEquals(100, UsAqi.subIndex(Pollutant.PM25, 35.4))
        assertEquals(150, UsAqi.subIndex(Pollutant.PM25, 55.4))
        assertEquals(200, UsAqi.subIndex(Pollutant.PM25, 125.4))
        assertEquals(300, UsAqi.subIndex(Pollutant.PM25, 225.4))
        assertEquals(50, UsAqi.subIndex(Pollutant.PM10, 54.0))
        assertEquals(100, UsAqi.subIndex(Pollutant.PM10, 154.0))
    }

    @Test
    fun `a missing or nonsense reading has no sub index`() {
        assertNull(UsAqi.subIndex(Pollutant.PM25, null))
        assertNull(UsAqi.subIndex(Pollutant.PM25, -1.0))
        assertNull(UsAqi.subIndex(Pollutant.PM25, Double.NaN))
    }

    @Test
    fun `the main pollutant is the one driving the index`() {
        val readings = Pollutants(pm25 = 10.0, pm10 = 200.0, ozone = 20.0)
        val (pollutant, index) = UsAqi.dominant(readings)!!
        assertEquals(Pollutant.PM10, pollutant)
        assertEquals(123, index)
    }

    @Test
    fun `a tie goes to PM2_5, which is the haze pollutant`() {
        // 9.0 µg/m³ of PM2.5 and 54 µg/m³ of PM10 are both exactly 50.
        val (pollutant, index) = UsAqi.dominant(Pollutants(pm25 = 9.0, pm10 = 54.0))!!
        assertEquals(Pollutant.PM25, pollutant)
        assertEquals(50, index)
    }

    @Test
    fun `nothing measured means no main pollutant`() {
        assertNull(UsAqi.dominant(Pollutants()))
    }
}

class ScaleRulerTest {

    @Test
    fun `PSI has no sensitive groups step, US AQI does`() {
        assertEquals(5, IndexScale.segmentsFor("PSI").size)
        assertFalse(IndexScale.segmentsFor("PSI").any { it.band == Band.SENSITIVE })
        assertEquals(6, IndexScale.segmentsFor("US AQI").size)
        assertTrue(IndexScale.segmentsFor("US AQI").any { it.band == Band.SENSITIVE })
    }

    @Test
    fun `every step starts where the one before it ended`() {
        listOf(IndexScale.PSI_SEGMENTS, IndexScale.US_AQI_SEGMENTS).forEach { segments ->
            assertEquals(0, segments.first().lower)
            assertEquals(500, segments.last().upper)
            segments.zipWithNext().forEach { (earlier, later) ->
                assertEquals(earlier.upper + 1, later.lower)
            }
        }
    }

    @Test
    fun `a step's own band agrees with the index scale it came from`() {
        IndexScale.US_AQI_SEGMENTS.forEach { segment ->
            assertEquals(segment.band, IndexScale.forUsAqi(segment.lower))
            assertEquals(segment.band, IndexScale.forUsAqi(segment.upper))
        }
        IndexScale.PSI_SEGMENTS.forEach { segment ->
            assertEquals(segment.band, IndexScale.forPsi(segment.lower))
            assertEquals(segment.band, IndexScale.forPsi(segment.upper))
        }
    }
}

class WeatherTest {

    @Test
    fun `WMO codes become plain English, and unknown ones become nothing`() {
        assertEquals("Overcast", Weather(weatherCode = 3).condition)
        assertEquals("Rain", Weather(weatherCode = 63).condition)
        assertEquals("Thunderstorm with hail", Weather(weatherCode = 99).condition)
        assertNull(Weather(weatherCode = 4).condition)
        assertNull(Weather().condition)
    }

    @Test
    fun `a weather block with no readings in it counts as empty`() {
        assertTrue(Weather().isEmpty)
        assertTrue(Weather(weatherCode = 3).isEmpty)
        assertFalse(Weather(temperatureCelsius = 29.0).isEmpty)
    }
}
