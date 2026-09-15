package com.beyondexplain.penangstalls

import com.beyondexplain.penangstalls.data.DistanceBand
import com.beyondexplain.penangstalls.data.NearbyStall
import com.beyondexplain.penangstalls.data.Stall
import com.beyondexplain.penangstalls.data.StallCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StallCatalogTest {

    @Test
    fun `parses a well formed feed`() {
        val catalog = StallCatalog.parse(
            """
            {
              "source": "test",
              "updatedAt": "2026-09-15",
              "stalls": [
                {"id":"a","name":"Laksa stall","category":"Asam laksa","area":"Air Itam","lat":5.4,"lng":100.3}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, catalog.stalls.size)
        assertEquals("test", catalog.sourceLabel)
        assertEquals("Laksa stall", catalog.stalls.single().name)
    }

    @Test
    fun `skips rows that are missing or out of range instead of failing the feed`() {
        val catalog = StallCatalog.parse(
            """
            {
              "stalls": [
                {"id":"good","name":"Good","lat":5.4,"lng":100.3},
                {"name":"No id","lat":5.4,"lng":100.3},
                {"id":"no-name","lat":5.4,"lng":100.3},
                {"id":"no-coords","name":"No coords"},
                {"id":"bad-lat","name":"Bad lat","lat":99.0,"lng":100.3}
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("good"), catalog.stalls.map { it.id })
    }

    @Test
    fun `falls back to the stall name when no facebook query is given`() {
        val stall = Stall("a", "Ah Leng CKT", "CKT", "Keramat", 5.4, 100.3)
        assertEquals("Ah Leng CKT", stall.searchTerm)
        assertEquals("custom", stall.copy(facebookQuery = "custom").searchTerm)
    }

    @Test
    fun `bands split at 100m and 200m and keep going up`() {
        assertEquals(DistanceBand.WITHIN_100, DistanceBand.of(0.0))
        assertEquals(DistanceBand.WITHIN_100, DistanceBand.of(100.0))
        assertEquals(DistanceBand.FROM_100_TO_200, DistanceBand.of(100.1))
        assertEquals(DistanceBand.FROM_100_TO_200, DistanceBand.of(200.0))
        assertEquals(DistanceBand.FROM_200_TO_500, DistanceBand.of(200.1))
        assertEquals(DistanceBand.FROM_500_TO_1000, DistanceBand.of(999.0))
        assertEquals(DistanceBand.BEYOND_1KM, DistanceBand.of(5_000.0))
    }

    @Test
    fun `distance reads in metres below a kilometre and kilometres above`() {
        val stall = Stall("a", "Stall", "Food", "Area", 5.4, 100.3)
        assertEquals("85 m", NearbyStall(stall, 85.4).readableDistance)
        assertTrue(NearbyStall(stall, 1400.0).readableDistance.startsWith("1.4"))
    }

    @Test
    fun `bundled catalogue shape is valid`() {
        val json = javaClass.classLoader!!.getResourceAsStream("stalls.json")!!
            .bufferedReader().use { it.readText() }
        val catalog = StallCatalog.parse(json)
        assertTrue("expected a populated catalogue", catalog.stalls.size > 20)
        assertEquals(catalog.stalls.size, catalog.stalls.map { it.id }.toSet().size)
        // Everything bundled should sit in the Penang region.
        assertTrue(catalog.stalls.all { it.latitude in 5.0..5.7 && it.longitude in 100.1..100.6 })
    }
}
