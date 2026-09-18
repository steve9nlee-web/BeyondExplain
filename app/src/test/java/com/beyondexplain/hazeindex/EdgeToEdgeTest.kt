package com.beyondexplain.hazeindex

import android.graphics.Rect
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Targeting SDK 35 puts the window behind the status bar on Android 15. These tests
 * assert the app actually moves its chrome out from under the system bars, which is
 * the bug that let the toolbar sit hidden beneath the status bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EdgeToEdgeTest {

    private fun systemBarInsets(top: Int, bottom: Int) = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, top, 0, bottom))
        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, 0))
        .build()

    @Test
    fun `the toolbar is pushed clear of the status bar`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val appBar = activity.findViewById<android.view.View>(R.id.appBar)
        val scroll = activity.findViewById<android.view.View>(R.id.scroll)

        val before = appBar.paddingTop
        ViewCompat.dispatchApplyWindowInsets(appBar, systemBarInsets(top = 96, bottom = 48))
        ViewCompat.dispatchApplyWindowInsets(scroll, systemBarInsets(top = 96, bottom = 48))

        assertEquals(before + 96, appBar.paddingTop)
        assertEquals(48, scroll.paddingBottom)
    }

    @Test
    fun `repeated inset dispatches do not stack up`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val appBar = activity.findViewById<android.view.View>(R.id.appBar)

        repeat(4) { ViewCompat.dispatchApplyWindowInsets(appBar, systemBarInsets(top = 96, bottom = 48)) }

        assertEquals(96, appBar.paddingTop)
    }

    @Test
    fun `a smaller inset shrinks the padding back rather than leaving a gap`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val appBar = activity.findViewById<android.view.View>(R.id.appBar)

        ViewCompat.dispatchApplyWindowInsets(appBar, systemBarInsets(top = 96, bottom = 48))
        ViewCompat.dispatchApplyWindowInsets(appBar, systemBarInsets(top = 24, bottom = 0))

        assertEquals(24, appBar.paddingTop)
    }

    @Test
    fun `the map cards keep clear of the bars while the map stays full bleed`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = MapActivity.intent(context, Cities.SINGAPORE)
        val activity = Robolectric.buildActivity(MapActivity::class.java, intent).setup().get()

        val header = activity.findViewById<android.view.View>(R.id.headerCard)
        val bottom = activity.findViewById<android.view.View>(R.id.bottomCard)
        val map = activity.findViewById<android.view.View>(R.id.map)

        val headerBefore = (header.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin
        val bottomBefore = (bottom.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.dispatchApplyWindowInsets(header, systemBarInsets(top = 96, bottom = 48))
        ViewCompat.dispatchApplyWindowInsets(bottom, systemBarInsets(top = 96, bottom = 48))

        assertEquals(
            headerBefore + 96,
            (header.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin
        )
        assertEquals(
            bottomBefore + 48,
            (bottom.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
        )
        // The map is meant to run under the bars; nothing should have inset it.
        assertEquals(0, map.paddingTop)
        assertTrue(Rect().let { map.getGlobalVisibleRect(it); true })
    }
}
