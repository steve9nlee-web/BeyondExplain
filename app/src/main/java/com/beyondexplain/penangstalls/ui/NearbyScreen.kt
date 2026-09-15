package com.beyondexplain.penangstalls.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.beyondexplain.penangstalls.data.AppSettings
import com.beyondexplain.penangstalls.data.DistanceBand
import com.beyondexplain.penangstalls.data.FacebookLinks
import com.beyondexplain.penangstalls.data.MapsLinks
import com.beyondexplain.penangstalls.data.NearbyStall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onRequestPermission: () -> Unit,
    onRequestPrecise: () -> Unit,
    onRefresh: () -> Unit,
    onRadiusChange: (Double) -> Unit,
    onFeedUrlChange: (String) -> Unit,
    onVerifyPositionsChange: (Boolean) -> Unit,
    onClearResolvedPositions: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Penang Stalls Nearby") },
                actions = {
                    TextButton(onClick = { showSettings = true }) { Text("Settings") }
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.status) {
                Status.NeedsPermission -> PermissionPrompt(state.message, onRequestPermission)
                Status.Locating -> Loading(state)
                Status.Failed -> ErrorState(state.message, onRefresh)
                Status.Ready -> ResultList(state, onRequestPrecise)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onRadiusChange = onRadiusChange,
            onFeedUrlChange = onFeedUrlChange,
            onVerifyPositionsChange = onVerifyPositionsChange,
            onClearResolvedPositions = onClearResolvedPositions,
        )
    }
}

@Composable
private fun Loading(state: NearbyUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Finding where you are…", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        val fix = state.fix
        Text(
            if (fix == null) {
                "Waiting for a GPS lock. This can take a few seconds outdoors, longer indoors."
            } else {
                "Best so far: ±${fix.accuracyMeters.toInt()} m. Sharpening…"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PermissionPrompt(message: String?, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Find hawker food around you", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            message ?: "The app needs your location to rank stalls by how far they are — " +
                "within 100 m, 100–200 m, and further out.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text("Allow location access") }
    }
}

@Composable
private fun ErrorState(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Can't show stalls yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message ?: "Unknown problem.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun ResultList(state: NearbyUiState, onRequestPrecise: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Summary(state) }

        if (!state.preciseLocation) {
            item { PreciseLocationBanner(onRequestPrecise) }
        } else if (state.coarseFix) {
            item { CoarseFixBanner(state) }
        }

        if (state.matchCount == 0) {
            item {
                Text(
                    "No stalls in the catalogue within ${formatRadius(state.radiusMeters)} of you. " +
                        "Widen the search radius in Settings, or point the app at your own feed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.bands.forEach { (band, stalls) ->
            item(key = "header-${band.name}") { BandHeader(band, stalls.size) }
            items(stalls, key = { it.stall.id }) { nearby ->
                StallCard(
                    nearby = nearby,
                    onOpenInMaps = { context.openUri(MapsLinks.place(nearby.stall)) },
                    onWalkThere = { context.openUri(MapsLinks.walkingDirections(nearby.stall)) },
                    onFacebook = { context.openUri(FacebookLinks.searchPageFor(nearby.stall.searchTerm)) },
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { context.openUri(FacebookLinks.page()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open the Penang Foodie page") }
        }
    }
}

@Composable
private fun Summary(state: NearbyUiState) {
    val fix = state.fix
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${state.matchCount} stalls within ${formatRadius(state.radiusMeters)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (fix != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "You: %.5f, %.5f · ±%d m%s".format(
                        fix.latitude,
                        fix.longitude,
                        fix.accuracyMeters.toInt(),
                        if (fix.provider.isNotBlank()) " · ${fix.provider}" else "",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.refining) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Still sharpening the fix — distances may shift.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Catalogue: ${state.catalogSize} stalls · source: ${state.sourceLabel}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    state.verifying ->
                        "Looking up stall positions by name… ${state.verifiedCount} confirmed so far."
                    !state.verifyPositions ->
                        "Using catalogue coordinates as-is. Turn on position lookup in Settings " +
                            "to rank by Google's positions instead."
                    !state.geocoderAvailable ->
                        "This device has no geocoder, so catalogue coordinates are used for " +
                            "ranking. Tapping a stall still opens Google Maps by name."
                    else ->
                        "${state.verifiedCount} of ${state.catalogSize} positions confirmed by " +
                            "name lookup; the rest use catalogue coordinates."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PreciseLocationBanner(onRequestPrecise: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Only approximate location is allowed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Android is giving this app a rough position, which can be a kilometre or more " +
                    "out. The 100 m and 200 m rings cannot work until precise location is on.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequestPrecise) { Text("Allow precise location") }
        }
    }
}

@Composable
private fun CoarseFixBanner(state: NearbyUiState) {
    val accuracy = state.fix?.accuracyMeters?.toInt() ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Your position is only accurate to ±$accuracy m",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "That is wider than the 100 m and 200 m rings, so treat the nearest entries as " +
                    "rough. Step outside for a clear view of the sky and tap Refresh — a settled " +
                    "GPS lock is usually ±5–10 m.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BandHeader(band: DistanceBand, count: Int) {
    Column {
        Spacer(Modifier.height(8.dp))
        Text(
            "${band.label}  ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StallCard(
    nearby: NearbyStall,
    onOpenInMaps: () -> Unit,
    onWalkThere: () -> Unit,
    onFacebook: () -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    nearby.stall.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    nearby.readableDistance,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    nearby.stall.category.takeIf { it.isNotBlank() },
                    nearby.stall.area.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (nearby.stall.verified) {
                    "Distance from Google's position for this place"
                } else {
                    "Distance from an approximate position — open in Maps for the exact spot"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (nearby.stall.verified) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (nearby.stall.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(nearby.stall.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpenInMaps, label = { Text("Find in Google Maps") })
                AssistChip(onClick = onWalkThere, label = { Text("Walk there") })
                AssistChip(onClick = onFacebook, label = { Text("On Penang Foodie") })
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    state: NearbyUiState,
    onDismiss: () -> Unit,
    onRadiusChange: (Double) -> Unit,
    onFeedUrlChange: (String) -> Unit,
    onVerifyPositionsChange: (Boolean) -> Unit,
    onClearResolvedPositions: () -> Unit,
) {
    var url by remember { mutableStateOf(state.feedUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Search radius", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettings.RADIUS_CHOICES.forEach { choice ->
                        FilterChip(
                            selected = state.radiusMeters == choice,
                            onClick = { onRadiusChange(choice) },
                            label = { Text(formatRadius(choice)) },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Look up positions by name", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = state.verifyPositions, onCheckedChange = onVerifyPositionsChange)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Asks Google where each stall actually is, using its name, and ranks the " +
                        "list by that instead of the catalogue's coordinates.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClearResolvedPositions) { Text("Re-check all positions") }

                Spacer(Modifier.height(20.dp))
                Text("Curated stall feed (optional)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "An https URL serving the JSON feed format. Leave blank to use the " +
                        "catalogue bundled with the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("https://…/stalls.json") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.trim() != state.feedUrl) onFeedUrlChange(url)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatRadius(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else "${(meters / 1000).toInt()} km"

private fun android.content.Context.openUri(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app on this phone can open that link.", Toast.LENGTH_SHORT).show()
    }
}
