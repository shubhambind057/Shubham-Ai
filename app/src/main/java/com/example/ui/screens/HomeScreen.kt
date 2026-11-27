package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.BatteryStatusInfo
import com.example.data.model.PackageFormat
import com.example.data.model.ProjectStage
import com.example.data.model.StudioProject
import com.example.data.model.UserPreferenceProfile
import com.example.ui.components.EditorialBadge
import com.example.ui.components.MetricPill
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg

@Composable
fun HomeScreen(
    userName: String,
    preferences: UserPreferenceProfile,
    projects: List<StudioProject>,
    battery: BatteryStatusInfo,
    flashlightOn: Boolean = false,
    hasFlashlightHardware: Boolean = false,
    onToggleFlashlight: (Boolean) -> Unit = {},
    onStartTodaysResearch: () -> Unit,
    onOpenProject: (String) -> Unit,
    onNavigateVoice: () -> Unit,
    onNavigateChat: () -> Unit,
    onNavigateStudio: () -> Unit,
    onNavigateCamera: () -> Unit,
    onNavigatePhoneControls: () -> Unit,
    onNavigateServices: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val activeProject = projects.firstOrNull()
    val lang = AppLanguage.fromRaw(preferences.defaultLanguage)
    val pkg = PackageFormat.fromRaw(preferences.defaultPackageFormat)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero_1795728917081),
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(248.dp)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x550D0F14), Color(0xEE0D0F14))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorialBadge(
                            text = "PEHLU PROJECT • STUDIO",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        EditorialBadge(
                            text = if (preferences.economyModeEnabled) "${lang.label} • Lite Quota" else "${lang.label} • ${pkg.label}",
                            containerColor = Color(0xAA1C2230),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Namaste, ${userName.substringBefore(" ").ifBlank { "Shubham" }}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "PEHLU PROJECT Documentary Desk: 5-tier research, original scripts, editor-ready video blueprints, scene-linked clips & Jarvis quick controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = onStartTodaysResearch,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_todays_research_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Start Today's Research",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cta_start_todays_research),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Live Device & Studio Telemetry Row + Instant Zero-API-Cost Flashlight Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill(
                    label = "Battery",
                    value = if (battery.percentage >= 0) {
                        "${battery.percentage}% ${if (battery.charging) "⚡" else ""}"
                    } else "N/A",
                    accent = if (battery.percentage in 0..20) CautionGold else VerifiedEmerald,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "PEHLU Files",
                    value = "${projects.size} Saved",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Daily Auto",
                    value = if (preferences.automationEnabled) {
                        String.format("%02d:%02d", preferences.automationHour, preferences.automationMinute)
                    } else "Paused",
                    accent = if (preferences.automationEnabled) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            if (hasFlashlightHardware) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onToggleFlashlight(!flashlightOn) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("home_flashlight_toggle")
                ) {
                    Icon(
                        imageVector = if (flashlightOn) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                        contentDescription = "Toggle Flashlight",
                        tint = if (flashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (flashlightOn) "Jarvis Quick Action: Turn Flashlight OFF" else "Jarvis Quick Action: Turn Flashlight ON (0 AI Cost)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Active PEHLU PROJECT Dossier Card
        item {
            StudioCard(
                elevated = true,
                accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PEHLU PROJECT — ACTIVE DOSSIER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (activeProject != null) {
                        EditorialBadge(
                            text = ProjectStage.fromRaw(activeProject.stage).label,
                            containerColor = VerifiedEmeraldBg,
                            contentColor = VerifiedEmerald
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (activeProject == null) {
                    Text(
                        text = "No documentary dossier in production yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tap START TODAY'S RESEARCH above to discover History, Science, Tech, Psychology, or Unexplored PEHLU PROJECT stories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = activeProject.topicTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activeProject.hook.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "\"${activeProject.hook}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpenProject(activeProject.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("resume_active_project_card"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sceneCount = activeProject.parsedBlueprintScenes().size
                        Text(
                            text = "Open Workspace (${activeProject.parsedFacts().size} claims • $sceneCount scenes • ${activeProject.parsedFootage().size} clips)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open project",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Quick Launch Grid to All Integrated Modules
        item {
            Text(
                text = "QUICK LAUNCH MODULES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTile(
                        icon = Icons.Filled.GraphicEq,
                        title = "AI Voice Live",
                        subtitle = "Barge-in Hinglish voice",
                        tag = "home_tile_voice",
                        onClick = onNavigateVoice,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Filled.MovieCreation,
                        title = "Studio Archive",
                        subtitle = "Scripts, SRT & ZIP",
                        tag = "home_tile_studio",
                        onClick = onNavigateStudio,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTile(
                        icon = Icons.Filled.CameraAlt,
                        title = "Camera & Vision",
                        subtitle = "Objects & docs Q&A",
                        tag = "home_tile_camera",
                        onClick = onNavigateCamera,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "Phone Controls",
                        subtitle = "Apps, calls & GPS",
                        tag = "home_tile_phone",
                        onClick = onNavigatePhoneControls,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTile(
                        icon = Icons.Filled.Public,
                        title = "Services & Auto",
                        subtitle = "WorkManager & OAuth",
                        tag = "home_tile_services",
                        onClick = onNavigateServices,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Filled.Settings,
                        title = "Settings & Perms",
                        subtitle = "Memory & readiness",
                        tag = "home_tile_settings",
                        onClick = onNavigateSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Grounded Assistant Prompt Shortcut
        item {
            StudioCard(
                modifier = Modifier
                    .clickable(onClick = onNavigateChat)
                    .testTag("home_open_chat_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Personal AI Text Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Ask in Hindi, Hinglish, or English with optional live web reference citations and honest fact vs opinion separation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open AI Chat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
