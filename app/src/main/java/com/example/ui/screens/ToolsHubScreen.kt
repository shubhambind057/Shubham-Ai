package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.example.data.model.AppLanguage
import com.example.data.model.AutomationRun
import com.example.data.model.BatteryStatusInfo
import com.example.data.model.IntegrationStatusItem
import com.example.data.model.LocationSnapshot
import com.example.data.model.ReadinessTier
import com.example.data.model.UserPreferenceProfile
import com.example.data.repository.ExternalAnalyticsState
import com.example.data.repository.MetaInsightsSnapshot
import com.example.data.repository.YouTubeAnalyticsSnapshot
import com.example.platform.DeviceActionResult
import com.example.platform.DeviceControlManager
import com.example.platform.InstalledAppSummary
import com.example.ui.components.ReadinessTierBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.theme.ObsidianBg
import com.example.ui.viewmodel.VisionUiState

enum class ToolsSubScreen(val title: String) {
    HUB("Tools & Device Hub"),
    CAMERA("Camera & Vision"),
    PHONE_CONTROLS("Battery, GPS & Phone Controls"),
    SERVICES("Daily Automation & Analytics"),
    SETTINGS("Settings, Memory & Permissions")
}

@Composable
fun ToolsHubScreen(
    activeSubScreen: ToolsSubScreen,
    onSelectSubScreen: (ToolsSubScreen) -> Unit,
    userName: String,
    userEmail: String,
    selectedLanguage: AppLanguage,
    preferences: UserPreferenceProfile,
    deviceManager: DeviceControlManager,
    batteryStatus: BatteryStatusInfo,
    flashlightOn: Boolean,
    locationSnapshot: LocationSnapshot?,
    locationError: String?,
    installedApps: List<InstalledAppSummary>,
    lastAction: DeviceActionResult?,
    visionState: VisionUiState,
    periodicWorkState: List<WorkInfo>,
    automationRuns: List<AutomationRun>,
    youtubeState: ExternalAnalyticsState<YouTubeAnalyticsSnapshot>,
    metaState: ExternalAnalyticsState<MetaInsightsSnapshot>,
    integrationLedger: List<IntegrationStatusItem>,
    settingsBanner: String?,
    onAnalyzeBitmap: (Bitmap, String, AppLanguage) -> Unit,
    onResetVision: () -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onRefreshBattery: () -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onFetchLocation: (Boolean) -> Unit,
    onCheckLocationRevocation: () -> Unit,
    onRecordDeviceAction: (DeviceActionResult) -> Unit,
    onUpdateSchedule: (Boolean, Int, Int, String) -> Unit,
    onTriggerAutomationNow: () -> Unit,
    onRefreshExternalAnalytics: () -> Unit,
    onSavePreferences: (UserPreferenceProfile, String) -> Unit,
    onClearBanner: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(enabled = activeSubScreen != ToolsSubScreen.HUB) {
        onSelectSubScreen(ToolsSubScreen.HUB)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        if (activeSubScreen != ToolsSubScreen.HUB) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSelectSubScreen(ToolsSubScreen.HUB) },
                    modifier = Modifier.testTag("tools_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Tools Hub",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = activeSubScreen.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        when (activeSubScreen) {
            ToolsSubScreen.HUB -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionHeader(
                            eyebrow = "INTEGRATED ANDROID & STUDIO TOOLS",
                            title = "Tools Hub",
                            subtitle = "Camera Vision, Flashlight, Battery, GPS, Phone Controls, Daily Research Automation, Analytics APIs, and Privacy Settings."
                        )
                    }
                    item {
                        ToolsHubCard(
                            icon = Icons.Filled.CameraAlt,
                            title = "Camera & Multimodal Vision",
                            subtitle = "Explicit CameraX preview/capture + zero-permission Photo Picker + Gemini object & document Q&A.",
                            tier = ReadinessTier.PERMISSION_REQUIRED,
                            tag = "tools_card_camera",
                            onClick = { onSelectSubScreen(ToolsSubScreen.CAMERA) }
                        )
                    }
                    item {
                        ToolsHubCard(
                            icon = Icons.Filled.PhoneAndroid,
                            title = "Flashlight, Battery, GPS & Phone Controls",
                            subtitle = "Torch (${if (flashlightOn) "ON" else "OFF"}), live battery (${batteryStatus.percentage}%), GPS, Maps, YouTube, Dialer & Apps.",
                            tier = ReadinessTier.READY_NOW,
                            tag = "tools_card_phone",
                            onClick = { onSelectSubScreen(ToolsSubScreen.PHONE_CONTROLS) }
                        )
                    }
                    item {
                        ToolsHubCard(
                            icon = Icons.Filled.Public,
                            title = "Daily Automation & Analytics APIs",
                            subtitle = "WorkManager background research with run logs + YouTube Analytics & Meta Insights OAuth status.",
                            tier = ReadinessTier.EXTERNAL_SETUP_REQUIRED,
                            tag = "tools_card_services",
                            onClick = { onSelectSubScreen(ToolsSubScreen.SERVICES) }
                        )
                    }
                    item {
                        ToolsHubCard(
                            icon = Icons.Filled.Settings,
                            title = "Settings, Quota & Permissions",
                            subtitle = "Economy Lite (${if (preferences.economyModeEnabled) "ON" else "OFF"}), ${selectedLanguage.label}, editable AI memory & readiness ledger.",
                            tier = ReadinessTier.FIREBASE_READY,
                            tag = "tools_card_settings",
                            onClick = { onSelectSubScreen(ToolsSubScreen.SETTINGS) }
                        )
                    }
                }
            }

            ToolsSubScreen.CAMERA -> CameraVisionScreen(
                selectedLanguage = selectedLanguage,
                visionState = visionState,
                hasCameraPermission = { deviceManager.hasPermission(android.Manifest.permission.CAMERA) },
                onAnalyzeBitmap = onAnalyzeBitmap,
                onResetVision = onResetVision,
                onOpenAppSettings = { onRecordDeviceAction(deviceManager.openAppSettings()) }
            )

            ToolsSubScreen.PHONE_CONTROLS -> PhoneControlsScreen(
                deviceManager = deviceManager,
                batteryStatus = batteryStatus,
                flashlightOn = flashlightOn,
                locationSnapshot = locationSnapshot,
                locationError = locationError,
                installedApps = installedApps,
                lastAction = lastAction,
                onToggleFlashlight = onToggleFlashlight,
                onRefreshBattery = onRefreshBattery,
                onRefreshInstalledApps = onRefreshInstalledApps,
                onFetchLocation = onFetchLocation,
                onCheckLocationRevocation = onCheckLocationRevocation,
                onRecordAction = onRecordDeviceAction
            )

            ToolsSubScreen.SERVICES -> ConnectedServicesScreen(
                preferences = preferences,
                periodicWorkState = periodicWorkState,
                automationRuns = automationRuns,
                youtubeState = youtubeState,
                metaState = metaState,
                hasNotificationPermission = {
                    deviceManager.hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                settingsBanner = settingsBanner,
                onUpdateSchedule = onUpdateSchedule,
                onTriggerAutomationNow = onTriggerAutomationNow,
                onRefreshExternalAnalytics = onRefreshExternalAnalytics,
                onClearBanner = onClearBanner
            )

            ToolsSubScreen.SETTINGS -> SettingsPermissionsScreen(
                userName = userName,
                userEmail = userEmail,
                preferences = preferences,
                deviceManager = deviceManager,
                integrationLedger = integrationLedger,
                settingsBanner = settingsBanner,
                onSavePreferences = onSavePreferences,
                onOpenAppSettings = { onRecordDeviceAction(deviceManager.openAppSettings()) },
                onSignOut = onSignOut
            )
        }
    }
}

@Composable
private fun ToolsHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tier: ReadinessTier,
    tag: String,
    onClick: () -> Unit
) {
    StudioCard(
        elevated = true,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            ReadinessTierBadge(tier = tier)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open $title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
