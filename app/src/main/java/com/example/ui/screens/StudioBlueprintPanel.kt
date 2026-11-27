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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.BlueprintSceneItem
import com.example.data.model.ChecklistEntry
import com.example.data.model.StudioProject
import com.example.ui.components.EditorialBadge
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.MetricPill
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
fun VideoBlueprintPanel(
    project: StudioProject?,
    economyMode: Boolean,
    isBusy: Boolean,
    onGenerateBlueprint: (StudioProject) -> Unit,
    onOpenClipFinderForScene: (Int, String, String) -> Unit,
    onGenerateSceneVoiceover: (StudioProject, Int) -> Unit,
    onPreviewAudioFile: (File) -> Unit,
    onPreviewDeviceTts: (String, AppLanguage) -> Unit,
    onStopAudioPreview: () -> Unit
) {
    if (project == null) {
        Column(Modifier.padding(16.dp)) {
            InfoNoticeBanner(
                title = "No Project Selected",
                body = "Approve a topic in Topics or open a saved dossier in Saved to build the PEHLU PROJECT Complete Documentary Video Blueprint."
            )
        }
        return
    }

    val overview = remember(project.projectOverview) { project.parsedOverview() }
    val structure = remember(project.storyStructure) { project.parsedStoryStructure() }
    val scenes = remember(project.blueprintScenes) { project.parsedBlueprintScenes() }
    val footage = remember(project.footageAssets) { project.parsedFootage() }
    val totalEstSec = remember(scenes) { scenes.sumOf { it.estimatedDurationSeconds() } }
    val lang = remember(project.language) { AppLanguage.fromRaw(project.language) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Generate CTA
        item {
            StudioCard(elevated = true, accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorialBadge(
                        text = "MODULE 6 • VIDEO BLUEPRINT",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    EditorialBadge(
                        text = if (economyMode) "Economy Lite" else "Full Model",
                        containerColor = SetupSlateBg,
                        contentColor = SetupSlate
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Editor-Ready Documentary Blueprint",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A complete production plan: Project Overview, Story Structure, Timestamped Scenes, Camera/Archival Direction, BGM/SFX, and Fallback Graphic Ideas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = "Scenes",
                        value = "${scenes.size}",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Est. VO",
                        value = if (totalEstSec > 0) "${totalEstSec / 60}m ${totalEstSec % 60}s" else "—",
                        accent = VerifiedEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Linked Clips",
                        value = "${footage.count { it.sceneNumber > 0 }}/${footage.size}",
                        accent = CautionGold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onGenerateBlueprint(project) },
                    enabled = !isBusy && project.topicApproved,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("generate_video_blueprint_button")
                ) {
                    Icon(Icons.Filled.MovieFilter, contentDescription = "Generate Video Blueprint", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (scenes.isEmpty()) "Generate Complete Video Blueprint" else "Regenerate Video Blueprint",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (!overview.isNotEmpty() && !structure.isNotEmpty() && scenes.isEmpty()) {
            item {
                InfoNoticeBanner(
                    title = "Blueprint Not Built Yet",
                    body = "Tap \"Generate Complete Video Blueprint\" above after running Deep Research and Scripts. Each scene gets direct one-tap Clip Finder and optional Scene WAV controls."
                )
            }
        } else {
            // Section A: Project Overview
            if (overview.isNotEmpty()) {
                item {
                    StudioCard {
                        EditorialBadge(
                            text = "A. PROJECT OVERVIEW",
                            containerColor = VerifiedEmeraldBg,
                            contentColor = VerifiedEmerald
                        )
                        Spacer(Modifier.height(8.dp))
                        BlueprintFieldRow("Documentary Title", overview.documentaryTitle.ifBlank { project.topicTitle })
                        BlueprintFieldRow("Central Idea", overview.centralIdea)
                        BlueprintFieldRow("Unique PEHLU Angle", overview.uniqueAngle.ifBlank { project.angle })
                        BlueprintFieldRow("Target Audience", overview.targetAudience)
                        BlueprintFieldRow("Estimated Duration", overview.estimatedDuration)
                        BlueprintFieldRow("Format / Aspect Ratio", overview.formatAspectRatio)
                        BlueprintFieldRow("Main Objective", overview.mainObjective)
                    }
                }
            }

            // Section B: Story Structure
            if (structure.isNotEmpty()) {
                item {
                    StudioCard {
                        EditorialBadge(
                            text = "B. STORY STRUCTURE",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        BlueprintFieldRow("Opening Hook", structure.openingHook)
                        BlueprintFieldRow("Introduction", structure.introduction)
                        BlueprintFieldRow("Background", structure.background)
                        if (structure.mainChapters.isNotEmpty()) {
                            BlueprintFieldRow(
                                "Main Chapters",
                                structure.mainChapters.mapIndexed { idx, ch -> "${idx + 1}. $ch" }.joinToString("\n")
                            )
                        }
                        BlueprintFieldRow("Key Discoveries", structure.keyDiscoveries)
                        BlueprintFieldRow("Climax", structure.climax)
                        BlueprintFieldRow("Conclusion", structure.conclusion)
                    }
                }
            }

            // Section C & D: Timestamped Scene Breakdown & Visual Direction
            if (scenes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "C & D. SCENE BREAKDOWN & VISUAL DIRECTION (${scenes.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = onStopAudioPreview,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop playback", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop Audio", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                items(scenes, key = { it.sceneNumber }) { scene ->
                    val linkedClipsCount = footage.count { it.sceneNumber == scene.sceneNumber }
                    BlueprintSceneCard(
                        scene = scene,
                        linkedClipsCount = linkedClipsCount,
                        language = lang,
                        isBusy = isBusy,
                        onFindClips = {
                            val kw = scene.searchKeywords.ifBlank { scene.requiredVisuals.take(45) }
                            onOpenClipFinderForScene(
                                scene.sceneNumber,
                                "${scene.startTime}-${scene.endTime}",
                                kw
                            )
                        },
                        onGenerateSceneWav = { onGenerateSceneVoiceover(project, scene.sceneNumber) },
                        onPreviewAudioFile = onPreviewAudioFile,
                        onPreviewDeviceTts = onPreviewDeviceTts
                    )
                }
            }
        }
    }
}

@Composable
private fun BlueprintSceneCard(
    scene: BlueprintSceneItem,
    linkedClipsCount: Int,
    language: AppLanguage,
    isBusy: Boolean,
    onFindClips: () -> Unit,
    onGenerateSceneWav: () -> Unit,
    onPreviewAudioFile: (File) -> Unit,
    onPreviewDeviceTts: (String, AppLanguage) -> Unit
) {
    val wavFile = remember(scene.audioLocalPath) {
        scene.audioLocalPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }

    StudioCard(elevated = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorialBadge(
                text = "SCENE ${scene.sceneNumber} • ${scene.startTime}–${scene.endTime} (~${scene.estimatedDurationSeconds()}s)",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            )
            EditorialBadge(
                text = if (linkedClipsCount > 0) "$linkedClipsCount Clip(s)" else "Needs Visual",
                containerColor = if (linkedClipsCount > 0) VerifiedEmeraldBg else CautionGoldBg,
                contentColor = if (linkedClipsCount > 0) VerifiedEmerald else CautionGold
            )
        }

        if (scene.purpose.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = scene.purpose,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "VOICE-OVER NARRATION",
            style = MaterialTheme.typography.labelSmall,
            color = VerifiedEmerald
        )
        Text(
            text = scene.voiceOverNarration,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (scene.pacingEmotionCue.isNotBlank() || scene.pronunciationNotes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    scene.pacingEmotionCue.takeIf { it.isNotBlank() }?.let { "Delivery: $it" },
                    scene.pronunciationNotes.takeIf { it.isNotBlank() }?.let { "Pronunciation: $it" }
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = CautionGold
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))

        BlueprintFieldRow("Required Visuals", scene.requiredVisuals)
        BlueprintFieldRow("Camera / Animation", scene.cameraAnimation)
        BlueprintFieldRow("Archival Presentation", scene.archivalPresentation)
        BlueprintFieldRow("Visual Continuity", scene.visualContinuity)
        BlueprintFieldRow("On-Screen Text", scene.onScreenText)
        BlueprintFieldRow(
            "BGM / SFX / Transition",
            listOfNotNull(
                scene.bgmMood.takeIf { it.isNotBlank() }?.let { "BGM: $it" },
                scene.sfx.takeIf { it.isNotBlank() }?.let { "SFX: $it" },
                scene.transition.takeIf { it.isNotBlank() }?.let { "Cut: $it" }
            ).joinToString(" | ")
        )
        BlueprintFieldRow("Research Refs", scene.researchRefs)
        BlueprintFieldRow("Fallback Original Graphic", scene.alternativeVisualIdea)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onFindClips,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("blueprint_find_clips_scene_${scene.sceneNumber}")
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Find Clips", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Find Clips")
            }
            OutlinedButton(
                onClick = { onPreviewDeviceTts(scene.voiceOverNarration, language) },
                enabled = scene.voiceOverNarration.isNotBlank(),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Device TTS", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("TTS")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onGenerateSceneWav,
                enabled = !isBusy && scene.voiceOverNarration.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("blueprint_scene_wav_${scene.sceneNumber}")
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = "Generate Scene WAV", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (wavFile != null) "Regen Scene WAV" else "Gen Scene WAV (Opt)")
            }
            if (wavFile != null) {
                OutlinedButton(
                    onClick = { onPreviewAudioFile(wavFile) },
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play Scene WAV", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play (${wavFile.length() / 1024}KB)")
                }
            }
        }
    }
}

