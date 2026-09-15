package com.beyondexplain.penangstalls.data

import android.net.Uri

/**
 * Links out to the Penang Foodie page.
 *
 * The app intentionally links out instead of pulling posts in: Facebook's
 * Graph API only exposes another page's posts under Page Public Content
 * Access (an app-review permission), and scraping the page HTML breaks
 * Facebook's terms. Tapping through opens the real page in the Facebook app
 * or a browser, where the content is served the way Facebook intends.
 */
object FacebookLinks {

    const val PAGE_URL = "https://www.facebook.com/penangfoodie"

    /** Searches the Penang Foodie page for a specific stall. */
    fun searchPageFor(stallName: String): Uri =
        Uri.parse("https://www.facebook.com/search/top/").buildUpon()
            .appendQueryParameter("q", "penangfoodie $stallName")
            .build()

    fun page(): Uri = Uri.parse(PAGE_URL)
}
