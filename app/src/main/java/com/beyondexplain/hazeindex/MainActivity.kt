package com.beyondexplain.hazeindex

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.beyondexplain.hazeindex.databinding.ActivityMainBinding
import com.beyondexplain.hazeindex.databinding.DialogSourcesBinding
import com.beyondexplain.hazeindex.databinding.ItemRegionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: HazeViewModel by viewModels()
    private val deviceLocation by lazy { DeviceLocation(this) }
    private var followItem: MenuItem? = null

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startFollowing() else {
            followItem?.isChecked = false
            toast(getString(R.string.location_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark whatever the system theme is, so the bars always want
        // light icons; `auto` would pick dark ones on a light-themed device.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Keep the toolbar clear of the status bar and the content clear of the
        // navigation bar, now that the window runs the full height of the screen.
        binding.appBar.padForSystemBars(top = true)
        binding.scroll.padForSystemBars(bottom = true)

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

        viewModel.state.observe(this) { render(it) }

        if (savedInstanceState == null) viewModel.refresh()
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
        R.id.action_map -> { startActivity(MapActivity.intent(this, viewModel.currentCity)); true }
        R.id.action_pick_city -> { showCityPicker(); true }
        R.id.action_my_location -> { toggleFollowDevice(); true }
        R.id.action_sources -> { showSourceSettings(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // --------------------------------------------------------------- follow mode

    private fun toggleFollowDevice() {
        if (viewModel.isFollowingDevice) {
            viewModel.stopFollowingDevice()
            return
        }
        if (deviceLocation.hasPermission()) {
            startFollowing()
        } else {
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun startFollowing() {
        viewModel.followDeviceLocation()
        if (!deviceLocation.isLocationEnabled()) {
            promptForLocationServices()
        } else {
            toast(getString(R.string.following_device_on))
        }
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

        val report = state.report
        if (report == null) {
            binding.observedAt.text = when {
                state.isLocating -> getString(R.string.locating)
                else -> getString(R.string.pull_hint)
            }
            binding.indexValue.text = "--"
            binding.bandLabel.visibility = View.GONE
            binding.advice.text = ""
            binding.stationLine.text = ""
            binding.trendView.setData(emptyList(), 0)
            binding.regionsCard.visibility = View.GONE
            binding.sourceLine.text = ""
            binding.fetchedAt.text = ""
            showBanner(state.error)
            return
        }

        val bandColor = BandColors.of(this, report.band)

        binding.indexName.text = if (report.indexName == "PSI") "PSI (24-hour)" else report.indexName
        binding.indexValue.text = report.indexValue.toString()
        binding.indexValue.setTextColor(bandColor)

        binding.bandLabel.visibility = View.VISIBLE
        binding.bandLabel.text = report.band.label
        tint(binding.bandLabel, bandColor)

        binding.advice.text = report.band.advice
        binding.stationLine.text = provenance(report)

        val clock = Times.cityClock(report.observedAtEpochSeconds, report.utcOffsetSeconds)
        binding.observedAt.text = when {
            state.isLocating -> getString(R.string.locating)
            state.followingDevice -> getString(R.string.following_device, clock)
            else -> getString(R.string.observed_at, clock)
        }

        binding.trendView.setData(report.trend, report.utcOffsetSeconds)

        bindPollutants(report.pollutants, report.pollutantUnit)
        bindRegions(report.regions)

        binding.sourceLine.text = getString(R.string.source_line, report.sourceLabel)
        binding.fetchedAt.text =
            getString(R.string.fetched_at, Times.relativeToNow(report.fetchedAtEpochMillis))

        showBanner(
            state.error
                ?: report.notice
                ?: if (report.fromCache) getString(R.string.cached_notice) else null
        )
    }

    /** Says plainly whether the number was measured or simulated, and by what. */
    private fun provenance(report: HazeReport): CharSequence {
        if (!report.measured) {
            return getString(R.string.modelled_reading) +
                if (viewModel.settings().hasStationKey) "" else "\n" + getString(R.string.modelled_hint)
        }

        val station = report.stationName?.let { name ->
            report.stationDistanceMetres
                ?.let { getString(R.string.station_with_distance, name, Numbers.distance(it)) }
                ?: name
        }
        val head = station
            ?.let { getString(R.string.measured_at_station, it) }
            ?: getString(R.string.measured_no_station)

        val dominant = report.dominantPollutant
            ?.let { getString(R.string.dominant_pollutant, Numbers.pollutantName(it)) }
        return listOfNotNull(head, dominant).joinToString(" \u00b7 ")
    }

    private fun bindPollutants(p: Pollutants, unit: PollutantUnit) {
        binding.valuePm25.text = Numbers.concentration(p.pm25)
        binding.valuePm10.text = Numbers.concentration(p.pm10)
        binding.valueO3.text = Numbers.concentration(p.ozone)
        binding.valueNo2.text = Numbers.concentration(p.nitrogenDioxide)
        binding.valueSo2.text = Numbers.concentration(p.sulphurDioxide)
        binding.valueCo.text = Numbers.concentration(p.coMilligrams)

        // Sub-indices are already an AQI number, so the tiles have to say which it is.
        val unitLabel = when (unit) {
            PollutantUnit.AQI -> getString(R.string.unit_aqi)
            PollutantUnit.MICROGRAMS -> getString(R.string.unit_ugm3)
        }
        listOf(
            binding.unitPm25, binding.unitPm10, binding.unitO3, binding.unitNo2, binding.unitSo2
        ).forEach { it.text = unitLabel }
        binding.unitCo.text = when (unit) {
            PollutantUnit.AQI -> unitLabel
            PollutantUnit.MICROGRAMS -> getString(R.string.unit_mgm3)
        }
        binding.pollutantsTitle.setText(
            when (unit) {
                PollutantUnit.AQI -> R.string.section_pollutants_aqi
                PollutantUnit.MICROGRAMS -> R.string.section_pollutants
            }
        )

        // PM2.5 is the haze pollutant, so colour it by severity where that is meaningful.
        binding.valuePm25.setTextColor(
            p.pm25?.takeIf { unit == PollutantUnit.MICROGRAMS }
                ?.let { BandColors.of(this, IndexScale.forPm25(it)) }
                ?: p.pm25?.let { BandColors.of(this, IndexScale.forUsAqi(it.toInt())) }
                ?: ContextCompat.getColor(this, R.color.text_primary)
        )
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
        if (view is TextView) view.setTextColor(ContextCompat.getColor(this, R.color.background))
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

    // ---------------------------------------------------------- data source setup

    private fun showSourceSettings() {
        val settings = viewModel.settings()
        val view = DialogSourcesBinding.inflate(layoutInflater)

        view.waqiToken.setText(settings.waqiToken.orEmpty())
        view.iqAirKey.setText(settings.iqAirKey.orEmpty())
        when (settings.source) {
            SourceChoice.AUTO -> view.sourceAuto
            SourceChoice.WAQI -> view.sourceWaqi
            SourceChoice.IQAIR -> view.sourceIqAir
            SourceChoice.OPEN_METEO -> view.sourceOpenMeteo
        }.isChecked = true

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_sources)
            .setView(view.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                settings.waqiToken = view.waqiToken.text?.toString()
                settings.iqAirKey = view.iqAirKey.text?.toString()
                settings.source = when (view.sourceGroup.checkedRadioButtonId) {
                    R.id.sourceWaqi -> SourceChoice.WAQI
                    R.id.sourceIqAir -> SourceChoice.IQAIR
                    R.id.sourceOpenMeteo -> SourceChoice.OPEN_METEO
                    else -> SourceChoice.AUTO
                }
                toast(getString(R.string.sources_saved))
                viewModel.onSourceSettingsChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
