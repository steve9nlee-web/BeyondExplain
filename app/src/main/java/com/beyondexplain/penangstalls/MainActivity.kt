package com.beyondexplain.penangstalls

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.beyondexplain.penangstalls.ui.NearbyScreen
import com.beyondexplain.penangstalls.ui.NearbyViewModel
import com.beyondexplain.penangstalls.ui.PenangStallsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NearbyViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }

    /**
     * Upgrading from "Approximate" to "Precise". Android only shows the dialog
     * again while the user hasn't locked the choice in; once they have, the
     * only route is the app's settings page.
     */
    private val preciseLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refresh()
        } else {
            openAppSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PenangStallsTheme {
                val state by viewModel.state.collectAsState()
                NearbyScreen(
                    state = state,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    onRequestPrecise = {
                        preciseLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    onRefresh = { viewModel.refresh(forceReload = true) },
                    onRadiusChange = viewModel::setRadius,
                    onFeedUrlChange = viewModel::setFeedUrl,
                    onVerifyPositionsChange = viewModel::setVerifyPositions,
                    onClearResolvedPositions = viewModel::clearResolvedPositions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the settings page, the permission may now be precise.
        viewModel.onReturnToForeground()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }
}
