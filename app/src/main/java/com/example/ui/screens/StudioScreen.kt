package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.ChecklistEntry
import com.example.data.model.ClaimClassification
import com.example.data.model.FootageAssetItem
import com.example.data.model.PackageFormat
import com.example.data.model.ProjectStage
import com.example.data.model.StudioProject
import com.example.data.model.TopicCandidate
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
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SetupSlate
import com.example.ui.theme.SetupSlateBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.viewmodel.StudioSubTab
import java.io.File

@Composable
fun StudioScreen(
    activeSubTab: StudioSubTab,
    projects: List<StudioProject>,
    activeProject: StudioProject?,
    discoveredTopics: List<TopicCandidate>,
    stockResult: StockMediaSearchResult?,
    archivalPortals: List<ArchivalPortalLink>,
    clipSearchQuerySeed: String,
    clipTargetSceneNumber: Int,
    clipTargetTimestamp: String,
    selectedLanguage: AppLanguage,
    defaultPackageFormat: PackageFormat,
    economyMode: Boolean,
    backendProxyUrl: String,
    selectedVoice: String,
    isBusy: Boolean,
    busyLabel: String,
    statusBanner: String?,
    errorMessage: String?,
    onSelectSubTab: (StudioSubTab) -> Unit,
    onSelectProject: (String, StudioSubTab?) -> Unit,
    onStartTodaysResearch: (String, AppLanguage, String, Boolean) -> Unit,
    onApproveCandidate: (TopicCandidate, AppLanguage, PackageFormat) -> Unit,
    onApproveProjectTopic: (StudioProject) -> Unit,
    onRunFactCheck: (StudioProject) -> Unit,
    onGenerateScripts: (StudioProject) -> Unit,
    onGenerateBlueprint: (StudioProject) -> Unit,
    onSaveScripts: (String, String, String, String) -> Unit,
    onOpenClipFinderForScene: (Int, String, String) -> Unit,
    onSearchStock: (String, String, String) -> Unit,
    onAttachFootage: (StudioProject, FootageAssetItem) -> Unit,
    onSelectVoice: (String) -> Unit,
    onGenerateVoiceover: (StudioProject, Boolean) -> Unit,
    onGenerateSceneVoiceover: (StudioProject, Int) -> Unit,
    onPreviewAudioFile: (File) -> Unit,
    onPreviewDeviceTts: (String, AppLanguage) -> Unit,
    onStopAudioPreview: () -> Unit,
    onSaveSubtitlesAndCapCut: (String, String, String, List<ChecklistEntry>, List<ChecklistEntry>) -> Unit,
    onSetFinalApproved: (StudioProject, Boolean) -> Unit,
    onExportZip: (StudioProject, (File) -> Unit) -> Unit,
    onDeleteProject: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearAlerts: () -> Unit
) {
    val context = LocalContext.current
    var pendingSaveZipProject by remember { mutableStateOf<StudioProject?>(null) }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val project = pendingSaveZipProject
        pendingSaveZipProject = null
        if (uri != null && project != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    SrtAndZipExporter.writeProjectZipToStream(project, out)
                }
            } catch (_: Exception) {
            }
        }
    }

    val shareFile: (File, String) -> Unit = { file, mime ->
        val uri = SrtAndZipExporter.getShareUri(context, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export PEHLU PROJECT Deliverable"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Top Studio Header + 7-Stage Pipeline Sub-tabs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PEHLU PROJECT × SHUBHAM FILES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = activeProject?.topicTitle ?: "Documentary Production Desk",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                if (activeProject != null) {
                    EditorialBadge(
                        text = ProjectStage.fromRaw(activeProject.stage).label,
                        containerColor = if (activeProject.finalApproved) VerifiedEmeraldBg else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (activeProject.finalApproved) VerifiedEmerald else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StudioSubTab.entries) { tab ->
                    FilterChip(
                        selected = activeSubTab == tab,
                        onClick = { onSelectSubTab(tab) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.testTag("studio_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        if (isBusy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = busyLabel.ifBlank { "Working..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            ErrorBanner(
                message = errorMessage,
                onDismiss = onClearAlerts,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else if (!statusBanner.isNullOrBlank()) {
            InfoNoticeBanner(
                title = "Studio Update",
                body = statusBanner,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        when (activeSubTab) {
            StudioSubTab.DISCOVER -> DiscoverTopicsPanel(
                topics = discoveredTopics,
                selectedLanguage = selectedLanguage,
                defaultPackageFormat = defaultPackageFormat,
                isBusy = isBusy,
                onStartResearch = onStartTodaysResearch,
                onApproveCandidate = onApproveCandidate
            )
            StudioSubTab.RESEARCH -> FactCheckPanel(
                project = activeProject,
                isBusy = isBusy,
                onApproveTopic = onApproveProjectTopic,
                onRunFactCheck = onRunFactCheck,
                onGenerateScripts = onGenerateScripts,
                onOpenUrl = onOpenUrl
            )
            StudioSubTab.SCRIPTS -> DualScriptsPanel(
                project = activeProject,
                isBusy = isBusy,
                onGenerateScripts = onGenerateScripts,
                onSaveScripts = onSaveScripts,
                onBuildBlueprint = onGenerateBlueprint
            )
            StudioSubTab.BLUEPRINT -> VideoBlueprintPanel(
                project = activeProject,
                economyMode = economyMode,
                isBusy = isBusy,
                onGenerateBlueprint = onGenerateBlueprint,
                onOpenClipFinderForScene = onOpenClipFinderForScene,
                onGenerateSceneVoiceover = onGenerateSceneVoiceover,
                onPreviewAudioFile = onPreviewAudioFile,
                onPreviewDeviceTts = onPreviewDeviceTts,
                onStopAudioPreview = onStopAudioPreview
            )
            StudioSubTab.MEDIA -> FootageAndVoiceoverPanel(
                project = activeProject,
                stockResult = stockResult,
                archivalPortals = archivalPortals,
                clipSearchQuerySeed = clipSearchQuerySeed,
                clipTargetSceneNumber = clipTargetSceneNumber,
                clipTargetTimestamp = clipTargetTimestamp,
                backendProxyUrl = backendProxyUrl,
                selectedVoice = selectedVoice,
                isBusy = isBusy,
                onSearchStock = onSearchStock,
                onAttachFootage = onAttachFootage,
                onOpenUrl = onOpenUrl,
                onSelectVoice = onSelectVoice,
                onGenerateVoiceover = onGenerateVoiceover,
                onGenerateSceneVoiceover = onGenerateSceneVoiceover,
                onPreviewAudioFile = onPreviewAudioFile,
                onPreviewDeviceTts = onPreviewDeviceTts,
                onStopAudioPreview = onStopAudioPreview,
                onShareAudioFile = { file -> shareFile(file, "audio/wav") }
            )
            StudioSubTab.SUBTITLES -> SubtitlesCapCutExportPanel(
                project = activeProject,
                isBusy = isBusy,
                onSaveSubtitlesAndCapCut = onSaveSubtitlesAndCapCut,
                onSetFinalApproved = onSetFinalApproved,
                onExportSrt = { proj ->
                    val srtFile = SrtAndZipExporter.exportSrtToCache(context, proj)
                    shareFile(srtFile, "application/x-subrip")
                },
                onShareZip = { proj ->
                    onExportZip(proj) { zipFile -> shareFile(zipFile, "application/zip") }
                },
                onSaveZipToDevice = { proj ->
                    if (proj.finalApproved) {
                        pendingSaveZipProject = proj
                        saveZipLauncher.launch("pehlu_project_package.zip")
                    }
                }
            )
            StudioSubTab.HISTORY -> ResearchHistoryPanel(
                projects = projects,
                activeProjectId = activeProject?.id,
                onOpenProject = { id -> onSelectProject(id, StudioSubTab.RESEARCH) },
                onDeleteProject = onDeleteProject
            )
        }
    }
}

@Composable
private fun DiscoverTopicsPanel(
    topics: List<TopicCandidate>,
    selectedLanguage: AppLanguage,
    defaultPackageFormat: PackageFormat,
    isBusy: Boolean,
    onStartResearch: (String, AppLanguage, String, Boolean) -> Unit,
    onApproveCandidate: (TopicCandidate, AppLanguage, PackageFormat) -> Unit
) {
    val beats = listOf(
        "History",
        "Science & Tech",
        "Psychology",
        "Society",
        "Real Events",
        "Mysteries",
        "Unexplored Perspectives",
        "Current Affairs"
    )
    var selectedBeat by remember { mutableStateOf("History") }
    var customKeyword by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StudioCard(elevated = true, accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) {
                Text(
                    text = "PEHLU PROJECT TOPIC DISCOVERY • ${defaultPackageFormat.label.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Discover Verifiable Documentary Stories",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(beats) { beat ->
                        FilterChip(
                            selected = selectedBeat == beat,
                            onClick = { selectedBeat = beat },
                            label = { Text(beat) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customKeyword,
                    onValueChange = { customKeyword = it },
                    label = { Text("Optional focus (e.g., ISRO, Indus Valley, Behavioral Economics)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_topic_keyword_input")
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onStartResearch(selectedBeat, selectedLanguage, customKeyword, false) },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("studio_start_todays_research_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start Today's Research")
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cta_start_todays_research), fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (topics.isEmpty()) {
            item {
                InfoNoticeBanner(
                    title = "Evidence-First PEHLU PROJECT Workflow",
                    body = "Topics are grounded in reliable research pathways (no fabricated trending stats). Approve one topic to unlock 5-Tier Deep Research, Original Scripts, and the Complete Video Blueprint."
                )
            }
        } else {
            items(topics) { topic ->
                StudioCard {
                    EditorialBadge(
                        text = topic.category,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Hook: ${topic.hook}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Angle: ${topic.angle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Why it matters: ${topic.whyCoverNow}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (topic.researchPathway.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Research Pathway: ${topic.researchPathway}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CautionGold
                        )
                    }
                    if (topic.sourceHints.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Sources to verify: ${topic.sourceHints.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedEmerald
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onApproveCandidate(topic, selectedLanguage, defaultPackageFormat) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("approve_topic_button")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Approve Topic")
                        Spacer(Modifier.width(8.dp))
                        Text("Approve Topic & Open Deep Research")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { onStartResearch(selectedBeat, selectedLanguage, customKeyword, true) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("more_topic_suggestions_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "More Suggestions", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("More Suggestions (Fresh Angles)")
                }
            }
        }
    }
}

@Composable
private fun FactCheckPanel(
    project: StudioProject?,
    isBusy: Boolean,
    onApproveTopic: (StudioProject) -> Unit,
    onRunFactCheck: (StudioProject) -> Unit,
    onGenerateScripts: (StudioProject) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    if (project == null) {
        Column(Modifier.padding(16.dp)) {
            InfoNoticeBanner(
                title = "No Active Project Selected",
                body = "Discover and approve a topic first, or open a saved project from the Saved tab."
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StudioCard(elevated = true) {
                Text(
                    text = project.topicTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = project.angle.ifBlank { project.whyCoverNow },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Source Hierarchy: Primary Records → Official Institutions → Peer-Reviewed Academic → Books/Archives → Investigative Journalism.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerifiedEmerald
                )
                Spacer(Modifier.height(12.dp))
                if (!project.topicApproved) {
                    Button(
                        onClick = { onApproveTopic(project) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Approve Topic to Unlock Deep Research")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onRunFactCheck(project) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("run_fact_check_button")
                        ) {
                            Icon(Icons.Filled.FactCheck, contentDescription = "Run Deep Research", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("5-Tier Research")
                        }
                        Button(
                            onClick = { onGenerateScripts(project) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("generate_dual_scripts_button")
                        ) {
                            Text("Write Scripts")
                        }
                    }
                }
            }
        }

        // 5-Tier Claim Classification Report
        item {
            StudioCard {
                val facts = project.parsedFacts()
                EditorialBadge(
                    text = "5-TIER VERIFIED RESEARCH (${facts.size} CLAIMS)",
                    containerColor = VerifiedEmeraldBg,
                    contentColor = VerifiedEmerald
                )
                Spacer(Modifier.height(8.dp))
                if (facts.isEmpty()) {
                    Text(
                        text = "Tap 5-Tier Research above to verify claims across Primary, Official, and Academic sources with live web citations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    facts.forEachIndexed { idx, fact ->
                        val tier = ClaimClassification.fromRaw(fact.classification)
                        val (tierBg, tierFg) = when (tier) {
                            ClaimClassification.CONFIRMED -> VerifiedEmeraldBg to VerifiedEmerald
                            ClaimClassification.SUPPORTED -> VerifiedEmeraldBg to VerifiedEmerald
                            ClaimClassification.ESTIMATED -> CautionGoldBg to CautionGold
                            ClaimClassification.UNVERIFIED -> SetupSlateBg to SetupSlate
                            ClaimClassification.DISPUTED -> DisputedCrimsonBg to DisputedCrimson
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EditorialBadge(text = tier.label, containerColor = tierBg, contentColor = tierFg)
                                EditorialBadge(
                                    text = fact.sourceType,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = fact.claim,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Source: ${fact.sourceTitle} • ${fact.publisher.ifBlank { "Publisher unspecified" }} (${fact.publishedDate.ifBlank { "Date TBD" }})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (fact.supportingEvidence.isNotBlank()) {
                                Text(
                                    text = "Supporting: ${fact.supportingEvidence}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VerifiedEmerald
                                )
                            }
                            if (fact.contradictoryEvidence.isNotBlank()) {
                                Text(
                                    text = "Contradictory / Alt View: ${fact.contradictoryEvidence}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DisputedCrimson
                                )
                            }
                            if (fact.limitations.isNotBlank()) {
                                Text(
                                    text = "Limitations: ${fact.limitations}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CautionGold
                                )
                            }
                            if (fact.sourceUrl.startsWith("http")) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { onOpenUrl(fact.sourceUrl) }
                                        .padding(top = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Link,
                                        contentDescription = "Open citation",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = fact.sourceUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (idx < facts.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        // Disputed / Unverified Claims
        item {
            StudioCard {
                EditorialBadge(
                    text = "DISPUTED / CONFLICTING NARRATIVES (${project.disputedClaims.size})",
                    containerColor = DisputedCrimsonBg,
                    contentColor = DisputedCrimson
                )
                Spacer(Modifier.height(8.dp))
                if (project.disputedClaims.isEmpty()) {
                    Text(
                        text = "No disputed or unverified claims flagged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    project.disputedClaims.forEach { claim ->
                        Text(
                            text = "⚠ $claim",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Assumptions Separated from Facts
        item {
            StudioCard {
                EditorialBadge(
                    text = "ASSUMPTIONS & OPEN QUESTIONS (${project.assumptions.size})",
                    containerColor = CautionGoldBg,
                    contentColor = CautionGold
                )
                Spacer(Modifier.height(8.dp))
                if (project.assumptions.isEmpty()) {
                    Text(
                        text = "No editorial assumptions listed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    project.assumptions.forEach { item ->
                        Text(
                            text = "? $item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchHistoryPanel(
    projects: List<StudioProject>,
    activeProjectId: String?,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (projects.isEmpty()) {
            item {
                InfoNoticeBanner(
                    title = "PEHLU PROJECT Cloud Archive Empty",
                    body = "All approved PEHLU PROJECT dossiers, 5-tier research, blueprints, scripts, subtitles, and 10-point audits automatically sync here via Firestore."
                )
            }
        } else {
            items(projects, key = { it.id }) { proj ->
                val isSelected = proj.id == activeProjectId
                StudioCard(
                    elevated = isSelected,
                    accentBorder = if (isSelected) MaterialTheme.colorScheme.primary else null,
                    modifier = Modifier.clickable { onOpenProject(proj.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EditorialBadge(
                                    text = proj.categoryBeat,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                                EditorialBadge(
                                    text = ProjectStage.fromRaw(proj.stage).label,
                                    containerColor = VerifiedEmeraldBg,
                                    contentColor = VerifiedEmerald
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = proj.topicTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${proj.language} • ${proj.parsedFacts().size} claims • ${proj.parsedBlueprintScenes().size} scenes • ${proj.parsedFootage().size} clips",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeleteProject(proj.id) },
                            modifier = Modifier.testTag("delete_project_${proj.id}")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete project",
                                tint = DisputedCrimson
                            )
                        }
                    }
                }
            }
        }
    }
}
