package com.beyondexplain.hazeindex

import org.junit.Assert.assertEquals
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
