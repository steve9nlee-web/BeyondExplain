package com.beyondexplain.penangstalls.data

import org.json.JSONObject

/** A parsed stall feed: the stalls plus where they came from. */
data class StallCatalog(
    val stalls: List<Stall>,
    val sourceLabel: String,
    val updatedAt: String,
) {
    companion object {
        val EMPTY = StallCatalog(emptyList(), "none", "")

        /**
         * Parses the feed format documented in `docs/stall-feed-format.md`.
         * Entries missing an id, name or coordinates are skipped rather than
         * failing the whole feed, so one bad row cannot empty the list.
         */
        fun parse(json: String): StallCatalog {
            val root = JSONObject(json)
            val array = root.optJSONArray("stalls") ?: return EMPTY
            val stalls = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    if (!item.has("lat") || !item.has("lng")) continue
                    val lat = item.optDouble("lat", Double.NaN)
                    val lng = item.optDouble("lng", Double.NaN)
                    if (lat.isNaN() || lng.isNaN()) continue
                    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
                    add(
                        Stall(
                            id = id,
                            name = name,
                            category = item.optString("category", "Food"),
                            area = item.optString("area", ""),
                            latitude = lat,
                            longitude = lng,
                            notes = item.optString("notes", ""),
                            facebookQuery = item.optString("facebookQuery", ""),
                        )
                    )
                }
            }
            return StallCatalog(
                stalls = stalls,
                sourceLabel = root.optString("source", "unknown"),
                updatedAt = root.optString("updatedAt", ""),
            )
        }
    }
}
