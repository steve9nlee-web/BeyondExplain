package com.beyondexplain.hazeindex

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.beyondexplain.hazeindex.databinding.ActivityMainBinding
import com.beyondexplain.hazeindex.databinding.ItemRegionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: HazeViewModel by viewModels()
    private var followItem: MenuItem? = null

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFollowing()
        } else {
            onLocationRefused()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.accent),
            ContextCompat.getColor(this, R.color.band_moderate),
            ContextCompat.getColor(this, R.color.band_unhealthy)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface)
        )
        // The whole point of the app: a swipe down goes and gets a live reading.
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.useMyLocation.setOnClickListener { requestFollowDevice() }

        viewModel.state.observe(this) { render(it) }

        if (savedInstanceState == null) startUp()
    }

    /**
     * Location-first. The app is about the air where the phone is, so a fresh install
     * asks for the position straight away rather than making the user go find a toggle.
     */
    private fun startUp() {
        if (viewModel.shouldAskForLocation) {
            viewModel.markLocationAsked()
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            viewModel.refresh()
        }
    }

    override fun onStart() {
        super.onStart()
        // Position updates run only while the app is on screen.
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        followItem = menu.findItem(R.id.action_my_location)
        followItem?.isChecked = viewModel.isFollowingDevice
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_pick_city -> { showCityPicker(); true }
        R.id.action_my_location -> { toggleFollowDevice(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // --------------------------------------------------------------- follow mode

    private fun toggleFollowDevice() {
        if (viewModel.isFollowingDevice) {
            viewModel.stopFollowingDevice()
        } else {
            requestFollowDevice()
        }
    }

    private fun requestFollowDevice() {
        if (viewModel.hasLocationPermission()) {
            startFollowing()
        } else {
            viewModel.markLocationAsked()
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun startFollowing() {
        viewModel.followDeviceLocation()
        if (!viewModel.isLocationEnabled()) promptForLocationServices()
    }

    /**
     * Refusing location is a legitimate choice, so the app drops back to a city list
     * rather than sitting on an error. The toolbar toggle brings it back.
     */
    private fun onLocationRefused() {
        followItem?.isChecked = false
        toast(getString(R.string.location_permission_denied))
        if (viewModel.isFollowingDevice) viewModel.stopFollowingDevice() else viewModel.refresh()
    }

    private fun promptForLocationServices() {
        Snackbar.make(binding.root, R.string.location_services_off, Snackbar.LENGTH_LONG)
            .setAction(R.string.open_location_settings) {
                runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }
            .show()
    }

    // ------------------------------------------------------------------ render

    private fun render(state: UiState) {
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.cityName.text = state.city.displayName
        followItem?.isChecked = state.followingDevice

        // The pin lights up only when the reading really is the device's own position.
        binding.locationPin.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (state.followingDevice) R.color.accent else R.color.text_secondary
            )
        )
        binding.useMyLocation.visibility = if (state.followingDevice) View.GONE else View.VISIBLE

        val report = state.report
        if (report == null) {
            renderEmpty(state)
            return
        }

        val bandColor = BandColors.of(this, report.band)

        binding.heroCard.setCardBackgroundColor(BandColors.surfaceTint(this, report.band))
        binding.indexName.text = if (report.indexName == "PSI") "PSI (24-hour)" else report.indexName
        binding.indexValue.text = report.indexValue.toString()
        binding.indexValue.setTextColor(bandColor)

        binding.bandFace.visibility = View.VISIBLE
        binding.bandFace.setImageResource(BandColors.face(report.band))
        binding.bandFace.imageTintList = ColorStateList.valueOf(bandColor)

        binding.bandLabel.visibility = View.VISIBLE
        binding.bandLabel.text = report.band.label
        tint(binding.bandLabel, bandColor)

        binding.advice.text = report.band.advice
        binding.adviceIcon.imageTintList = ColorStateList.valueOf(bandColor)

        binding.scaleBar.setScale(IndexScale.segmentsFor(report.indexName), report.indexValue)

        val clock = Times.cityClock(report.observedAtEpochSeconds, report.utcOffsetSeconds)
        binding.observedAt.text = when {
            state.isLocating -> getString(R.string.locating)
            state.followingDevice -> getString(R.string.following_device, clock)
            else -> getString(R.string.observed_at, clock)
        }

        binding.trendView.setData(report.trend, report.utcOffsetSeconds)

        bindMainPollutant(report)
        bindPollutants(report.pollutants)
        bindWeather(report.weather)
        bindRegions(report.regions)

        binding.sourceLine.text = getString(R.string.source_line, report.sourceLabel)
        binding.fetchedAt.text =
            getString(R.string.fetched_at, Times.relativeToNow(report.fetchedAtEpochMillis))

        showBanner(state.error ?: if (report.fromCache) getString(R.string.cached_notice) else null)
    }

    /** Nothing to show yet: first launch, or a failure with no cached reading behind it. */
    private fun renderEmpty(state: UiState) {
        binding.observedAt.text = when {
            state.isLocating -> getString(R.string.locating)
            else -> getString(R.string.pull_hint)
        }
        binding.heroCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface))
        binding.indexValue.text = "--"
        binding.indexValue.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.bandLabel.visibility = View.GONE
        binding.bandFace.visibility = View.INVISIBLE
        binding.advice.text = ""
        binding.mainPollutant.text = getString(R.string.main_pollutant_none)
        binding.scaleBar.setScale(IndexScale.US_AQI_SEGMENTS, null)
        binding.trendView.setData(emptyList(), 0)
        bindPollutants(Pollutants())
        bindWeather(null)
        binding.regionsCard.visibility = View.GONE
        binding.sourceLine.text = ""
        binding.fetchedAt.text = ""
        showBanner(state.error)
    }

    /** IQAir's headline extra: which pollutant is actually driving the number. */
    private fun bindMainPollutant(report: HazeReport) {
        val main = report.mainPollutant
        if (main == null) {
            binding.mainPollutant.text = getString(R.string.main_pollutant_none)
            binding.mainPollutant.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            return
        }
        val unit = getString(if (main.isMilligrams) R.string.unit_mgm3 else R.string.unit_ugm3)
        binding.mainPollutant.text = getString(
            R.string.main_pollutant_value,
            main.label,
            Numbers.concentration(report.pollutants.valueOf(main)),
            unit
        )
        binding.mainPollutant.setTextColor(BandColors.of(this, report.band))
    }

    private fun bindPollutants(pollutants: Pollutants) {
        val cells = listOf(
            binding.valuePm25 to Pollutant.PM25,
            binding.valuePm10 to Pollutant.PM10,
            binding.valueO3 to Pollutant.OZONE,
            binding.valueNo2 to Pollutant.NITROGEN_DIOXIDE,
            binding.valueSo2 to Pollutant.SULPHUR_DIOXIDE,
            binding.valueCo to Pollutant.CARBON_MONOXIDE
        )
        // Each reading is coloured by its own AQI sub-index, so a single bad pollutant
        // stands out in the grid instead of hiding behind the headline.
        cells.forEach { (view, pollutant) ->
            val value = pollutants.valueOf(pollutant)
            view.text = Numbers.concentration(value)
            val subIndex = UsAqi.subIndex(pollutant, value)
            view.setTextColor(
                if (subIndex == null) ContextCompat.getColor(this, R.color.text_primary)
                else BandColors.of(this, IndexScale.forUsAqi(subIndex))
            )
        }
    }

    private fun bindWeather(weather: Weather?) {
        if (weather == null || weather.isEmpty) {
            binding.weatherCard.visibility = View.GONE
            return
        }
        binding.weatherCard.visibility = View.VISIBLE
        binding.valueTemperature.text =
            weather.temperatureCelsius?.let { getString(R.string.unit_celsius, Numbers.rounded(it)) }
                ?: getString(R.string.no_reading)
        binding.valueHumidity.text =
            weather.humidityPercent?.let { getString(R.string.unit_percent, it) }
                ?: getString(R.string.no_reading)
        binding.valueWind.text =
            weather.windKph?.let { getString(R.string.unit_kph, Numbers.rounded(it)) }
                ?: getString(R.string.no_reading)

        val condition = weather.condition
        binding.weatherCondition.text = condition.orEmpty()
        binding.weatherCondition.visibility = if (condition == null) View.GONE else View.VISIBLE
    }

    private fun bindRegions(regions: List<RegionReading>) {
        binding.regionsContainer.removeAllViews()
        if (regions.isEmpty()) {
            binding.regionsCard.visibility = View.GONE
            return
        }
        binding.regionsCard.visibility = View.VISIBLE
        regions.forEach { region ->
            val row = ItemRegionBinding.inflate(layoutInflater, binding.regionsContainer, false)
            val color = BandColors.of(this, IndexScale.forPsi(region.value))
            row.regionName.text = region.name
            row.regionValue.text = region.value.toString()
            row.regionValue.setTextColor(color)
            tint(row.regionDot, color)
            binding.regionsContainer.addView(row.root)
        }
    }

    private fun showBanner(message: String?) {
        binding.statusBanner.visibility = if (message == null) View.GONE else View.VISIBLE
        binding.statusBanner.text = message.orEmpty()
    }

    private fun tint(view: View, color: Int) {
        val background = view.background?.mutate() ?: return
        DrawableCompat.setTint(DrawableCompat.wrap(background), color)
        view.background = background
        if (view is TextView) view.setTextColor(ContextCompat.getColor(this, R.color.on_band))
    }

    // ------------------------------------------------------------- city picking

    private fun showCityPicker() {
        val names = Cities.ALL.map { it.displayName }.toTypedArray()
        val checked = Cities.ALL.indexOfFirst { it.id == viewModel.currentCity.id }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_pick_location)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                viewModel.selectCity(Cities.ALL[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
