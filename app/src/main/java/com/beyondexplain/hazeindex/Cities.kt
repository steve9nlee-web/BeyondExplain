package com.beyondexplain.hazeindex

/**
 * Places most affected by the annual Southeast Asian haze, plus the source regions
 * the smoke usually drifts from.
 */
object Cities {

    val SINGAPORE = City("sg", "Singapore", "", 1.3521, 103.8198, useNeaPsi = true)

    val ALL: List<City> = listOf(
        SINGAPORE,
        City("kul", "Kuala Lumpur", "Malaysia", 3.1390, 101.6869),
        City("jhb", "Johor Bahru", "Malaysia", 1.4927, 103.7414),
        City("png", "George Town", "Malaysia", 5.4141, 100.3288),
        City("kch", "Kuching", "Malaysia", 1.5533, 110.3592),
        City("bki", "Kota Kinabalu", "Malaysia", 5.9804, 116.0735),
        City("jkt", "Jakarta", "Indonesia", -6.2088, 106.8456),
        City("pku", "Pekanbaru", "Indonesia", 0.5071, 101.4478),
        City("plm", "Palembang", "Indonesia", -2.9761, 104.7754),
        City("ptk", "Pontianak", "Indonesia", -0.0263, 109.3425),
        City("bkk", "Bangkok", "Thailand", 13.7563, 100.5018),
        City("cnx", "Chiang Mai", "Thailand", 18.7883, 98.9853),
        City("mnl", "Manila", "Philippines", 14.5995, 120.9842),
        City("bwn", "Bandar Seri Begawan", "Brunei", 4.9031, 114.9398),
        City("han", "Hanoi", "Vietnam", 21.0278, 105.8342),
        City("sgn", "Ho Chi Minh City", "Vietnam", 10.8231, 106.6297),
        City("pnh", "Phnom Penh", "Cambodia", 11.5564, 104.9282)
    )

    fun byId(id: String?): City? = ALL.firstOrNull { it.id == id }

    /**
     * The device's own position. The id is stable so the cached reading survives the
     * small coordinate changes that come with every new fix.
     */
    const val DEVICE_ID = "device"

    /** Shown while the first fix is still being acquired. */
    val LOCATING = City(DEVICE_ID, "Locating\u2026", "", 0.0, 0.0)

    fun isDevice(city: City): Boolean = city.id == DEVICE_ID

    /** True once a device city has real coordinates to query. */
    fun isResolved(city: City): Boolean =
        !isDevice(city) || city.latitude != 0.0 || city.longitude != 0.0

    /**
     * A city built from device coordinates. Inside Singapore this still uses the
     * official NEA PSI, so standing in Singapore gives the same number the local
     * advisories quote.
     */
    fun fromCoordinates(name: String, latitude: Double, longitude: Double): City =
        City(
            id = DEVICE_ID,
            name = name,
            country = "",
            latitude = latitude,
            longitude = longitude,
            useNeaPsi = isInSingapore(latitude, longitude)
        )

    private fun isInSingapore(latitude: Double, longitude: Double): Boolean =
        latitude in 1.13..1.49 && longitude in 103.58..104.13
}

/** Great-circle distance, kept as plain maths so it can be unit tested off-device. */
object Geo {
    private const val EARTH_RADIUS_METRES = 6_371_000.0

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METRES * kotlin.math.asin(kotlin.math.sqrt(a).coerceIn(0.0, 1.0))
    }
}

/** The visible map rectangle, and the sampling maths that shades it. */
data class MapBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    /** A lattice of sample points, at most [GRID] per side, inset half a cell from the edges. */
    fun samplePoints(): List<Pair<Double, Double>> {
        if (north <= south || east <= west) return emptyList()
        val latStep = (north - south) / GRID
        val lonStep = (east - west) / GRID
        return (0 until GRID).flatMap { row ->
            (0 until GRID).map { column ->
                (south + latStep * (row + 0.5)) to (west + lonStep * (column + 0.5))
            }
        }
    }

    /** Half a cell, so the drawn circles tile the viewport without swamping it. */
    fun cellRadiusMetres(): Double {
        val latStep = (north - south) / GRID
        return Geo.distanceMetres(south, west, south + latStep, west) / 2
    }

    fun intersectsSingapore(): Boolean =
        south <= SINGAPORE_NORTH && north >= SINGAPORE_SOUTH &&
            west <= SINGAPORE_EAST && east >= SINGAPORE_WEST

    private companion object {
        const val GRID = 5
        const val SINGAPORE_SOUTH = 1.13
        const val SINGAPORE_NORTH = 1.49
        const val SINGAPORE_WEST = 103.58
        const val SINGAPORE_EAST = 104.13
    }
}
