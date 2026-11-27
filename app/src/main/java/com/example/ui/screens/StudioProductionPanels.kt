package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AppLanguage
import com.example.data.model.ChecklistEntry
import com.example.data.model.FootageAssetItem
import com.example.data.model.ReusePermissionStatus
import com.example.data.model.StudioProject
import com.example.data.repository.ArchivalPortalLink
import com.example.data.repository.StockMediaSearchResult
import com.example.platform.SrtAndZipExporter
import com.example.ui.components.EditorialBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.DisputedCrimson
import com.example.ui.theme.DisputedCrimsonBg
import com.example.ui.theme.SetupSlate
import com.example.ui.theme.SetupSlateBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import java.io.File

@Composable
fun DualScriptsPanel(
    project: StudioProject?,
    isBusy: Boolean,
    onGenerateScripts: (StudioProject) -> Unit,
    onSaveScripts: (String, String, String, String) -> Unit,
    onBuildBlueprint: (StudioProject) -> Unit
) {
    if (project == null) {
        Column(Modifier.padding(16.dp)) {
            InfoNoticeBanner(
                title = "No Project Selected",
                body = "Approve a topic in Topics or choose a saved project in Saved first."
            )
        }
        return
    }

    var shortText by remember(project.id, project.shortScript) { mutableStateOf(project.shortScript) }
    var longText by remember(project.id, project.longScript) { mutableStateOf(project.longScript) }
    var visualDir by remember(project.id, project.visualDirections) { mutableStateOf(project.visualDirections) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onGenerateScripts(project) },
                    enabled = !isBusy && project.topicApproved,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_scripts_panel_button")
                ) {
                    Text("Generate Scripts")
                }
                OutlinedButton(
                    onClick = { onSaveScripts(project.id, shortText, longText, visualDir) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_scripts_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save scripts", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Edits")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onBuildBlueprint(project) },
                enabled = !isBusy && project.topicApproved,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("scripts_to_blueprint_button")
            ) {
                Text("Next: Build Complete Documentary Video Blueprint")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Build Blueprint", modifier = Modifier.size(18.dp))
            }
        }

        // 5 Title Options, 3 Thumbnail Concepts & YouTube Chapters
        if (project.titleOptions.isNotEmpty() || project.thumbnailConcepts.isNotEmpty() || project.descriptionAndChapters.isNotBlank()) {
            item {
                StudioCard(elevated = true, accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                    EditorialBadge(
                        text = "PEHLU PROJECT • PACKAGING (TITLES, THUMBNAILS & CHAPTERS)",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    if (project.titleOptions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "5 Title Options:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        project.titleOptions.forEachIndexed { idx, title ->
                            Text(
                                text = "${idx + 1}. $title",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    if (project.thumbnailConcepts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "3 Thumbnail Concepts:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        project.thumbnailConcepts.forEachIndexed { idx, thumb ->
                            Text(
                                text = "${idx + 1}. $thumb",
                                style = MaterialTheme.typography.bodySmall,
                                color = CautionGold,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    if (project.descriptionAndChapters.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "YouTube Description & Chapters:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = project.descriptionAndChapters,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            StudioCard(elevated = true) {
                EditorialBadge(
                    text = "40–60s YOUTUBE SHORTS / REELS SCRIPT",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = shortText,
                    onValueChange = { shortText = it },
                    minLines = 6,
                    maxLines = 14,
                    placeholder = { Text("40-60s Hinglish Shorts script with hook & sources...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("short_script_editor")
                )
            }
        }

        item {
            StudioCard {
                EditorialBadge(
                    text = "LONG YOUTUBE DOCUMENTARY SCRIPT",
                    containerColor = VerifiedEmeraldBg,
                    contentColor = VerifiedEmerald
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = longText,
                    onValueChange = { longText = it },
                    minLines = 8,
                    maxLines = 20,
                    placeholder = { Text("Multi-chapter YouTube documentary narration...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("long_script_editor")
                )
            }
        }

        if (project.voiceoverDirectionNotes.isNotBlank()) {
            item {
                StudioCard {
                    Text(
                        text = "VOICE-OVER DIRECTION, PRONUNCIATION & PACING NOTES",
                        style = MaterialTheme.typography.labelMedium,
                        color = VerifiedEmerald
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = project.voiceoverDirectionNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            StudioCard {
                Text(
                    text = "SCENE-BY-SCENE VISUAL DIRECTIONS & ON-SCREEN TEXT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = visualDir,
                    onValueChange = { visualDir = it },
                    minLines = 4,
                    maxLines = 10,
                    placeholder = { Text("Visual framing, archival maps, B-roll cues...") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (project.onScreenText.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "On-Screen Text Callouts:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    project.onScreenText.forEach { callout ->
                        Text(
                            text = "• $callout",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FootageAndVoiceoverPanel(
    project: StudioProject?,
    stockResult: StockMediaSearchResult?,
    archivalPortals: List<ArchivalPortalLink>,
    clipSearchQuerySeed: String,
    clipTargetSceneNumber: Int,
    clipTargetTimestamp: String,
    backendProxyUrl: String,
    selectedVoice: String,
    isBusy: Boolean,
    onSearchStock: (String, String, String) -> Unit,
    onAttachFootage: (StudioProject, FootageAssetItem) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onGenerateVoiceover: (StudioProject, Boolean) -> Unit,
    onGenerateSceneVoiceover: (StudioProject, Int) -> Unit,
    onPreviewAudioFile: (File) -> Unit,
    onPreviewDeviceTts: (String, AppLanguage) -> Unit,
    onStopAudioPreview: () -> Unit,
    onShareAudioFile: (File) -> Unit
) {
    if (project == null) {
        Column(Modifier.padding(16.dp)) {
            InfoNoticeBanner(
                title = "No Project Selected",
                body = "Approve a topic in Topics first to search license-checked visuals and produce voice-overs."
            )
        }
        return
    }

    val providers = listOf("Wikimedia Commons", "Pexels", "Pixabay")
    val voices = listOf("Kore", "Puck", "Charon", "Fenrir")
    var selectedProvider by remember { mutableStateOf("Wikimedia Commons") }
    var searchQuery by remember(project.id, clipSearchQuerySeed) {
        mutableStateOf(clipSearchQuerySeed.ifBlank { project.topicTitle.take(60) })
    }
    val localAudioFile = remember(project.voiceoverLocalPath) {
        project.voiceoverLocalPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }
    val scenes = remember(project.blueprintScenes) { project.parsedBlueprintScenes() }
    val shortWords = remember(project.shortScript) {
        project.shortScript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }
    val longWords = remember(project.longScript) {
        project.longScript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Module 8: Voice-Over Studio (Free Device TTS default + Optional Gemini TTS)
        item {
            StudioCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOICE-OVER STUDIO (OPTIONAL AI WAV + FREE TTS)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Est. Narration (@135 WPM): Short ~${(shortWords / 2.25f).toInt()}s ($shortWords words) • Long ~${(longWords / 135f).toInt()} min ($longWords words)",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerifiedEmerald
                )
                if (project.voiceoverDirectionNotes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Pacing & Pronunciation: ${project.voiceoverDirectionNotes.take(220)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(voices) { voice ->
                        FilterChip(
                            selected = selectedVoice == voice,
                            onClick = { onSelectVoice(voice) },
                            label = { Text(voice) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onGenerateVoiceover(project, true) },
                        enabled = !isBusy && project.shortScript.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("generate_voiceover_button")
                    ) {
                        Text("Short WAV (Opt)")
                    }
                    OutlinedButton(
                        onClick = {
                            val text = project.shortScript.ifBlank { project.longScript }
                            if (text.isNotBlank()) {
                                onPreviewDeviceTts(text, AppLanguage.fromRaw(project.language))
                            }
                        },
                        enabled = project.shortScript.isNotBlank() || project.longScript.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Preview TTS", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Free TTS Preview")
                    }
                }
                if (scenes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Scene-Wise Optional WAV Generation (Quota Saver):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(scenes, key = { it.sceneNumber }) { sc ->
                            val hasWav = sc.audioLocalPath.isNotBlank() && File(sc.audioLocalPath).exists()
                            FilterChip(
                                selected = hasWav,
                                onClick = {
                                    if (hasWav) onPreviewAudioFile(File(sc.audioLocalPath))
                                    else onGenerateSceneVoiceover(project, sc.sceneNumber)
                                },
                                label = {
                                    Text(if (hasWav) "Play Sc ${sc.sceneNumber}" else "Gen Sc ${sc.sceneNumber}")
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (localAudioFile != null) {
                        OutlinedButton(
                            onClick = { onPreviewAudioFile(localAudioFile) },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play WAV", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play WAV (${localAudioFile.length() / 1024} KB)")
                        }
                        OutlinedButton(
                            onClick = { onShareAudioFile(localAudioFile) },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Export WAV", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export WAV")
                        }
                    } else {
                        Text(
                            text = "Status: No full WAV generated yet (we never fake audio).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = onStopAudioPreview,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop playback", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Module 7: Scene-Linked Clip & Image Discovery
        item {
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLIP & IMAGE DISCOVERY (REUSE-CHECKED)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (clipTargetSceneNumber > 0) {
                        EditorialBadge(
                            text = "Scene $clipTargetSceneNumber • $clipTargetTimestamp",
                            containerColor = VerifiedEmeraldBg,
                            contentColor = VerifiedEmerald
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers) { p ->
                        FilterChip(
                            selected = selectedProvider == p,
                            onClick = { selectedProvider = p },
                            label = { Text(if (p == "Wikimedia Commons") "$p (Live)" else "$p (Proxy)") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search visual subject or archive keyword") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_media_search_input")
                    )
                    Button(
                        onClick = { onSearchStock(selectedProvider, searchQuery, backendProxyUrl) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("stock_media_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search media")
                    }
                }
            }
        }

        // Curated Archival Portals (Internet Archive, Wikimedia, NASA, Library of Congress)
        if (archivalPortals.isNotEmpty()) {
            item {
                StudioCard {
                    Text(
                        text = "CURATED ARCHIVAL RESEARCH PORTALS (LIVE LINKS)",
                        style = MaterialTheme.typography.labelMedium,
                        color = VerifiedEmerald
                    )
                    Spacer(Modifier.height(6.dp))
                    archivalPortals.forEachIndexed { idx, portal ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(portal.searchUrl) }
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = portal.portalName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Outlined.Link,
                                    contentDescription = "Open archive portal",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = portal.mediaScope,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "License note: ${portal.licenseReminder}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CautionGold
                            )
                        }
                        if (idx < archivalPortals.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        // Stock Search Results
        when (stockResult) {
            is StockMediaSearchResult.ProxyRequired -> {
                item {
                    InfoNoticeBanner(
                        title = "${stockResult.provider} — Server-Side Backend Proxy Required",
                        body = stockResult.explanation
                    )
                }
            }
            is StockMediaSearchResult.Error -> {
                item { ErrorBanner(message = "${stockResult.provider}: ${stockResult.message}") }
            }
            is StockMediaSearchResult.Success -> {
                if (stockResult.assets.isEmpty()) {
                    item {
                        InfoNoticeBanner(
                            title = "No Assets Found",
                            body = "Try a shorter English keyword on Wikimedia Commons (e.g., \"ISRO rocket\", \"Indus Valley\", \"solar eclipse\")."
                        )
                    }
                } else {
                    items(stockResult.assets) { asset ->
                        FootageAssetCard(
                            asset = asset,
                            alreadyAttached = project.parsedFootage().any {
                                it.assetUrl == asset.assetUrl && it.sceneNumber == asset.sceneNumber
                            },
                            onAttach = { onAttachFootage(project, asset) },
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
            }
            null -> Unit
        }

        // Attached Assets
        val attached = project.parsedFootage()
        if (attached.isNotEmpty()) {
            item {
                Text(
                    text = "ATTACHED PROJECT ASSETS (${attached.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = VerifiedEmerald
                )
            }
            items(attached) { asset ->
                FootageAssetCard(
                    asset = asset,
                    alreadyAttached = true,
                    onAttach = {},
                    onOpenUrl = onOpenUrl
                )
            }
        }
    }
}

@Composable
private fun FootageAssetCard(
    asset: FootageAssetItem,
    alreadyAttached: Boolean,
    onAttach: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val reuseStatus = ReusePermissionStatus.fromRaw(asset.reuseStatus)
    val (reuseBg, reuseFg) = when (reuseStatus) {
        ReusePermissionStatus.CONFIRMED_OPEN -> VerifiedEmeraldBg to VerifiedEmerald
        ReusePermissionStatus.ATTRIBUTION_REQUIRED -> CautionGoldBg to CautionGold
        ReusePermissionStatus.UNCLEAR_VERIFY -> SetupSlateBg to SetupSlate
        ReusePermissionStatus.RESTRICTED_REFERENCE_ONLY -> DisputedCrimsonBg to DisputedCrimson
    }

    StudioCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (asset.thumbnailUrl.startsWith("http")) {
                AsyncImage(
                    model = asset.thumbnailUrl,
                    contentDescription = asset.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = asset.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Text(
                    text = "${asset.mediaType} • Creator: ${asset.creator}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorialBadge(
                        text = reuseStatus.label,
                        containerColor = reuseBg,
                        contentColor = reuseFg
                    )
                    if (asset.sceneNumber > 0) {
                        EditorialBadge(
                            text = "Scene ${asset.sceneNumber}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "License: ${asset.license}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = asset.commercialCaution,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onOpenUrl(asset.assetUrl) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Outlined.Link, contentDescription = "Open source page", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Source Page")
            }
            Button(
                onClick = onAttach,
                enabled = !alreadyAttached,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    if (alreadyAttached) Icons.Filled.CheckCircle else Icons.Filled.Add,
                    contentDescription = "Attach asset",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (alreadyAttached) "Attached" else "Attach")
            }
        }
    }
}

@Composable
fun SubtitlesCapCutExportPanel(
    project: StudioProject?,
    isBusy: Boolean,
    onSaveSubtitlesAndCapCut: (String, String, String, List<ChecklistEntry>, List<ChecklistEntry>) -> Unit,
    onSetFinalApproved: (StudioProject, Boolean) -> Unit,
    onExportSrt: (StudioProject) -> Unit,
    onShareZip: (StudioProject) -> Unit,
    onSaveZipToDevice: (StudioProject) -> Unit
) {
    if (project == null) {
        Column(Modifier.padding(16.dp)) {
            InfoNoticeBanner(
                title = "No Project Selected",
                body = "Approve a topic and generate scripts/blueprint first to edit subtitles, review the 10-point audit, and export the 14-deliverable ZIP."
            )
        }
        return
    }

    var srtText by remember(project.id, project.subtitlesSrt) {
        mutableStateOf(
            project.subtitlesSrt.ifBlank {
                SrtAndZipExporter.generateSrtFromBlueprint(project)
            }
        )
    }
    var capcutText by remember(project.id, project.capcutPlan) { mutableStateOf(project.capcutPlan) }
    var checklist by remember(project.id, project.capcutChecklist) {
        mutableStateOf(
            project.parsedChecklist().ifEmpty {
                listOf(
                    ChecklistEntry("Confirm all facts & on-screen citations match dossier", false),
                    ChecklistEntry("Verify license & attribution credits for every clip", false),
                    ChecklistEntry("Check Hindi/English SRT timing against voiceover waveform", false),
                    ChecklistEntry("Mix royalty-free BGM under -22dB behind narration", false)
                )
            }
        )
    }
    var auditChecklist by remember(project.id, project.auditChecklist, project.verifiedFacts, project.blueprintScenes, project.footageAssets) {
        mutableStateOf(
            project.parsedAuditChecklist().ifEmpty {
                SrtAndZipExporter.buildTenPointAudit(project)
            }
        )
    }
    val srtValid = remember(srtText) { SrtAndZipExporter.isValidSrt(srtText) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Synchronized SRT Editor
        item {
            StudioCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYNCHRONIZED SRT SUBTITLES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    EditorialBadge(
                        text = if (srtValid) "Valid UTF-8 SRT" else "Check Format",
                        containerColor = if (srtValid) VerifiedEmeraldBg else CautionGoldBg,
                        contentColor = if (srtValid) VerifiedEmerald else CautionGold
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = srtText,
                    onValueChange = { srtText = it },
                    minLines = 6,
                    maxLines = 14,
                    textStyle = MaterialTheme.typography.labelMedium,
                    placeholder = { Text("1\n00:00:00,000 --> 00:00:04,000\nNamaste dosto...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("srt_subtitle_editor")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            srtText = SrtAndZipExporter.generateSrtFromBlueprint(project)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Sync from Blueprint")
                    }
                    OutlinedButton(
                        onClick = { onExportSrt(project.copy(subtitlesSrt = srtText)) },
                        enabled = srtText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("export_srt_button")
                    ) {
                        Text("Export .SRT")
                    }
                }
            }
        }

        // Module 9: Structured 7-Column Editing Sheet Preview
        item {
            SevenColumnEditingSheetCard(project = project)
        }

        // CapCut Production Plan & Final Checklist
        item {
            StudioCard {
                Text(
                    text = "CAPCUT PRODUCTION PLAN & TIMELINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = capcutText,
                    onValueChange = { capcutText = it },
                    minLines = 5,
                    maxLines = 14,
                    placeholder = { Text("Scene-by-scene clip durations, VO placement, transitions, SFX & licensed BGM...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "CapCut Timeline Checklist",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                checklist.forEachIndexed { idx, entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checklist = checklist.toMutableList().apply {
                                    this[idx] = entry.copy(completed = !entry.completed)
                                }
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = entry.completed,
                            onCheckedChange = { checked ->
                                checklist = checklist.toMutableList().apply {
                                    this[idx] = entry.copy(completed = checked)
                                }
                            }
                        )
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Module 10: 10-Point Fact-Check & Copyright Audit
        item {
            TenPointAuditCard(
                auditItems = auditChecklist,
                onToggleItem = { idx, checked ->
                    auditChecklist = auditChecklist.toMutableList().apply {
                        this[idx] = this[idx].copy(completed = checked)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSaveSubtitlesAndCapCut(project.id, srtText, capcutText, checklist, auditChecklist) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_capcut_srt_button")
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save Subtitles, Plan & Audit")
                Spacer(Modifier.width(8.dp))
                Text("Save Subtitles, Editing Plan & 10-Point Audit")
            }
        }

        // Module 11: Final User Content Approval Gate + 14-Deliverable ZIP Export
        item {
            val unresolvedAudit = auditChecklist.count { !it.completed }
            StudioCard(
                elevated = true,
                accentBorder = if (project.finalApproved) VerifiedEmerald else CautionGold
            ) {
                Text(
                    text = "FINAL APPROVAL & 14-DELIVERABLE PEHLU ZIP",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (project.finalApproved) VerifiedEmerald else CautionGold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Bundles research_dossier.md, short_script_40_60s.txt, long_documentary_script.txt, video_blueprint.md, editing_sheet_7col.csv, titles_thumbnails_chapters.md, voiceover_script_guide.md, subtitles.srt, footage_licenses.csv, capcut_production_plan.md, final_audit_checklist.md, plus main & scene WAVs (when generated).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unresolvedAudit > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Note: $unresolvedAudit audit item(s) above are still unchecked.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DisputedCrimson
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetFinalApproved(project, !project.finalApproved) }
                        .testTag("final_content_approval_row")
                ) {
                    Checkbox(
                        checked = project.finalApproved,
                        onCheckedChange = { onSetFinalApproved(project, it) },
                        modifier = Modifier.testTag("final_content_approval_checkbox")
                    )
                    Text(
                        text = "I have completed the 10-point review — Approve Final Content",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val exportProject = project.copy(
                        subtitlesSrt = srtText,
                        capcutPlan = capcutText,
                        auditChecklist = auditChecklist.map { it.toMap() }
                    )
                    Button(
                        onClick = { onSaveZipToDevice(exportProject) },
                        enabled = project.finalApproved && !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_zip_package_button")
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = "Save ZIP package", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save .ZIP", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onShareZip(exportProject) },
                        enabled = project.finalApproved && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_zip_package_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share ZIP package", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share .ZIP")
                    }
                }
            }
        }
    }
}
