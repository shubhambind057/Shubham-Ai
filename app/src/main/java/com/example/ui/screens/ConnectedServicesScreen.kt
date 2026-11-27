package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.example.data.model.AutomationRun
import com.example.data.model.UserPreferenceProfile
import com.example.data.repository.ExternalAnalyticsState
import com.example.data.repository.MetaInsightsSnapshot
import com.example.data.repository.YouTubeAnalyticsSnapshot
import com.example.ui.components.EditorialBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.MetricPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.DisputedCrimson
import com.example.ui.theme.DisputedCrimsonBg
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SetupSlate
import com.example.ui.theme.SetupSlateBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg

@Composable
fun ConnectedServicesScreen(
    preferences: UserPreferenceProfile,
    periodicWorkState: List<WorkInfo>,
    automationRuns: List<AutomationRun>,
    youtubeState: ExternalAnalyticsState<YouTubeAnalyticsSnapshot>,
    metaState: ExternalAnalyticsState<MetaInsightsSnapshot>,
    hasNotificationPermission: () -> Boolean,
    settingsBanner: String?,
    onUpdateSchedule: (Boolean, Int, Int, String) -> Unit,
    onTriggerAutomationNow: () -> Unit,
    onRefreshExternalAnalytics: () -> Unit,
    onClearBanner: () -> Unit
) {
    var notifGranted by remember { mutableStateOf(hasNotificationPermission()) }
    var enabled by remember(preferences.automationEnabled) { mutableStateOf(preferences.automationEnabled) }
    var hour by remember(preferences.automationHour) { mutableIntStateOf(preferences.automationHour) }
    var minute by remember(preferences.automationMinute) { mutableIntStateOf(preferences.automationMinute) }
    var beat by remember(preferences.automationBeat) { mutableStateOf(preferences.automationBeat) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    val workBadgeText = periodicWorkState.firstOrNull()?.state?.name ?: if (enabled) "SCHEDULED" else "STOPPED"
    val presetBeats = listOf(
        "History, Science & Current Affairs",
        "India Geopolitics & Economy",
        "Space, ISRO & Deep Tech",
        "Society & Investigative Explainer"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                eyebrow = "AUTOMATION & OFFICIAL ANALYTICS APIS",
                title = "Connected Services",
                subtitle = "On-device WorkManager daily research plus official YouTube & Meta API clients (never simulated, never password-based)."
            )
        }

        if (!settingsBanner.isNullOrBlank()) {
            item {
                InfoNoticeBanner(title = "Updated", body = settingsBanner)
            }
        }

        // 1. Daily Research Automation (WorkManager)
        item {
            StudioCard(elevated = true, accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = "Daily Automation",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Daily Research Automation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    EditorialBadge(
                        text = workBadgeText,
                        containerColor = if (enabled) VerifiedEmeraldBg else SetupSlateBg,
                        contentColor = if (enabled) VerifiedEmerald else SetupSlate
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (enabled) "Daily Schedule Active" else "Daily Schedule Paused / Stopped",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Target window: ~${String.format("%02d:%02d", hour, minute)} local time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            onUpdateSchedule(checked, hour, minute, beat)
                        },
                        modifier = Modifier.testTag("automation_enable_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { hour = (hour + 23) % 24 },
                        modifier = Modifier.height(48.dp)
                    ) { Text("-1h") }
                    OutlinedButton(
                        onClick = { hour = (hour + 1) % 24 },
                        modifier = Modifier.height(48.dp)
                    ) { Text("+1h") }
                    OutlinedButton(
                        onClick = { minute = (minute + 15) % 60 },
                        modifier = Modifier.height(48.dp)
                    ) { Text("+15m") }
                    Text(
                        text = String.format("%02d:%02d", hour, minute),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetBeats) { b ->
                        FilterChip(
                            selected = beat == b,
                            onClick = { beat = b },
                            label = { Text(b) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = beat,
                    onValueChange = { beat = it },
                    label = { Text("Daily topic beat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onUpdateSchedule(enabled, hour, minute, beat) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_automation_schedule_button")
                    ) {
                        Text("Save Schedule")
                    }
                    OutlinedButton(
                        onClick = onTriggerAutomationNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("run_automation_now_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Run Now", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run Now")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_notifications_button")
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = "Notifications", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Allow Completion & Failure Notifications")
                    }
                }

                Spacer(Modifier.height(12.dp))
                // Explicit honest disclosure of Android WorkManager behavior
                InfoNoticeBanner(
                    title = "Android Background Scheduling Reality",
                    body = "WorkManager requires active internet (NetworkType.CONNECTED) and retries up to 3 times with exponential backoff, then logs & notifies on terminal failure. Because of Android Doze mode, App Standby Buckets, and OEM battery optimization, exact clock-minute execution is NOT guaranteed on-device. If offline, Android defers execution until connectivity returns. Exact server-side cron requires Firebase Cloud Functions + Blaze billing.",
                    icon = Icons.Outlined.Info
                )
            }
        }

        // Automation Run Logs (Firestore)
        item {
            StudioCard {
                Text(
                    text = "Recent Automation Run Logs (${automationRuns.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                if (automationRuns.isEmpty()) {
                    Text(
                        text = "No background research runs logged yet. Tap Run Now above to test an immediate WorkManager run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    automationRuns.take(8).forEachIndexed { idx, run ->
                        val (bg, fg) = when (run.status) {
                            "COMPLETED" -> VerifiedEmeraldBg to VerifiedEmerald
                            "FAILED" -> DisputedCrimsonBg to DisputedCrimson
                            else -> CautionGoldBg to CautionGold
                        }
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EditorialBadge(text = "${run.triggerType} • ${run.status}", containerColor = bg, contentColor = fg)
                                Text(
                                    text = "Attempt ${run.attemptCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = run.summary.ifBlank { run.errorMessage },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (run.errorMessage.isNotBlank() && run.summary.isNotBlank()) {
                                Text(
                                    text = "Error: ${run.errorMessage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DisputedCrimson
                                )
                            }
                        }
                        if (idx < minOf(automationRuns.lastIndex, 7)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        // 2. YouTube Analytics API (Official OAuth via Backend Proxy — Zero Fake Numbers)
        item {
            AnalyticsSectionCard(
                title = "YouTube Analytics API",
                subtitle = "Official Google OAuth 2.0 channel metrics. Never claims secret knowledge of the YouTube recommendation algorithm.",
                state = youtubeState,
                onRefresh = onRefreshExternalAnalytics,
                testTagPrefix = "youtube_analytics"
            ) { snap ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("Views", "${snap.totalViews}", modifier = Modifier.weight(1f))
                    MetricPill("Watch Min", "${snap.estimatedMinutesWatched}", modifier = Modifier.weight(1f))
                    MetricPill("Avg Dur", "${snap.averageViewDurationSec}s", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Channel: ${snap.channelTitle} (${snap.periodLabel}) • Likes: ${snap.likes} • Comments: ${snap.comments} • Shares: ${snap.shares}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 3. Meta Graph API (Instagram & Facebook Insights — Official OAuth & App Review)
        item {
            AnalyticsSectionCard(
                title = "Meta Insights (Instagram & Facebook)",
                subtitle = "Official Meta Graph API for eligible Creator/Business accounts linked to a Facebook Page. Never asks for Instagram/Facebook passwords.",
                state = metaState,
                onRefresh = onRefreshExternalAnalytics,
                testTagPrefix = "meta_analytics"
            ) { snap ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("Reach", "${snap.reach}", modifier = Modifier.weight(1f))
                    MetricPill("Impressions", "${snap.impressions}", modifier = Modifier.weight(1f))
                    MetricPill("Profile Views", "${snap.profileViews}", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Account: ${snap.accountName} (${snap.platformLabel}) • Engagement: ${String.format("%.1f", snap.engagementRatePercent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun <T> AnalyticsSectionCard(
    title: String,
    subtitle: String,
    state: ExternalAnalyticsState<T>,
    onRefresh: () -> Unit,
    testTagPrefix: String,
    connectedContent: @Composable (T) -> Unit
) {
    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            val (badge, bg, fg) = when (state) {
                is ExternalAnalyticsState.Connected -> Triple("CONNECTED", VerifiedEmeraldBg, VerifiedEmerald)
                is ExternalAnalyticsState.Loading -> Triple("CHECKING", CautionGoldBg, CautionGold)
                is ExternalAnalyticsState.Error -> Triple("ERROR", DisputedCrimsonBg, DisputedCrimson)
                is ExternalAnalyticsState.NotConfigured -> Triple("NOT CONNECTED", SetupSlateBg, SetupSlate)
            }
            EditorialBadge(text = badge, containerColor = bg, contentColor = fg)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        when (state) {
            is ExternalAnalyticsState.NotConfigured -> {
                Text(
                    text = "External OAuth & Backend Proxy Setup Required (Zero Simulated Metrics):",
                    style = MaterialTheme.typography.labelMedium,
                    color = CautionGold,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                state.missingRequirements.forEach { req ->
                    Text(
                        text = "• $req",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Official endpoint: ${state.oauthEndpointHint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is ExternalAnalyticsState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking backend proxy OAuth status...", style = MaterialTheme.typography.bodySmall)
                }
            }
            is ExternalAnalyticsState.Connected -> connectedContent(state.data)
            is ExternalAnalyticsState.Error -> ErrorBanner(message = state.message)
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("${testTagPrefix}_check_button")
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Check Connection", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check Official API Connection")
        }
    }
}
