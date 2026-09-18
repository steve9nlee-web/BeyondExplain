package com.beyondexplain.hazeindex

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Because the app targets SDK 35, Android 15 lays it out edge to edge: the window
 * starts at the very top of the screen, behind the status bar and any notch. Views
 * that should not sit under the system bars have to ask for the insets themselves —
 * otherwise the toolbar ends up hidden behind the status bar.
 *
 * Both helpers capture the view's designed padding/margin once, so repeated inset
 * dispatches (rotation, keyboard, gesture-bar changes) never accumulate.
 */
private val SYSTEM_BARS: Int
    get() = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

fun View.padForSystemBars(top: Boolean = false, bottom: Boolean = false, sides: Boolean = true) {
    val left = paddingLeft
    val topPadding = paddingTop
    val right = paddingRight
    val bottomPadding = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(SYSTEM_BARS)
        view.setPadding(
            left + if (sides) insets.left else 0,
            topPadding + if (top) insets.top else 0,
            right + if (sides) insets.right else 0,
            bottomPadding + if (bottom) insets.bottom else 0
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.marginForSystemBars(top: Boolean = false, bottom: Boolean = false, sides: Boolean = true) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val left = params.leftMargin
    val topMargin = params.topMargin
    val right = params.rightMargin
    val bottomMargin = params.bottomMargin

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(SYSTEM_BARS)
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { layout ->
            layout.leftMargin = left + if (sides) insets.left else 0
            layout.topMargin = topMargin + if (top) insets.top else 0
            layout.rightMargin = right + if (sides) insets.right else 0
            layout.bottomMargin = bottomMargin + if (bottom) insets.bottom else 0
            view.layoutParams = layout
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
