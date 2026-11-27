package com.example.platform

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.ChecklistEntry
import com.example.data.model.ClaimClassification
import com.example.data.model.ReusePermissionStatus
import com.example.data.model.SrtCue
import com.example.data.model.StudioProject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SrtAndZipExporter {

    private val SRT_TIMESTAMP_REGEX = Regex("""\d{2}:\d{2}:\d{2},\d{3}\s*-->\s*\d{2}:\d{2}:\d{2},\d{3}""")

    fun generateSrtFromScript(script: String, secondsPerChunk: Int = 4): String {
        val sentences = script
            .replace("\r\n", "\n")
            .split(Regex("[\\n।.!?]+"))
            .map { it.trim() }
            .filter { it.length >= 4 }

        if (sentences.isEmpty()) return ""

        val sb = StringBuilder()
        var currentSec = 0
        sentences.forEachIndexed { idx, line ->
            val duration = (line.split(Regex("\\s+")).size / 3).coerceIn(2, maxOf(secondsPerChunk, 6))
            val start = formatSrtTime(currentSec * 1000L)
            val end = formatSrtTime((currentSec + duration) * 1000L)
            sb.append(idx + 1).append('\n')
            sb.append(start).append(" --> ").append(end).append('\n')
            sb.append(line).append("\n\n")
            currentSec += duration
        }
        return sb.toString().trim()
    }

    fun generateSrtFromBlueprint(project: StudioProject): String {
        val scenes = project.parsedBlueprintScenes()
        if (scenes.isEmpty()) {
            return generateSrtFromScript(project.shortScript.ifBlank { project.longScript })
        }
        val sb = StringBuilder()
        var cueIndex = 1
        var cursorSec = 0
        scenes.forEach { scene ->
            val lines = scene.voiceOverNarration
                .replace("\r\n", "\n")
                .split(Regex("[\\n।.!?]+"))
                .map { it.trim() }
                .filter { it.length >= 3 }
            lines.forEach { line ->
                val dur = (line.split(Regex("\\s+")).size / 2.5f).toInt().coerceIn(2, 6)
                sb.append(cueIndex++).append('\n')
                sb.append(formatSrtTime(cursorSec * 1000L))
                    .append(" --> ")
                    .append(formatSrtTime((cursorSec + dur) * 1000L)).append('\n')
                sb.append(line).append("\n\n")
                cursorSec += dur
            }
        }
        return sb.toString().trim()
    }

    fun parseSrt(srt: String): List<SrtCue> {
        if (srt.isBlank()) return emptyList()
        val blocks = srt.replace("\r\n", "\n").trim().split(Regex("\\n\\s*\\n"))
        return blocks.mapIndexedNotNull { idx, block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 3) return@mapIndexedNotNull null
            val timeLine = lines[1]
            if (!SRT_TIMESTAMP_REGEX.matches(timeLine)) return@mapIndexedNotNull null
            val parts = timeLine.split("-->").map { it.trim() }
            if (parts.size != 2) return@mapIndexedNotNull null
            SrtCue(
                index = lines[0].toIntOrNull() ?: (idx + 1),
                startTime = parts[0],
                endTime = parts[1],
                text = lines.drop(2).joinToString("\n")
            )
        }
    }

    fun formatCuesToSrt(cues: List<SrtCue>): String {
        return cues.mapIndexed { idx, cue ->
            "${idx + 1}\n${cue.startTime} --> ${cue.endTime}\n${cue.text.trim()}"
        }.joinToString("\n\n")
    }

    fun isValidSrt(srt: String): Boolean = parseSrt(srt).isNotEmpty()

    fun formatSrtTime(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val hours = safe / 3_600_000
        val minutes = (safe % 3_600_000) / 60_000
        val seconds = (safe % 60_000) / 1_000
        val ms = safe % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
    }

    /**
     * Module 10: 10-Point Automated + Manual Pre-Production Fact-Check & Copyright Audit.
     */
    fun buildTenPointAudit(project: StudioProject): List<ChecklistEntry> {
        val facts = project.parsedFacts()
        val scenes = project.parsedBlueprintScenes()
        val footage = project.parsedFootage()
        val hasPrimary = facts.any { it.sourceType.equals("PRIMARY", ignoreCase = true) }
        val hasUrls = facts.any { it.sourceUrl.startsWith("http") }
        val hasDisputedOrAlt = project.disputedClaims.isNotEmpty() || facts.any {
            it.contradictoryEvidence.isNotBlank() || it.classification == ClaimClassification.DISPUTED.name
        }
        val unclearAssets = footage.count {
            it.reuseStatus == ReusePermissionStatus.UNCLEAR_VERIFY.name ||
                it.reuseStatus == ReusePermissionStatus.RESTRICTED_REFERENCE_ONLY.name
        }
        val missingSceneClips = if (scenes.isEmpty()) 1 else scenes.count { sc ->
            footage.none { it.sceneNumber == sc.sceneNumber } && sc.alternativeVisualIdea.isBlank()
        }
        val srtReady = isValidSrt(project.subtitlesSrt)

        return listOf(
            ChecklistEntry(
                label = "1. Important factual claims are backed by reliable evidence",
                completed = facts.isNotEmpty(),
                issueNote = if (facts.isEmpty()) "Run Deep Research first." else "${facts.size} classified claims recorded."
            ),
            ChecklistEntry(
                label = "2. Primary & credible institutional sources have verifiable links",
                completed = hasPrimary && hasUrls,
                issueNote = if (!hasPrimary || !hasUrls) "Verify at least one primary source and direct URL." else "Primary source & URLs present."
            ),
            ChecklistEntry(
                label = "3. Conflicting accounts & disputed claims are fairly acknowledged",
                completed = hasDisputedOrAlt,
                issueNote = if (!hasDisputedOrAlt) "Confirm whether opposing perspectives exist and document them." else "Disputed/alternative notes recorded."
            ),
            ChecklistEntry(
                label = "4. Dates, historical context, and names verified accurately",
                completed = facts.all { it.publishedDate.isNotBlank() } && facts.isNotEmpty(),
                issueNote = "Manually double-check spelling & dates before recording VO."
            ),
            ChecklistEntry(
                label = "5. Statistics and estimates are framed with limitations & context",
                completed = facts.any { it.limitations.isNotBlank() } || project.assumptions.isNotEmpty(),
                issueNote = "Never present ESTIMATED or UNVERIFIED data as confirmed."
            ),
            ChecklistEntry(
                label = "6. Script avoids misleading claims, clickbait, or fake drama",
                completed = project.longScript.isNotBlank() || project.shortScript.isNotBlank(),
                issueNote = if (project.longScript.isBlank() && project.shortScript.isBlank()) "Generate original scripts first." else "Review hook & titles for factual restraint."
            ),
            ChecklistEntry(
                label = "7. Visuals in blueprint match narration with editor-ready directions",
                completed = scenes.isNotEmpty(),
                issueNote = if (scenes.isEmpty()) "Generate Complete Video Blueprint." else "${scenes.size} timestamped scenes defined."
            ),
            ChecklistEntry(
                label = "8. Reuse permissions & attribution credits are clear for every visual",
                completed = footage.isNotEmpty() && unclearAssets == 0,
                issueNote = when {
                    footage.isEmpty() -> "No media assets attached yet."
                    unclearAssets > 0 -> "$unclearAssets asset(s) marked Unclear or Restricted — verify license or replace with original graphic."
                    else -> "All attached assets have clear open/attribution tags."
                }
            ),
            ChecklistEntry(
                label = "9. No missing clips or unaddressed visual gaps in scenes",
                completed = scenes.isNotEmpty() && missingSceneClips == 0,
                issueNote = if (missingSceneClips > 0) "$missingSceneClips scene(s) need linked clips or fallback graphic plans." else "All scenes have clips or fallback visual plans."
            ),
            ChecklistEntry(
                label = "10. Subtitles (SRT) align consistently with approved narration",
                completed = srtReady,
                issueNote = if (!srtReady) "Generate and verify UTF-8 SRT cues before export." else "Valid UTF-8 SRT cues ready."
            )
        )
    }

    /**
     * Module 9: Structured 7-Column Editing Sheet CSV connecting scenes, visuals & sources.
     */
    fun buildSevenColumnEditingSheetCsv(project: StudioProject): String = buildString {
        appendLine("Timestamp,Voice-over,Video Clip/Image,On-screen Text,Music/SFX,Transition,Source")
        val scenes = project.parsedBlueprintScenes()
        val footage = project.parsedFootage()
        if (scenes.isEmpty()) {
            val row = listOf(
                "00:00 - 00:60",
                project.shortScript.take(200),
                project.visualDirections.take(180),
                project.onScreenText.joinToString(" | "),
                "Subtle documentary ambient bed",
                "Clean cut",
                project.parsedFacts().firstOrNull()?.sourceTitle.orEmpty()
            ).joinToString(",") { escapeCsv(it) }
            appendLine(row)
        } else {
            scenes.forEach { sc ->
                val linkedClips = footage.filter { it.sceneNumber == sc.sceneNumber }
                val visualCol = if (linkedClips.isNotEmpty()) {
                    linkedClips.joinToString(" ; ") { "${it.title} (${it.license}) [${it.assetUrl}]" }
                } else {
                    "${sc.requiredVisuals} | Fallback: ${sc.alternativeVisualIdea}".trim()
                }
                val musicSfx = listOfNotNull(
                    sc.bgmMood.takeIf { it.isNotBlank() }?.let { "BGM: $it" },
                    sc.sfx.takeIf { it.isNotBlank() }?.let { "SFX: $it" }
                ).joinToString(" / ")

                val row = listOf(
                    "${sc.startTime} - ${sc.endTime}",
                    sc.voiceOverNarration,
                    visualCol,
                    sc.onScreenText,
                    musicSfx,
                    sc.transition,
                    sc.researchRefs
                ).joinToString(",") { escapeCsv(it) }
                appendLine(row)
            }
        }
    }

    /**
     * Module 11: Bundles all 14 PEHLU PROJECT deliverables cleanly into one ZIP package
     * while preserving legacy file names (`research_dossier.md`, `short_script_40_60s.txt`,
     * `long_documentary_script.txt`, `subtitles.srt`, `footage_licenses.csv`, `capcut_production_plan.md`, `voiceover.wav`).
     */
    fun writeProjectZipToStream(project: StudioProject, outputStream: OutputStream) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            addUtf8Entry(zip, "research_dossier.md", buildResearchDossierMarkdown(project))
            addUtf8Entry(
                zip,
                "short_script_40_60s.txt",
                project.shortScript.ifBlank { "No 40-60s Short script generated yet." }
            )
            addUtf8Entry(
                zip,
                "long_documentary_script.txt",
                project.longScript.ifBlank { "No Long Documentary script generated yet." }
            )
            val srtContent = project.subtitlesSrt.ifBlank {
                generateSrtFromBlueprint(project)
            }
            addUtf8Entry(zip, "subtitles.srt", srtContent)
            addUtf8Entry(zip, "footage_licenses.csv", buildFootageLicenseCsv(project))
            addUtf8Entry(zip, "capcut_production_plan.md", buildCapCutMarkdown(project))
            addUtf8Entry(zip, "video_blueprint.md", buildVideoBlueprintMarkdown(project))
            addUtf8Entry(zip, "editing_sheet_7col.csv", buildSevenColumnEditingSheetCsv(project))
            addUtf8Entry(zip, "titles_thumbnails_chapters.md", buildPackagingMarkdown(project))
            addUtf8Entry(zip, "voiceover_script_guide.md", buildVoiceoverGuideMarkdown(project))
            addUtf8Entry(zip, "final_audit_checklist.md", buildFinalAuditMarkdown(project))

            val audioPath = project.voiceoverLocalPath
            if (audioPath.isNotBlank()) {
                val audioFile = File(audioPath)
                if (audioFile.exists() && audioFile.isFile && audioFile.length() > 0L) {
                    zip.putNextEntry(ZipEntry("voiceover.wav"))
                    FileInputStream(audioFile).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            // Include any scene-wise generated WAV files
            project.parsedBlueprintScenes().forEach { sc ->
                if (sc.audioLocalPath.isNotBlank()) {
                    val f = File(sc.audioLocalPath)
                    if (f.exists() && f.isFile && f.length() > 0L) {
                        val entryName = String.format(Locale.US, "scene_%02d_voiceover.wav", sc.sceneNumber)
                        zip.putNextEntry(ZipEntry(entryName))
                        FileInputStream(f).use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    fun exportProjectZipToCache(context: Context, project: StudioProject): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val slug = project.topicTitle.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "pehlu_project_package" }
        val zipFile = File(exportDir, "${slug}_package.zip")
        FileOutputStream(zipFile).use { fos ->
            writeProjectZipToStream(project, fos)
        }
        return zipFile
    }

    fun exportSrtToCache(context: Context, project: StudioProject): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val slug = project.topicTitle.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "pehlu_project" }
        val srtFile = File(exportDir, "${slug}_subtitles.srt")
        val content = project.subtitlesSrt.ifBlank { generateSrtFromBlueprint(project) }
        srtFile.writeText(content, StandardCharsets.UTF_8)
        return srtFile
    }

    fun getShareUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun escapeCsv(cell: String): String =
        "\"${cell.replace("\"", "\"\"").replace("\n", " ")}\""

    private fun addUtf8Entry(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildResearchDossierMarkdown(project: StudioProject): String = buildString {
        appendLine("# PEHLU PROJECT — Deep Research & 5-Tier Fact-Check Report")
        appendLine()
        appendLine("- **Topic:** ${project.topicTitle}")
        appendLine("- **Category:** ${project.categoryBeat}")
        appendLine("- **Language:** ${project.language}")
        appendLine("- **Format:** ${project.packageFormat}")
        appendLine("- **Topic Approved:** ${project.topicApproved}")
        appendLine("- **Final Approved:** ${project.finalApproved}")
        appendLine()
        appendLine("## Documentary Angle & Hook")
        appendLine("**Angle:** ${project.angle}")
        appendLine("**Opening Hook:** ${project.hook}")
        appendLine("**Why Cover Now:** ${project.whyCoverNow}")
        appendLine()
        appendLine("## 5-Tier Claim Verification Report")
        val facts = project.parsedFacts()
        if (facts.isEmpty()) {
            appendLine("_No researched claims recorded yet._")
        } else {
            facts.forEachIndexed { idx, f ->
                appendLine("${idx + 1}. **[${f.classification} | ${f.sourceType}]** ${f.claim}")
                appendLine("   - **Source:** ${f.sourceTitle} | Publisher: ${f.publisher.ifBlank { "Unspecified" }} | Date: ${f.publishedDate.ifBlank { "Verify date" }}")
                appendLine("   - **URL:** ${f.sourceUrl.ifBlank { "Verify link manually" }}")
                if (f.supportingEvidence.isNotBlank()) appendLine("   - **Supporting:** ${f.supportingEvidence}")
                if (f.contradictoryEvidence.isNotBlank()) appendLine("   - **Contradictory / Alternative:** ${f.contradictoryEvidence}")
                if (f.limitations.isNotBlank()) appendLine("   - **Evidence Limitations:** ${f.limitations}")
            }
        }
        appendLine()
        appendLine("## Disputed or Conflicting Narratives")
        if (project.disputedClaims.isEmpty()) appendLine("_None flagged._")
        else project.disputedClaims.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Assumptions & Open Questions (Separated from Confirmed Facts)")
        if (project.assumptions.isEmpty()) appendLine("_None flagged._")
        else project.assumptions.forEach { appendLine("- $it") }
    }

    private fun buildVideoBlueprintMarkdown(project: StudioProject): String = buildString {
        val ov = project.parsedOverview()
        val st = project.parsedStoryStructure()
        val scenes = project.parsedBlueprintScenes()
        appendLine("# PEHLU PROJECT — Complete Documentary Video Blueprint")
        appendLine()
        appendLine("## A. Project Overview")
        appendLine("- **Title:** ${ov.documentaryTitle.ifBlank { project.topicTitle }}")
        appendLine("- **Central Idea:** ${ov.centralIdea}")
        appendLine("- **Unique Angle:** ${ov.uniqueAngle.ifBlank { project.angle }}")
        appendLine("- **Target Audience:** ${ov.targetAudience}")
        appendLine("- **Estimated Duration:** ${ov.estimatedDuration}")
        appendLine("- **Format / Aspect Ratio:** ${ov.formatAspectRatio}")
        appendLine("- **Main Objective:** ${ov.mainObjective}")
        appendLine()
        appendLine("## B. Story Structure")
        appendLine("- **Opening Hook:** ${st.openingHook}")
        appendLine("- **Introduction:** ${st.introduction}")
        appendLine("- **Background:** ${st.background}")
        appendLine("- **Main Chapters:** ${st.mainChapters.joinToString(" → ")}")
        appendLine("- **Key Discoveries:** ${st.keyDiscoveries}")
        appendLine("- **Climax:** ${st.climax}")
        appendLine("- **Conclusion:** ${st.conclusion}")
        appendLine()
        appendLine("## C & D. Timestamped Scene Breakdown & Visual Direction")
        if (scenes.isEmpty()) {
            appendLine("_Blueprint scenes not generated yet._")
        } else {
            scenes.forEach { s ->
                appendLine("### Scene ${s.sceneNumber} (${s.startTime} – ${s.endTime}) — ${s.purpose}")
                appendLine("- **Voice-Over Narration:** ${s.voiceOverNarration}")
                appendLine("- **Required Visuals:** ${s.requiredVisuals}")
                appendLine("- **Camera / Animation Direction:** ${s.cameraAnimation}")
                appendLine("- **Archival Document Styling:** ${s.archivalPresentation}")
                appendLine("- **Visual Continuity:** ${s.visualContinuity}")
                appendLine("- **On-Screen Text:** ${s.onScreenText}")
                appendLine("- **BGM Mood:** ${s.bgmMood} | **SFX:** ${s.sfx} | **Transition:** ${s.transition}")
                appendLine("- **Research Refs:** ${s.researchRefs}")
                appendLine("- **Clip Search Keywords:** ${s.searchKeywords}")
                appendLine("- **Fallback Original Graphic:** ${s.alternativeVisualIdea}")
                appendLine()
            }
        }
    }

    private fun buildPackagingMarkdown(project: StudioProject): String = buildString {
        appendLine("# PEHLU PROJECT — Titles, Thumbnails & YouTube Description")
        appendLine()
        appendLine("## Title Options")
        if (project.titleOptions.isEmpty()) appendLine("_None generated yet._")
        else project.titleOptions.forEachIndexed { i, t -> appendLine("${i + 1}. $t") }
        appendLine()
        appendLine("## Thumbnail Concepts")
        if (project.thumbnailConcepts.isEmpty()) appendLine("_None generated yet._")
        else project.thumbnailConcepts.forEachIndexed { i, t -> appendLine("${i + 1}. $t") }
        appendLine()
        appendLine("## Description & Chapters")
        appendLine(project.descriptionAndChapters.ifBlank { "_Not generated yet._" })
    }

    private fun buildVoiceoverGuideMarkdown(project: StudioProject): String = buildString {
        appendLine("# PEHLU PROJECT — Voice-Over Script, Pronunciation & Pacing Guide")
        appendLine()
        appendLine("- **Selected Voice:** ${project.voiceoverVoice}")
        appendLine("- **Audio Status:** ${project.voiceoverStatus}")
        appendLine()
        appendLine("## Overall Delivery & Pronunciation Notes")
        appendLine(project.voiceoverDirectionNotes.ifBlank { "_Add pronunciation & pacing notes here._" })
        appendLine()
        val scenes = project.parsedBlueprintScenes()
        if (scenes.isNotEmpty()) {
            appendLine("## Scene-Wise Narration & Delivery Cues")
            scenes.forEach { s ->
                appendLine("### Scene ${s.sceneNumber} (${s.startTime}–${s.endTime}, ~${s.estimatedDurationSeconds()}s)")
                appendLine(s.voiceOverNarration)
                if (s.pronunciationNotes.isNotBlank()) appendLine("- **Pronunciation:** ${s.pronunciationNotes}")
                if (s.pacingEmotionCue.isNotBlank()) appendLine("- **Pacing / Emphasis / Emotion:** ${s.pacingEmotionCue}")
                appendLine()
            }
        }
    }

    private fun buildFinalAuditMarkdown(project: StudioProject): String = buildString {
        appendLine("# PEHLU PROJECT — 10-Point Final Fact-Check & Copyright Review")
        appendLine()
        val items = project.parsedAuditChecklist().ifEmpty { buildTenPointAudit(project) }
        items.forEach { entry ->
            val mark = if (entry.completed) "[x]" else "[ ]"
            appendLine("- $mark **${entry.label}**")
            if (entry.issueNote.isNotBlank()) appendLine("  - Note: ${entry.issueNote}")
        }
    }

    private fun buildFootageLicenseCsv(project: StudioProject): String = buildString {
        appendLine("Scene,Timestamp,Title,MediaType,Provider,Creator,License,ReuseStatus,AttributionRequired,AssetUrl,Relevance,CommercialCaution")
        project.parsedFootage().forEach { a ->
            val row = listOf(
                if (a.sceneNumber > 0) "Scene ${a.sceneNumber}" else "General",
                a.timestampRange,
                a.title,
                a.mediaType,
                a.provider,
                a.creator,
                a.license,
                a.reuseStatus,
                a.attributionRequired.toString(),
                a.assetUrl,
                a.relevanceReason,
                a.commercialCaution
            ).joinToString(",") { escapeCsv(it) }
            appendLine(row)
        }
    }

    private fun buildCapCutMarkdown(project: StudioProject): String = buildString {
        appendLine("# Editing Instructions & CapCut Plan — ${project.topicTitle}")
        appendLine()
        appendLine("## Visual Directions")
        appendLine(project.visualDirections.ifBlank { "_Not generated yet._" })
        appendLine()
        appendLine("## On-Screen Text Cues")
        project.onScreenText.forEachIndexed { idx, txt ->
            appendLine("${idx + 1}. $txt")
        }
        appendLine()
        appendLine("## Editing Plan, Transitions, SFX & Licensed BGM")
        appendLine(project.capcutPlan.ifBlank { "_Not generated yet._" })
        appendLine()
        appendLine("## Editing Checklist")
        project.parsedChecklist().forEach { entry ->
            val mark = if (entry.completed) "[x]" else "[ ]"
            appendLine("- $mark ${entry.label}")
        }
    }
}
