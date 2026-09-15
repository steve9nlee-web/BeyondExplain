package com.beyondexplain.hazeindex

import android.Manifest
import android.content.Intent
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
        R.id.action_pick_city -> { showCityPicker(); true }
        R.id.action_my_location -> { toggleFollowDevice(); true }
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

        val clock = Times.cityClock(report.observedAtEpochSeconds, report.utcOffsetSeconds)
        binding.observedAt.text = when {
            state.isLocating -> getString(R.string.locating)
            state.followingDevice -> getString(R.string.following_device, clock)
            else -> getString(R.string.observed_at, clock)
        }

        binding.trendView.setData(report.trend, report.utcOffsetSeconds)

        bindPollutants(report.pollutants)
        bindRegions(report.regions)

        binding.sourceLine.text = getString(R.string.source_line, report.sourceLabel)
        binding.fetchedAt.text =
            getString(R.string.fetched_at, Times.relativeToNow(report.fetchedAtEpochMillis))

        showBanner(state.error ?: if (report.fromCache) getString(R.string.cached_notice) else null)
    }

    private fun bindPollutants(p: Pollutants) {
        binding.valuePm25.text = Numbers.concentration(p.pm25)
        binding.valuePm10.text = Numbers.concentration(p.pm10)
        binding.valueO3.text = Numbers.concentration(p.ozone)
        binding.valueNo2.text = Numbers.concentration(p.nitrogenDioxide)
        binding.valueSo2.text = Numbers.concentration(p.sulphurDioxide)
        binding.valueCo.text = Numbers.concentration(p.coMilligrams)
        binding.unitCo.text = getString(R.string.unit_mgm3)

        // PM2.5 is the haze pollutant, so colour it by severity.
        binding.valuePm25.setTextColor(
            p.pm25?.let { BandColors.of(this, IndexScale.forPm25(it)) }
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

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