@Composable
private fun BlueprintFieldRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Module 9: Structured 7-Column Editing Sheet visual preview inside SRT & Audit tab.
 */
@Composable
fun SevenColumnEditingSheetCard(project: StudioProject) {
    val scenes = remember(project.blueprintScenes) { project.parsedBlueprintScenes() }
    val footage = remember(project.footageAssets) { project.parsedFootage() }

    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "7-COLUMN EDITING SHEET (EXPORTED AS CSV)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            EditorialBadge(
                text = "${scenes.size} Rows",
                containerColor = VerifiedEmeraldBg,
                contentColor = VerifiedEmerald
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Columns: Timestamp | Voice-over | Video Clip/Image | On-screen Text | Music/SFX | Transition | Source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (scenes.isEmpty()) {
            Text(
                text = "Generate the Complete Video Blueprint first to populate timestamped 7-column rows automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            scenes.forEachIndexed { idx, sc ->
                val linked = footage.filter { it.sceneNumber == sc.sceneNumber }
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "${sc.startTime}–${sc.endTime} • Scene ${sc.sceneNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "VO: ${sc.voiceOverNarration.take(120)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Visual: ${
                            if (linked.isNotEmpty()) linked.joinToString("; ") { "${it.title} (${it.license})" }
                            else "${sc.requiredVisuals.take(90)} | Fallback: ${sc.alternativeVisualIdea.take(60)}"
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (linked.isNotEmpty()) VerifiedEmerald else CautionGold
                    )
                    Text(
                        text = "Text: ${sc.onScreenText.ifBlank { "—" }}  |  Audio: ${sc.bgmMood.ifBlank { "—" }} / ${sc.sfx.ifBlank { "—" }}  |  Cut: ${sc.transition.ifBlank { "—" }}  |  Src: ${sc.researchRefs.ifBlank { "—" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (idx < scenes.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * Module 10: 10-Point Final Fact-Check & Copyright Review Card.
 */
@Composable
fun TenPointAuditCard(
    auditItems: List<ChecklistEntry>,
    onToggleItem: (Int, Boolean) -> Unit
) {
    val passedCount = auditItems.count { it.completed }
    val unresolvedCount = auditItems.size - passedCount

    StudioCard(
        elevated = true,
        accentBorder = if (unresolvedCount == 0 && auditItems.isNotEmpty()) VerifiedEmerald else CautionGold
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "10-POINT FACT-CHECK & COPYRIGHT AUDIT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            EditorialBadge(
                text = "$passedCount/${auditItems.size} Passed",
                containerColor = if (unresolvedCount == 0) VerifiedEmeraldBg else DisputedCrimsonBg,
                contentColor = if (unresolvedCount == 0) VerifiedEmerald else DisputedCrimson
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (unresolvedCount == 0) {
                "All 10 pre-production quality, accuracy, and copyright checks are marked verified."
            } else {
                "$unresolvedCount unresolved issue(s) highlighted below — review before locking Final Approval."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (unresolvedCount == 0) VerifiedEmerald else CautionGold
        )
        Spacer(Modifier.height(8.dp))
        auditItems.forEachIndexed { idx, entry ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleItem(idx, !entry.completed) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = entry.completed,
                    onCheckedChange = { checked -> onToggleItem(idx, checked) }
                )
                Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (entry.issueNote.isNotBlank()) {
                        Text(
                            text = entry.issueNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.completed) VerifiedEmerald else DisputedCrimson
                        )
                    }
                }
            }
            if (idx < auditItems.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
