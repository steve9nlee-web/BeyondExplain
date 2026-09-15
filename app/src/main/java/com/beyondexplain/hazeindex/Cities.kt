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

    /** A city built from device coordinates; never uses the Singapore-only PSI feed. */
    fun fromCoordinates(name: String, latitude: Double, longitude: Double): City =
        City(
            id = "loc:${"%.3f".format(latitude)},${"%.3f".format(longitude)}",
            name = name,
            country = "",
            latitude = latitude,
            longitude = longitude
        )
}
