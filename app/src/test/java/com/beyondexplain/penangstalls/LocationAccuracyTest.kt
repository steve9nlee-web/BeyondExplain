package com.beyondexplain.penangstalls

import com.beyondexplain.penangstalls.data.Fix
import com.beyondexplain.penangstalls.data.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAccuracyTest {

    private fun fix(accuracy: Double, at: Long = 0L) =
        Fix(latitude = 5.4, longitude = 100.3, accuracyMeters = accuracy, elapsedMillis = at)

    @Test
    fun `a tighter fix replaces a looser one`() {
        assertTrue(fix(10.0).isBetterThan(fix(80.0)))
        assertFalse(fix(80.0).isBetterThan(fix(10.0)))
    }

    @Test
    fun `the first fix is always accepted`() {
        assertTrue(fix(2000.0).isBetterThan(null))
    }

    @Test
    fun `a coarse cell fix never displaces a settled gps lock arriving together`() {
        val gps = fix(8.0, at = 1_000L)
        val cell = fix(1_500.0, at = 1_200L)
        assertFalse("a 1.5 km estimate must not overwrite an 8 m lock", cell.isBetterThan(gps))
    }

    @Test
    fun `a stale fix gives way to a slightly looser but current one`() {
        val old = fix(10.0, at = 0L)
        val fresh = fix(18.0, at = 60_000L)
        assertTrue("the person has probably moved", fresh.isBetterThan(old))

        val muchWorse = fix(500.0, at = 60_000L)
        assertFalse("but not to something wildly worse", muchWorse.isBetterThan(old))
    }

    @Test
    fun `fixes wider than the near rings are flagged as coarse`() {
        assertFalse(fix(12.0).isCoarse)
        assertFalse(fix(50.0).isCoarse)
        assertTrue(fix(51.0).isCoarse)
        assertTrue("an approximate-permission fix is always coarse", fix(2_000.0).isCoarse)
    }

    @Test
    fun `distance between identical points is zero`() {
        assertEquals(0.0, Geo.distanceMeters(5.4, 100.3, 5.4, 100.3), 0.001)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, Geo.distanceMeters(5.0, 100.0, 6.0, 100.0), 5.0)
    }

    @Test
    fun `known Penang separation matches an independent calculation`() {
        // Gurney Drive to Pulau Tikus market, computed outside the app.
        assertEquals(562.98, Geo.distanceMeters(5.4377, 100.3093, 5.4327, 100.3101), 0.5)
    }

    @Test
    fun `distance is symmetric`() {
        val there = Geo.distanceMeters(5.4377, 100.3093, 5.4157, 100.3335)
        val back = Geo.distanceMeters(5.4157, 100.3335, 5.4377, 100.3093)
        assertEquals(there, back, 0.0001)
    }
}
