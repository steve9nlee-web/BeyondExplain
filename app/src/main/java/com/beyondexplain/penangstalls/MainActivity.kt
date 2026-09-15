package com.beyondexplain.penangstalls

import android.Manifest
import android.os.Bundle
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
                    onRefresh = { viewModel.refresh(forceReload = true) },
                    onRadiusChange = viewModel::setRadius,
                    onFeedUrlChange = viewModel::setFeedUrl,
                )
            }
        }
    }
}
