package com.beyondexplain.hazeindex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.beyondexplain.hazeindex.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screen is assembled from a hand-written layout, two custom views and six vector
 * drawables, none of which the compiler checks. These tests inflate the real thing and
 * draw it, so a broken id, a bad vector path or an out-of-bounds canvas call fails the
 * build rather than the first phone it lands on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenRenderTest {

    @Test
    fun `the main layout inflates with every view the activity binds to`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_HazeIndex)
        val binding = ActivityMainBinding.inflate(android.view.LayoutInflater.from(themed))

        // A spot check across every card, so a renamed id cannot slip through.
        assertTrue(binding.indexValue is TextView)
        assertTrue(binding.mainPollutant is TextView)
        assertEquals(View.GONE, binding.weatherCard.visibility)
        assertEquals(View.GONE, binding.regionsCard.visibility)
        assertEquals(View.GONE, binding.useMyLocation.visibility)
        assertEquals(View.VISIBLE, binding.propertyNotice.visibility)
        assertEquals(
            themed.getString(R.string.property_notice),
            binding.propertyNotice.text.toString()
        )
    }

    @Test
    fun `the activity starts and shows a reading for every band`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        Band.entries.forEach { band ->
            val report = sampleReport(band)
            renderInto(activity, report)

            val indexValue = activity.findViewById<TextView>(R.id.indexValue)
            assertEquals(report.indexValue.toString(), indexValue.text.toString())
            assertNotEquals("", activity.findViewById<TextView>(R.id.advice).text.toString())
            // The band face and the weather strip are the parts that only exist at runtime.
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.bandFace).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.weatherCard).visibility)

            // Drawing the whole tree exercises both custom views and all six vectors.
            drawOnce(activity.findViewById(android.R.id.content), widthPx = 720)
        }
        controller.destroy()
    }

    @Test
    fun `the scale ruler draws for every index without falling off the canvas`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = ScaleBarView(context)

        val scales = listOf(IndexScale.US_AQI_SEGMENTS, IndexScale.PSI_SEGMENTS)
        // Nothing yet, both ends of the scale, and a value past the top of the table.
        val readings = listOf(null, 0, 1, 50, 51, 149, 300, 500, 900)

        scales.forEach { segments ->
            readings.forEach { reading ->
                view.setScale(segments, reading)
                drawOnce(view, widthPx = 720)
                // A narrow screen is where the end labels and the marker bubble collide.
                drawOnce(view, widthPx = 240)
            }
        }
    }

    @Test
    fun `the trend chart draws with no data, one hour and a full day`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = TrendView(context)
        val start = 1_789_477_200L

        listOf(
            emptyList(),
            listOf(HourPoint(start, 12.0)),
            (0 until 24).map { HourPoint(start + it * 3600, it * 9.5) },
            (0 until 24).map { HourPoint(start + it * 3600, 0.0) }
        ).forEach { points ->
            view.setData(points, 8 * 3600)
            drawOnce(view, widthPx = 720)
        }
    }

    /** Measures, lays out and draws the view onto a real bitmap. */
    private fun drawOnce(view: View, widthPx: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = view.measuredHeight.coerceAtLeast(1)
        view.layout(0, 0, widthPx, height)
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    private fun renderInto(activity: MainActivity, report: HazeReport) {
        val method = MainActivity::class.java.getDeclaredMethod("render", UiState::class.java)
        method.isAccessible = true
        method.invoke(activity, UiState(city = report.city, report = report))
    }

    private fun sampleReport(band: Band): HazeReport {
        val value = when (band) {
            Band.GOOD -> 20
            Band.MODERATE -> 75
            Band.SENSITIVE -> 130
            Band.UNHEALTHY -> 180
            Band.VERY_UNHEALTHY -> 260
            Band.HAZARDOUS -> 420
        }
        return HazeReport(
            city = Cities.SINGAPORE,
            indexName = "US AQI",
            indexValue = value,
            band = band,
            pollutants = Pollutants(
                pm25 = 42.0, pm10 = 88.0, ozone = 60.0,
                nitrogenDioxide = 25.0, sulphurDioxide = 12.0, coMilligrams = 0.6
            ),
            observedAtEpochSeconds = 1_789_477_200L,
            utcOffsetSeconds = 8 * 3600,
            fetchedAtEpochMillis = System.currentTimeMillis(),
            sourceLabel = "test",
            trend = (0 until 24).map { HourPoint(1_789_477_200L + it * 3600, it * 4.0) },
            regions = listOf(RegionReading("North", 55), RegionReading("South", 61)),
            mainPollutant = Pollutant.PM25,
            weather = Weather(29.0, 78, 11.0, 3)
        )
    }
}
