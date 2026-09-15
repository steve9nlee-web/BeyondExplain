package com.beyondexplain.penangstalls.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supplies the stall list.
 *
 * Two sources, in priority order:
 *  1. A remote JSON feed, if the user has configured one in Settings. This is the
 *     hook for a curated Penang Foodie feed — see `docs/stall-feed-format.md`.
 *  2. The catalogue bundled in `assets/stalls.json`, always available offline.
 *
 * Note: this deliberately does not scrape facebook.com. See the README for why.
 */
class StallRepository(private val context: Context) {

    private var cached: StallCatalog? = null
    private var cachedFeedUrl: String? = null

    suspend fun load(feedUrl: String, forceRefresh: Boolean = false): StallCatalog {
        val hit = cached
        if (!forceRefresh && hit != null && cachedFeedUrl == feedUrl) return hit

        val catalog = withContext(Dispatchers.IO) {
            val remote = if (feedUrl.isNotBlank()) runCatching { fetchRemote(feedUrl) }.getOrNull() else null
            remote?.takeIf { it.stalls.isNotEmpty() } ?: loadBundled()
        }
        cached = catalog
        cachedFeedUrl = feedUrl
        return catalog
    }

    private fun loadBundled(): StallCatalog =
        context.assets.open("stalls.json").bufferedReader().use { StallCatalog.parse(it.readText()) }

    private fun fetchRemote(feedUrl: String): StallCatalog {
        val url = URL(feedUrl)
        require(url.protocol == "https") { "Feed URL must use https" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("Feed returned HTTP ${connection.responseCode}")
            return StallCatalog.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Sorts every stall by great-circle distance from [origin] and keeps those
     * inside [radiusMeters]. Android's own [Location.distanceBetween] does the maths.
     */
    fun nearby(catalog: StallCatalog, origin: Location, radiusMeters: Double): List<NearbyStall> {
        val results = FloatArray(1)
        return catalog.stalls
            .map { stall ->
                Location.distanceBetween(
                    origin.latitude, origin.longitude,
                    stall.latitude, stall.longitude,
                    results,
                )
                NearbyStall(stall, results[0].toDouble())
            }
            .filter { it.meters <= radiusMeters }
            .sortedBy { it.meters }
    }
}
