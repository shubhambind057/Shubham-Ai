package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.BatteryStatusInfo
import com.example.data.model.LocationSnapshot
import com.example.platform.DeviceActionResult
import com.example.platform.DeviceControlManager
import com.example.platform.InstalledAppSummary
import com.example.ui.components.EditorialBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.MetricPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.DisputedCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg

@Composable
fun PhoneControlsScreen(
    deviceManager: DeviceControlManager,
    batteryStatus: BatteryStatusInfo,
    flashlightOn: Boolean,
    locationSnapshot: LocationSnapshot?,
    locationError: String?,
    installedApps: List<InstalledAppSummary>,
    lastAction: DeviceActionResult?,
    onToggleFlashlight: (Boolean) -> Unit,
    onRefreshBattery: () -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onFetchLocation: (Boolean) -> Unit,
    onCheckLocationRevocation: () -> Unit,
    onRecordAction: (DeviceActionResult) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasFlash = remember(deviceManager) { deviceManager.hasFlashlightHardware() }

    var usePreciseLocation by remember { mutableStateOf(false) }
    var destinationQuery by remember { mutableStateOf("") }
    var youtubeQuery by remember { mutableStateOf("") }
    var browserQuery by remember { mutableStateOf("") }
    var phoneNumberInput by remember { mutableStateOf("") }
    var appFilter by remember { mutableStateOf("") }
    var pendingConfirmedCallNumber by remember { mutableStateOf<String?>(null) }

    // Detect permission revocation immediately whenever the user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshBattery()
                onCheckLocationRevocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            onFetchLocation(usePreciseLocation && fine)
        } else {
            onCheckLocationRevocation()
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val num = pendingConfirmedCallNumber
        pendingConfirmedCallNumber = null
        if (!num.isNullOrBlank()) {
            onRecordAction(deviceManager.initiateConfirmedCall(num))
        }
    }

    // Mandatory explicit confirmation dialog before initiating any phone call
    if (pendingConfirmedCallNumber != null) {
        val num = pendingConfirmedCallNumber!!
        AlertDialog(
            onDismissRequest = { pendingConfirmedCallNumber = null },
            title = { Text("Confirm Phone Call") },
            text = {
                Text(
                    "Do you want Shubham AI to initiate a phone call to $num?\n\n" +
                        "If CALL_PHONE permission is not granted, Android will safely open the Phone Dialer pre-filled with this number instead."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deviceManager.hasPermission(Manifest.permission.CALL_PHONE)) {
                            val target = num
                            pendingConfirmedCallNumber = null
                            onRecordAction(deviceManager.initiateConfirmedCall(target))
                        } else {
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    modifier = Modifier.testTag("confirm_call_dialog_button")
                ) {
                    Text("Call $num")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmedCallNumber = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filteredApps = remember(installedApps, appFilter) {
        val q = appFilter.trim()
        if (q.isEmpty()) installedApps.take(16)
        else installedApps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }.take(20)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                eyebrow = "JARVIS QUICK ACTIONS & ANDROID CONTROLS",
                title = "Flashlight, Battery, GPS & Apps",
                subtitle = "Supported Android intents & CameraManager torch APIs with explicit confirmation and zero API cost."
            )
        }

        if (lastAction != null) {
            item {
                if (lastAction.success) {
                    InfoNoticeBanner(title = "Device Action Executed", body = lastAction.message)
                } else {
                    ErrorBanner(message = lastAction.message)
                }
            }
        }

        // 0. Jarvis Quick Action: Hardware Flashlight (Torch)
        item {
            StudioCard(
                elevated = true,
                accentBorder = if (flashlightOn) MaterialTheme.colorScheme.primary else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (flashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (flashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Flashlight (Torch Control)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    EditorialBadge(
                        text = when {
                            !hasFlash -> "UNSUPPORTED"
                            flashlightOn -> "TORCH ON"
                            else -> "TORCH OFF"
                        },
                        containerColor = if (flashlightOn) VerifiedEmeraldBg else CautionGoldBg,
                        contentColor = if (flashlightOn) VerifiedEmerald else CautionGold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (hasFlash) {
                        "Uses Android CameraManager.setTorchMode(). Also responds to \"flashlight on\" / \"torch band karo\" in Chat & Voice."
                    } else {
                        "This device or emulator does not report a hardware camera flash unit."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onToggleFlashlight(true) },
                        enabled = hasFlash && !flashlightOn,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("flashlight_on_button")
                    ) {
                        Icon(Icons.Filled.FlashlightOn, contentDescription = "Turn ON", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Turn ON")
                    }
                    OutlinedButton(
                        onClick = { onToggleFlashlight(false) },
                        enabled = hasFlash && flashlightOn,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("flashlight_off_button")
                    ) {
                        Icon(Icons.Filled.FlashlightOff, contentDescription = "Turn OFF", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Turn OFF")
                    }
                }
            }
        }

        // 1. Live Battery Information (No extra permissions needed)
        item {
            StudioCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Battery Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onRefreshBattery,
                        modifier = Modifier.testTag("refresh_battery_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh battery")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = "Charge",
                        value = if (batteryStatus.percentage >= 0) "${batteryStatus.percentage}%" else "N/A",
                        accent = if (batteryStatus.percentage in 0..20) CautionGold else VerifiedEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Power",
                        value = batteryStatus.chargeSource,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Health",
                        value = batteryStatus.healthLabel,
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Temp: ${String.format("%.1f", batteryStatus.temperatureCelsius)}°C  •  Voltage: ${batteryStatus.voltageMv} mV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Foreground Location & Maps Navigation (Approximate vs Precise choice)
        item {
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Location & Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val hasLoc = deviceManager.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                        deviceManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    EditorialBadge(
                        text = if (hasLoc) "GRANTED" else "NOT GRANTED",
                        containerColor = if (hasLoc) VerifiedEmeraldBg else CautionGoldBg,
                        contentColor = if (hasLoc) VerifiedEmerald else CautionGold
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Choose foreground precision. No background or secret tracking is ever performed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !usePreciseLocation,
                        onClick = { usePreciseLocation = false },
                        label = { Text("Approximate") },
                        modifier = Modifier.testTag("location_mode_approximate")
                    )
                    FilterChip(
                        selected = usePreciseLocation,
                        onClick = { usePreciseLocation = true },
                        label = { Text("Precise GPS") },
                        modifier = Modifier.testTag("location_mode_precise")
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val needed = if (usePreciseLocation) {
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            } else {
                                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            val alreadyGranted = if (usePreciseLocation) {
                                deviceManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                deviceManager.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            if (alreadyGranted) {
                                onFetchLocation(usePreciseLocation)
                            } else {
                                locationPermissionLauncher.launch(needed)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fetch_location_button")
                    ) {
                        Text("Read Current Location")
                    }
                    OutlinedButton(
                        onClick = { onRecordAction(deviceManager.openAppSettings()) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Revoke in Settings", modifier = Modifier.size(18.dp))
                    }
                }

                if (locationSnapshot != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${locationSnapshot.areaLabel} (${locationSnapshot.precisionLabel})",
                        style = MaterialTheme.typography.titleSmall,
                        color = VerifiedEmerald
                    )
                    Text(
                        text = "Lat ${String.format("%.5f", locationSnapshot.latitude)}, Lng ${String.format("%.5f", locationSnapshot.longitude)} • ±${locationSnapshot.accuracyMeters.toInt()}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!locationError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(message = locationError)
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = destinationQuery,
                    onValueChange = { destinationQuery = it },
                    label = { Text("Maps navigation destination (e.g., India Gate, Delhi)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maps_destination_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onRecordAction(deviceManager.openMapsNavigation(destinationQuery)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_maps_navigation_button")
                ) {
                    Icon(Icons.Filled.Explore, contentDescription = "Navigate", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Launch Maps Navigation")
                }
            }
        }

        // 3. YouTube, Browser Search & Media Controls
        item {
            StudioCard {
                Text(
                    text = "YouTube, Web & Media Controls",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = youtubeQuery,
                    onValueChange = { youtubeQuery = it },
                    label = { Text("YouTube search query (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onRecordAction(deviceManager.openYouTube(youtubeQuery)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_youtube_button")
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = "Open YouTube", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open YouTube")
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = browserQuery,
                    onValueChange = { browserQuery = it },
                    label = { Text("Browser web search or https:// URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onRecordAction(deviceManager.openBrowserSearch(browserQuery)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_browser_search_button")
                ) {
                    Icon(Icons.Filled.Public, contentDescription = "Web Search", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Browser")
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRecordAction(deviceManager.openMusicApp()) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_music_app_button")
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = "Open Music", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Music App")
                    }
                    OutlinedButton(
                        onClick = { onRecordAction(deviceManager.toggleMediaPlayPause()) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("media_play_pause_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play / Pause")
                    }
                }
            }
        }

        // 4. Phone Dialer & Confirmed Call (Always Requires Confirmation Dialog)
        item {
            StudioCard {
                Text(
                    text = "Phone Dialer & Confirmed Call",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Shubham AI never places a call without your explicit confirmation dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phoneNumberInput,
                    onValueChange = { phoneNumberInput = it },
                    label = { Text("Phone number (e.g., +91 9876543210)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_number_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onRecordAction(deviceManager.openDialerWithNumber(phoneNumberInput))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_dialer_button")
                    ) {
                        Icon(Icons.Filled.Dialpad, contentDescription = "Open Dialer", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Dialer")
                    }
                    Button(
                        onClick = {
                            val sanitized = deviceManager.sanitizePhoneNumber(phoneNumberInput)
                            if (sanitized.isEmpty()) {
                                onRecordAction(DeviceActionResult(false, "Enter a valid phone number first."))
                            } else {
                                pendingConfirmedCallNumber = sanitized
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DisputedCrimson,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("initiate_call_button")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Call (Confirm)")
                    }
                }
            }
        }

        // 5. Launch Installed Apps (<queries> compliant)
        item {
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed Apps (${installedApps.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onRefreshInstalledApps) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh apps")
                    }
                }
                OutlinedTextField(
                    value = appFilter,
                    onValueChange = { appFilter = it },
                    label = { Text("Filter launchable apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filteredApps.isEmpty()) {
                    Text(
                        text = "No matching launchable apps found on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    filteredApps.forEachIndexed { idx, app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRecordAction(deviceManager.launchInstalledApp(app.packageName, app.label))
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "OPEN",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (idx < filteredApps.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
