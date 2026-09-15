package com.beyondexplain.hazeindex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real screen to a PNG so the layout can be looked at without a phone.
 * Output lands in `build/screenshots/`. This is a development aid, not an assertion —
 * it only fails if the screen cannot be drawn at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun `render the main screen for a few bands`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val outputDir = File("build/screenshots").apply { mkdirs() }

        listOf(Band.GOOD, Band.MODERATE, Band.UNHEALTHY, Band.HAZARDOUS).forEach { band ->
            render(activity, sampleReport(band))
            val root = activity.window.decorView
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1080, 2400)

            val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File(outputDir, "main-${band.name.lowercase()}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }

        // One full-length render so the cards below the fold can be checked too.
        render(activity, sampleReport(Band.MODERATE))
        capture(activity, 1080, 4900, File(outputDir, "main-full.png"))
        controller.destroy()
    }

    private fun capture(activity: MainActivity, width: Int, height: Int, target: File) {
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun render(activity: MainActivity, report: HazeReport) {
        val method = MainActivity::class.java.getDeclaredMethod("render", UiState::class.java)
        method.isAccessible = true
        method.invoke(activity, UiState(city = report.city, report = report, followingDevice = true))
    }

    private fun sampleReport(band: Band): HazeReport {
        val value = when (band) {
            Band.GOOD -> 22
            Band.MODERATE -> 78
            Band.SENSITIVE -> 132
            Band.UNHEALTHY -> 174
            Band.VERY_UNHEALTHY -> 258
            Band.HAZARDOUS -> 412
        }
        return HazeReport(
            city = Cities.fromCoordinates("Bedok", 1.3236, 103.9273),
            indexName = "US AQI",
            indexValue = value,
            band = band,
            pollutants = Pollutants(
                pm25 = 42.3, pm10 = 88.1, ozone = 61.4,
                nitrogenDioxide = 24.7, sulphurDioxide = 11.9, coMilligrams = 0.62
            ),
            observedAtEpochSeconds = 1_789_477_200L,
            utcOffsetSeconds = 8 * 3600,
            fetchedAtEpochMillis = System.currentTimeMillis(),
            sourceLabel = "Open-Meteo Air Quality",
            trend = (0 until 24).map { HourPoint(1_789_477_200L + it * 3600, 8.0 + it * 3.4) },
            regions = listOf(
                RegionReading("North", 54), RegionReading("South", 61),
                RegionReading("East", 49), RegionReading("West", 72),
                RegionReading("Central", 58)
            ),
            mainPollutant = Pollutant.PM25,
            weather = Weather(29.0, 78, 11.0, 2)
        )
    }
}
