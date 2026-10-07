package com.beyondexplain.hazeindex

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the real inflated layout to a PNG so the result can actually be looked at,
 * rather than inferred. Not an assertion — a developer aid, kept because this
 * project has no emulator available.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {

    @Test
    fun `render the main screen with representative data`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<View>(android.R.id.content)

        fun text(id: Int, value: String) {
            activity.findViewById<TextView>(id)?.text = value
        }

        text(R.id.cityName, "George Town")
        text(R.id.observedAt, "Following your location · reading for 7 Oct, 17:00")

        // Mirrors showBanner(cta, opensDataSource = true). The decision itself is
        // asserted in StationKeyPromptTest; this is here to see how it reads.
        activity.findViewById<TextView>(R.id.statusBanner).apply {
            visibility = View.VISIBLE
            setText(R.string.modelled_cta)
            backgroundTintList = ColorStateList.valueOf(
                activity.getColor(R.color.banner_action)
            )
            setTextColor(activity.getColor(R.color.accent))
        }
        text(R.id.indexName, "AQI")
        text(R.id.indexValue, "88")
        text(R.id.bandLabel, "Moderate")
        activity.findViewById<View>(R.id.bandLabel).visibility = View.VISIBLE
        text(R.id.stationLine, "Measured · Minden, Pulau Pinang, Malaysia, 5.6 km away")
        text(R.id.advice, "Acceptable. Unusually sensitive people may want to limit long outdoor exertion.")
        text(R.id.pollutantsTitle, "Pollutant sub-indices")
        listOf(
            R.id.valuePm25 to "166.0", R.id.valuePm10 to "69.0", R.id.valueO3 to "20.8",
            R.id.valueNo2 to "1.5", R.id.valueSo2 to "1.1", R.id.valueCo to "0.1"
        ).forEach { (id, value) -> text(id, value) }
        listOf(R.id.unitPm25, R.id.unitPm10, R.id.unitO3, R.id.unitNo2, R.id.unitSo2, R.id.unitCo)
            .forEach { text(it, "AQI") }
        text(R.id.sourceLine, "Source: aqicn.org (World Air Quality Index) + Open-Meteo (trend)")
        text(R.id.fetchedAt, "Fetched just now")

        activity.findViewById<TrendView>(R.id.trendView).setData(
            (0 until 24).map { HourPoint(1_791_000_000L + it * 3600, 8.0 + it * 1.4) },
            8 * 3600
        )

        val width = 411 * 3
        val height = 891 * 3
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val out = File("build/reports/screens").apply { mkdirs() }
        File(out, "main.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("RENDERED ${File(out, "main.png").absolutePath}")
    }
}
