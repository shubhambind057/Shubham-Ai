package com.example.ui.screens

import android.Manifest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.IntegrationStatusItem
import com.example.data.model.PackageFormat
import com.example.data.model.UserPreferenceProfile
import com.example.platform.DeviceControlManager
import com.example.ui.components.EditorialBadge
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.ReadinessTierBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.DisputedCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg

@Composable
fun SettingsPermissionsScreen(
    userName: String,
    userEmail: String,
    preferences: UserPreferenceProfile,
    deviceManager: DeviceControlManager,
    integrationLedger: List<IntegrationStatusItem>,
    settingsBanner: String?,
    onSavePreferences: (UserPreferenceProfile, String) -> Unit,
    onOpenAppSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    var newMemoryNote by remember { mutableStateOf("") }
    var proxyUrlDraft by remember(preferences.backendProxyUrl) { mutableStateOf(preferences.backendProxyUrl) }

    val selectedLang = AppLanguage.fromRaw(preferences.defaultLanguage)
    val selectedFmt = PackageFormat.fromRaw(preferences.defaultPackageFormat)

    val permissionsAudit = listOf(
        "Camera (Live Vision)" to Manifest.permission.CAMERA,
        "Microphone (Real-Time Voice)" to Manifest.permission.RECORD_AUDIO,
        "Approximate Location" to Manifest.permission.ACCESS_COARSE_LOCATION,
        "Precise GPS Location" to Manifest.permission.ACCESS_FINE_LOCATION,
        "Notifications (Daily Research)" to Manifest.permission.POST_NOTIFICATIONS,
        "Direct Call (Optional; Dialer fallback)" to Manifest.permission.CALL_PHONE
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
                eyebrow = "PRIVACY, QUOTA & HONEST READINESS",
                title = "Settings & Permissions",
                subtitle = "Manage your persistent Google session, quota-saving Economy Mode, AI memory, permissions, and readiness."
            )
        }

        if (!settingsBanner.isNullOrBlank()) {
            item {
                InfoNoticeBanner(title = "Saved", body = settingsBanner)
            }
        }

        // 1. Account & Persistent Session Sign Out
        item {
            StudioCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName.ifBlank { "Google Account" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = userEmail.ifBlank { "Signed in via Google Credential Manager" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Persistent Session: You stay signed in automatically across app restarts. Signing out clears your local session only and never deletes saved PEHLU PROJECT dossiers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerifiedEmerald
                )
            }
        }

        // 1b. Cost & Quota Optimization Controls
        item {
            StudioCard {
                Text(
                    text = "Cost & Quota Optimization (Free-Tier Friendly)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Economy Lite Mode (Recommended)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uses fast gemini-3.5-flash-lite for chat, voice, scripts & blueprints; reserves heavy grounded model strictly for Deep Research.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = preferences.economyModeEnabled,
                        onCheckedChange = { enabled ->
                            onSavePreferences(
                                preferences.copy(economyModeEnabled = enabled),
                                if (enabled) "Economy Lite Mode enabled to conserve quota." else "High-detail model enabled for studio drafts."
                            )
                        },
                        modifier = Modifier.testTag("economy_mode_switch")
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Web Search in Chat by Default",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Keep OFF to avoid burning search grounding quota on ordinary questions (you can toggle it anytime in Chat).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = preferences.chatWebSearchDefault,
                        onCheckedChange = { enabled ->
                            onSavePreferences(
                                preferences.copy(chatWebSearchDefault = enabled),
                                if (enabled) "Chat Web Search default set to ON." else "Chat Web Search default set to OFF (Quota Saver)."
                            )
                        },
                        modifier = Modifier.testTag("chat_web_search_default_switch")
                    )
                }
            }
        }

        // 2. Language & Default Studio Package
        item {
            StudioCard {
                Text(
                    text = "Assistant Language & Studio Defaults",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Default Language",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = {
                                onSavePreferences(
                                    preferences.copy(defaultLanguage = lang.name),
                                    "Default language set to ${lang.label}."
                                )
                            },
                            label = { Text(lang.label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Default START TODAY'S RESEARCH Package",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PackageFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFmt == fmt,
                            onClick = {
                                onSavePreferences(
                                    preferences.copy(defaultPackageFormat = fmt.name),
                                    "Default package set to ${fmt.label}."
                                )
                            },
                            label = { Text(fmt.label) }
                        )
                    }
                }
            }
        }

        // 3. User-Approved AI Memory Editor
        item {
            StudioCard {
                Text(
                    text = "User-Approved AI Memory (${preferences.memoryNotes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Only preferences you explicitly add here are remembered across Chat and Voice sessions. Delete any item anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMemoryNote,
                        onValueChange = { newMemoryNote = it },
                        label = { Text("Add memory (e.g., Documentary tone: serious & crisp)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("memory_note_input")
                    )
                    Button(
                        onClick = {
                            val clean = newMemoryNote.trim()
                            if (clean.isNotEmpty()) {
                                val updatedList = (preferences.memoryNotes + clean).distinct().take(25)
                                newMemoryNote = ""
                                onSavePreferences(
                                    preferences.copy(memoryNotes = updatedList),
                                    "Memory preference saved to cloud."
                                )
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_memory_note_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Memory")
                    }
                }

                if (preferences.memoryNotes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    preferences.memoryNotes.forEach { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val updated = preferences.memoryNotes.filterNot { it == note }
                                    onSavePreferences(
                                        preferences.copy(memoryNotes = updated),
                                        "Removed memory note."
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete memory note",
                                    tint = DisputedCrimson
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Optional Backend Proxy URL (Zero Secrets in APK Policy)
        item {
            StudioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = "Zero Secrets",
                        tint = VerifiedEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Backend Proxy URL (Zero Secrets in APK)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "API secrets (Pexels, Pixabay, YouTube/Meta OAuth tokens) are NEVER stored inside this APK. Optionally enter your HTTPS backend proxy base URL below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = proxyUrlDraft,
                    onValueChange = { proxyUrlDraft = it },
                    label = { Text("https://your-proxy.example.com (non-secret URL only)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backend_proxy_url_input")
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val trimmed = proxyUrlDraft.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("https://")) {
                            onSavePreferences(
                                preferences.copy(backendProxyUrl = trimmed),
                                if (trimmed.isEmpty()) "Backend proxy URL cleared." else "Saved HTTPS backend proxy URL."
                            )
                        } else {
                            onSavePreferences(
                                preferences,
                                "Backend proxy URL must start with https:// (never enter plain HTTP or API keys)."
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_proxy_url_button")
                ) {
                    Text("Save Proxy URL")
                }
            }
        }

        // 5. Live Permissions Audit Dashboard
        item {
            StudioCard {
                Text(
                    text = "Android Permissions Audit",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                permissionsAudit.forEachIndexed { idx, (label, perm) ->
                    val granted = deviceManager.hasPermission(perm)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        EditorialBadge(
                            text = if (granted) "GRANTED" else "NOT GRANTED",
                            containerColor = if (granted) VerifiedEmeraldBg else CautionGoldBg,
                            contentColor = if (granted) VerifiedEmerald else CautionGold
                        )
                    }
                    if (idx < permissionsAudit.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_manage_permissions_button")
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Android Settings", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Manage / Revoke in Android Settings")
                }
            }
        }

        // 6. Honest Integration Readiness Ledger
        item {
            StudioCard {
                Text(
                    text = "Honest Integration Readiness Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Every module is transparently labeled by whether it works immediately, uses Firebase, needs permissions, or requires external setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                integrationLedger.forEachIndexed { idx, item ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            ReadinessTierBadge(tier = item.tier)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Setup: ${item.setupRequirement}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (idx < integrationLedger.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
