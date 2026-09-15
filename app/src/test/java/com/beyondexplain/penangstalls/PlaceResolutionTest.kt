package com.beyondexplain.penangstalls

import com.beyondexplain.penangstalls.data.GeoPoint
import com.beyondexplain.penangstalls.data.MapsLinks
import com.beyondexplain.penangstalls.data.Stall
import com.beyondexplain.penangstalls.data.acceptCorrection
import com.beyondexplain.penangstalls.data.inPenang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceResolutionTest {

    private fun stall(name: String, area: String = "", lat: Double = 5.4377, lng: Double = 100.3093) =
        Stall(id = "x", name = name, category = "Food", area = area, latitude = lat, longitude = lng)

    @Test
    fun `search query names the place and pins it to Penang`() {
        assertEquals(
            "Gurney Drive Hawker Centre, Gurney Drive, Penang, Malaysia",
            MapsLinks.searchQuery(stall("Gurney Drive Hawker Centre", "Gurney Drive")),
        )
    }

    @Test
    fun `search query still works when a stall has no area`() {
        assertEquals("Toh Soon Cafe, Penang, Malaysia", MapsLinks.searchQuery(stall("Toh Soon Cafe")))
    }

    @Test
    fun `search query does not repeat a segment`() {
        val query = MapsLinks.searchQuery(stall("Chowrasta Market", "Chowrasta Market"))
        assertEquals("Chowrasta Market, Penang, Malaysia", query)
    }

    @Test
    fun `penang bounds accept island and mainland but reject elsewhere`() {
        assertTrue("George Town", GeoPoint(5.4164, 100.3327).inPenang())
        assertTrue("Butterworth", GeoPoint(5.3990, 100.3640).inPenang())
        assertFalse("Kuala Lumpur", GeoPoint(3.1390, 101.6869).inPenang())
        assertFalse("Singapore", GeoPoint(1.3521, 103.8198).inPenang())
    }

    @Test
    fun `a nearby correction is accepted`() {
        val catalogueGuess = GeoPoint(5.4377, 100.3093)
        // Google's position for the same place, about 250 m away.
        val resolved = GeoPoint(5.4399, 100.3105)
        assertTrue(acceptCorrection(resolved, catalogueGuess))
    }

    @Test
    fun `a same-named place far away is rejected rather than used`() {
        val catalogueGuess = GeoPoint(5.4377, 100.3093)
        val wrongMatch = GeoPoint(5.3630, 100.4660) // Bukit Mertajam, ~17 km off
        assertFalse(acceptCorrection(wrongMatch, catalogueGuess))
    }

    @Test
    fun `a match outside Penang is rejected even if the bounds were bypassed`() {
        assertFalse(acceptCorrection(GeoPoint(3.1390, 101.6869), GeoPoint(5.4377, 100.3093)))
    }
}
