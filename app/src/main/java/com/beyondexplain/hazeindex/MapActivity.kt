package com.beyondexplain.hazeindex

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.beyondexplain.hazeindex.databinding.ActivityMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/**
 * The haze index as a picture: readings across the visible area, with the chosen
 * location's own number pinned in a card over the top. Panning or zooming reloads
 * the area, so the map is the thing you explore rather than a static image.
 */
class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private val viewModel: MapViewModel by viewModels()
    private lateinit var city: City

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid needs its cache and a real user agent set up before the view exists.
        Configuration.getInstance().apply {
            load(this@MapActivity, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
        }

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        city = cityFromIntent()
        setUpMap()
        buildLegend()

        binding.recentre.setOnClickListener { centreOnChosenLocation() }
        binding.refresh.setOnClickListener { viewModel.refresh() }

        viewModel.state.observe(this) { render(it) }
        viewModel.start(city)
    }

    private fun setUpMap() = with(binding.map) {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        // Inverting the tiles keeps the map readable next to the app's dark surfaces.
        overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        controller.setZoom(DEFAULT_ZOOM)
        controller.setCenter(GeoPoint(city.latitude, city.longitude))

        // The first layout is when the viewport finally has real bounds to report.
        addOnFirstLayoutListener { _, _, _, _, _ -> reportViewport() }
        addMapListener(
            DelayedMapListener(
                object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        reportViewport()
                        return true
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        reportViewport()
                        return true
                    }
                },
                VIEWPORT_SETTLE_MS
            )
        )
    }

    private fun reportViewport() {
        val box = binding.map.boundingBox ?: return
        viewModel.onViewportChanged(
            MapBounds(
                south = box.latSouth,
                west = box.lonWest,
                north = box.latNorth,
                east = box.lonEast
            )
        )
    }

    private fun centreOnChosenLocation() {
        binding.map.controller.animateTo(GeoPoint(city.latitude, city.longitude))
        binding.map.controller.setZoom(DEFAULT_ZOOM)
    }

    // ------------------------------------------------------------------ rendering

    private fun render(state: MapState) {
        binding.mapProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.headerCity.text = state.city.displayName

        val report = state.report
        if (report != null) {
            val color = BandColors.of(this, report.band)
            binding.headerValue.text = report.indexValue.toString()
            binding.headerValue.setTextColor(color)
            binding.headerBand.text = "${report.indexName} · ${report.band.label}"
            binding.headerBand.setTextColor(color)
            binding.headerProvenance.text = if (report.measured) {
                listOfNotNull(getString(R.string.map_measured), report.stationName)
                    .joinToString(" · ")
            } else {
                getString(R.string.map_modelled)
            }
        }

        binding.mapSource.text = buildString {
            append(
                if (state.area.readings.isEmpty() && !state.loading) {
                    getString(R.string.map_no_readings)
                } else {
                    getString(R.string.map_source, state.area.sourceLabel)
                }
            )
            append("\n")
            append(getString(R.string.map_attribution))
            state.error?.let { append("\n").append(it) }
        }

        drawOverlays(state)
    }

    private fun drawOverlays(state: MapState) {
        binding.map.overlays.clear()

        // Modelled samples become translucent cells; real stations become pins.
        state.area.readings.filter { !it.measured }.forEach { reading ->
            val radius = state.area.cellRadiusMetres ?: return@forEach
            val cell = Polygon(binding.map).apply {
                points = Polygon.pointsAsCircle(GeoPoint(reading.latitude, reading.longitude), radius)
                fillPaint.color = withAlpha(BandColors.of(this@MapActivity, reading.band), CELL_ALPHA)
                outlinePaint.color = Color.TRANSPARENT
                outlinePaint.strokeWidth = 0f
                setOnClickListener { _, _, _ -> showSelection(reading); true }
            }
            binding.map.overlays.add(cell)
        }

        state.area.readings.filter { it.measured }.forEach { reading ->
            binding.map.overlays.add(pin(reading, emphasised = false))
        }

        // The chosen location goes on last so it is never hidden behind a station.
        state.report?.let { report ->
            val marker = Marker(binding.map).apply {
                position = GeoPoint(state.city.latitude, state.city.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MarkerIcons.pill(this@MapActivity, report.indexValue, report.band, emphasised = true)
                setOnMarkerClickListener { _, _ ->
                    showSelection(
                        AreaReading(
                            name = "${state.city.displayName} — ${getString(R.string.map_chosen)}",
                            latitude = state.city.latitude,
                            longitude = state.city.longitude,
                            indexValue = report.indexValue,
                            indexName = report.indexName,
                            measured = report.measured,
                            observedAtEpochSeconds = report.observedAtEpochSeconds
                        )
                    )
                    true
                }
            }
            binding.map.overlays.add(marker)
        }

        binding.map.invalidate()
    }

    private fun pin(reading: AreaReading, emphasised: Boolean) = Marker(binding.map).apply {
        position = GeoPoint(reading.latitude, reading.longitude)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = MarkerIcons.pill(this@MapActivity, reading.indexValue, reading.band, emphasised)
        setOnMarkerClickListener { _, _ ->
            showSelection(reading)
            true
        }
    }

    private fun showSelection(reading: AreaReading) {
        binding.selectionTitle.text = reading.name
        binding.selectionDetail.visibility = View.VISIBLE
        binding.selectionDetail.text = buildString {
            append(
                getString(
                    R.string.map_selection_detail,
                    "${reading.indexName} ${reading.indexValue} · ${reading.band.label}",
                    if (reading.measured) {
                        getString(R.string.map_measured)
                    } else {
                        getString(R.string.map_modelled)
                    }
                )
            )
            reading.observedAtEpochSeconds?.let {
                append(" · ").append(Times.cityClock(it, SINGAPORE_OFFSET_FALLBACK))
            }
        }
        binding.selectionTitle.setTextColor(BandColors.of(this, reading.band))
    }

    private fun buildLegend() {
        val density = resources.displayMetrics.density
        Band.entries.forEach { band ->
            val chip = TextView(this).apply {
                text = shortLabel(band)
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MapActivity, R.color.background))
                setBackgroundResource(R.drawable.bg_chip)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    BandColors.of(this@MapActivity, band)
                )
                setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = (3 * density).toInt() }
            }
            binding.legend.addView(chip)
        }
    }

    private fun shortLabel(band: Band): String = when (band) {
        Band.GOOD -> "Good"
        Band.MODERATE -> "Moderate"
        Band.SENSITIVE -> "Sensitive"
        Band.UNHEALTHY -> "Unhealthy"
        Band.VERY_UNHEALTHY -> "Very bad"
        Band.HAZARDOUS -> "Hazardous"
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    // ----------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun cityFromIntent(): City {
        val id = intent.getStringExtra(EXTRA_ID)
        Cities.byId(id)?.let { return it }
        return City(
            id = id ?: Cities.DEVICE_ID,
            name = intent.getStringExtra(EXTRA_NAME) ?: Cities.SINGAPORE.name,
            country = intent.getStringExtra(EXTRA_COUNTRY).orEmpty(),
            latitude = intent.getDoubleExtra(EXTRA_LAT, Cities.SINGAPORE.latitude),
            longitude = intent.getDoubleExtra(EXTRA_LON, Cities.SINGAPORE.longitude),
            useNeaPsi = intent.getBooleanExtra(EXTRA_NEA, false)
        )
    }

    companion object {
        private const val EXTRA_ID = "city_id"
        private const val EXTRA_NAME = "city_name"
        private const val EXTRA_COUNTRY = "city_country"
        private const val EXTRA_LAT = "city_lat"
        private const val EXTRA_LON = "city_lon"
        private const val EXTRA_NEA = "city_nea"

        private const val DEFAULT_ZOOM = 9.5
        private const val VIEWPORT_SETTLE_MS = 700L
        private const val CELL_ALPHA = 105
        private const val SINGAPORE_OFFSET_FALLBACK = 8 * 3600

        fun intent(context: Context, city: City): Intent =
            Intent(context, MapActivity::class.java)
                .putExtra(EXTRA_ID, city.id)
                .putExtra(EXTRA_NAME, city.name)
                .putExtra(EXTRA_COUNTRY, city.country)
                .putExtra(EXTRA_LAT, city.latitude)
                .putExtra(EXTRA_LON, city.longitude)
                .putExtra(EXTRA_NEA, city.useNeaPsi)
    }
}
