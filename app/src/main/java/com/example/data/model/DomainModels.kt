package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage(val label: String, val localeTag: String, val promptDirective: String) {
    HINGLISH(
        label = "Hinglish",
        localeTag = "hi-IN",
        promptDirective = "Respond in natural Hinglish (conversational Hindi written in Latin script mixed naturally with everyday English terms)."
    ),
    HINDI(
        label = "Hindi (हिंदी)",
        localeTag = "hi-IN",
        promptDirective = "Respond in clear, natural Hindi written in Devanagari script."
    ),
    ENGLISH(
        label = "English",
        localeTag = "en-IN",
        promptDirective = "Respond in crisp, clear, journalistic English."
    );

    companion object {
        fun fromRaw(raw: String?): AppLanguage =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HINGLISH
    }
}

enum class PackageFormat(val label: String, val subtitle: String) {
    DUAL("Dual Package", "40-60s Short + Long Documentary"),
    SHORT("Shorts / Reels", "40-60s Vertical Script"),
    LONG("Long Documentary", "8-12 min YouTube Deep Dive");

    companion object {
        fun fromRaw(raw: String?): PackageFormat =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DUAL
    }
}

enum class ProjectStage(val label: String) {
    DISCOVERED("Topic Discovered"),
    TOPIC_APPROVED("Topic Approved"),
    IN_PRODUCTION("In Production"),
    FINAL_APPROVED("Final Approved"),
    EXPORTED("Exported");

    companion object {
        fun fromRaw(raw: String?): ProjectStage =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DISCOVERED
    }
}

enum class ClaimClassification(val label: String, val description: String) {
    CONFIRMED("CONFIRMED", "Strong reliable independent evidence"),
    SUPPORTED("SUPPORTED", "Credible evidence, minor limitations remain"),
    DISPUTED("DISPUTED", "Reliable sources disagree"),
    ESTIMATED("ESTIMATED", "Based on estimates or incomplete data"),
    UNVERIFIED("UNVERIFIED", "Insufficient reliable evidence");

    companion object {
        fun fromRaw(raw: String?): ClaimClassification =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNVERIFIED
    }
}

enum class ReusePermissionStatus(val label: String) {
    CONFIRMED_OPEN("Confirmed Open / PD"),
    ATTRIBUTION_REQUIRED("Attribution Required"),
    UNCLEAR_VERIFY("Unclear — Verify Manually"),
    RESTRICTED_REFERENCE_ONLY("Restricted / Reference Only");

    companion object {
        fun fromRaw(raw: String?): ReusePermissionStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNCLEAR_VERIFY
    }
}

@Keep
@IgnoreExtraProperties
data class UserPreferenceProfile(
    @DocumentId val id: String = "profile",
    val userId: String = "",
    val defaultLanguage: String = AppLanguage.HINGLISH.name,
    val defaultPackageFormat: String = PackageFormat.DUAL.name,
    val memoryNotes: List<String> = emptyList(),
    val backendProxyUrl: String = "",
    val automationEnabled: Boolean = false,
    val automationHour: Int = 8,
    val automationMinute: Int = 0,
    val automationBeat: String = "History, Science & Current Affairs",
    val economyModeEnabled: Boolean = true,
    val chatWebSearchDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CitationItem(
    val title: String = "",
    val url: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf("title" to title, "url" to url)
}

@Keep
@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val threadId: String = "default_thread",
    val role: String = "user",
    val content: String = "",
    val language: String = AppLanguage.HINGLISH.name,
    val citations: List<Map<String, Any>> = emptyList(),
    val certaintyTag: String = "USER",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedCitations(): List<CitationItem> = citations.mapNotNull { m ->
        val title = m["title"] as? String ?: return@mapNotNull null
        val url = m["url"] as? String ?: ""
        CitationItem(title = title, url = url)
    }
}

@Keep
@IgnoreExtraProperties
data class VerifiedFactItem(
    val claim: String = "",
    val classification: String = ClaimClassification.CONFIRMED.name,
    val sourceType: String = "SECONDARY", // PRIMARY | SECONDARY
    val sourceTitle: String = "",
    val publisher: String = "",
    val sourceUrl: String = "",
    val publishedDate: String = "",
    val supportingEvidence: String = "",
    val contradictoryEvidence: String = "",
    val limitations: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "claim" to claim,
        "classification" to ClaimClassification.fromRaw(classification).name,
        "sourceType" to if (sourceType.equals("PRIMARY", ignoreCase = true)) "PRIMARY" else "SECONDARY",
        "sourceTitle" to sourceTitle,
        "publisher" to publisher,
        "sourceUrl" to sourceUrl,
        "publishedDate" to publishedDate,
        "supportingEvidence" to supportingEvidence,
        "contradictoryEvidence" to contradictoryEvidence,
        "limitations" to limitations
    )

    companion object {
        fun fromMap(m: Map<String, Any>): VerifiedFactItem = VerifiedFactItem(
            claim = m["claim"] as? String ?: "",
            classification = ClaimClassification.fromRaw(m["classification"] as? String ?: "CONFIRMED").name,
            sourceType = (m["sourceType"] as? String)?.ifBlank { "SECONDARY" } ?: "SECONDARY",
            sourceTitle = m["sourceTitle"] as? String ?: "",
            publisher = m["publisher"] as? String ?: "",
            sourceUrl = m["sourceUrl"] as? String ?: "",
            publishedDate = m["publishedDate"] as? String ?: "",
            supportingEvidence = m["supportingEvidence"] as? String ?: "",
            contradictoryEvidence = m["contradictoryEvidence"] as? String ?: "",
            limitations = m["limitations"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class FootageAssetItem(
    val title: String = "",
    val provider: String = "",
    val assetUrl: String = "",
    val thumbnailUrl: String = "",
    val creator: String = "",
    val license: String = "",
    val attributionRequired: Boolean = true,
    val commercialCaution: String = "",
    val sceneNumber: Int = 0,
    val timestampRange: String = "",
    val mediaType: String = "Image",
    val reuseStatus: String = ReusePermissionStatus.UNCLEAR_VERIFY.name,
    val relevanceReason: String = "",
    val searchKeywords: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "provider" to provider,
        "assetUrl" to assetUrl,
        "thumbnailUrl" to thumbnailUrl,
        "creator" to creator,
        "license" to license,
        "attributionRequired" to attributionRequired,
        "commercialCaution" to commercialCaution,
        "sceneNumber" to sceneNumber,
        "timestampRange" to timestampRange,
        "mediaType" to mediaType,
        "reuseStatus" to ReusePermissionStatus.fromRaw(reuseStatus).name,
        "relevanceReason" to relevanceReason,
        "searchKeywords" to searchKeywords
    )

    companion object {
        fun fromMap(m: Map<String, Any>): FootageAssetItem = FootageAssetItem(
            title = m["title"] as? String ?: "",
            provider = m["provider"] as? String ?: "",
            assetUrl = m["assetUrl"] as? String ?: "",
            thumbnailUrl = m["thumbnailUrl"] as? String ?: "",
            creator = m["creator"] as? String ?: "",
            license = m["license"] as? String ?: "",
            attributionRequired = m["attributionRequired"] as? Boolean ?: true,
            commercialCaution = m["commercialCaution"] as? String ?: "",
            sceneNumber = (m["sceneNumber"] as? Number)?.toInt() ?: 0,
            timestampRange = m["timestampRange"] as? String ?: "",
            mediaType = (m["mediaType"] as? String)?.ifBlank { "Image" } ?: "Image",
            reuseStatus = ReusePermissionStatus.fromRaw(m["reuseStatus"] as? String).name,
            relevanceReason = m["relevanceReason"] as? String ?: "",
            searchKeywords = m["searchKeywords"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ProjectOverviewData(
    val documentaryTitle: String = "",
    val centralIdea: String = "",
    val uniqueAngle: String = "",
    val targetAudience: String = "",
    val estimatedDuration: String = "",
    val formatAspectRatio: String = "16:9 Long + 9:16 Short",
    val mainObjective: String = ""
) {
    fun isNotEmpty(): Boolean = documentaryTitle.isNotBlank() || centralIdea.isNotBlank()

    fun toMap(): Map<String, Any> = mapOf(
        "documentaryTitle" to documentaryTitle,
        "centralIdea" to centralIdea,
        "uniqueAngle" to uniqueAngle,
        "targetAudience" to targetAudience,
        "estimatedDuration" to estimatedDuration,
        "formatAspectRatio" to formatAspectRatio,
        "mainObjective" to mainObjective
    )

    companion object {
        fun fromMap(m: Map<String, Any>): ProjectOverviewData = ProjectOverviewData(
            documentaryTitle = m["documentaryTitle"] as? String ?: "",
            centralIdea = m["centralIdea"] as? String ?: "",
            uniqueAngle = m["uniqueAngle"] as? String ?: "",
            targetAudience = m["targetAudience"] as? String ?: "",
            estimatedDuration = m["estimatedDuration"] as? String ?: "",
            formatAspectRatio = m["formatAspectRatio"] as? String ?: "16:9 Long + 9:16 Short",
            mainObjective = m["mainObjective"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class StoryStructureData(
    val openingHook: String = "",
    val introduction: String = "",
    val background: String = "",
    val mainChapters: List<String> = emptyList(),
    val keyDiscoveries: String = "",
    val climax: String = "",
    val conclusion: String = ""
) {
    fun isNotEmpty(): Boolean = openingHook.isNotBlank() || mainChapters.isNotEmpty()

    fun toMap(): Map<String, Any> = mapOf(
        "openingHook" to openingHook,
        "introduction" to introduction,
        "background" to background,
        "mainChapters" to mainChapters,
        "keyDiscoveries" to keyDiscoveries,
        "climax" to climax,
        "conclusion" to conclusion
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any>): StoryStructureData = StoryStructureData(
            openingHook = m["openingHook"] as? String ?: "",
            introduction = m["introduction"] as? String ?: "",
            background = m["background"] as? String ?: "",
            mainChapters = (m["mainChapters"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            keyDiscoveries = m["keyDiscoveries"] as? String ?: "",
            climax = m["climax"] as? String ?: "",
            conclusion = m["conclusion"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class BlueprintSceneItem(
    val sceneNumber: Int = 1,
    val startTime: String = "00:00",
    val endTime: String = "00:20",
    val purpose: String = "",
    val voiceOverNarration: String = "",
    val requiredVisuals: String = "",
    val cameraAnimation: String = "",
    val archivalPresentation: String = "",
    val visualContinuity: String = "",
    val onScreenText: String = "",
    val bgmMood: String = "",
    val sfx: String = "",
    val transition: String = "",
    val researchRefs: String = "",
    val searchKeywords: String = "",
    val alternativeVisualIdea: String = "",
    val pronunciationNotes: String = "",
    val pacingEmotionCue: String = "",
    val audioLocalPath: String = ""
) {
    fun estimatedDurationSeconds(): Int {
        val words = voiceOverNarration.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return if (words == 0) 0 else ((words / 2.3f).toInt()).coerceAtLeast(3)
    }

    fun toMap(): Map<String, Any> = mapOf(
        "sceneNumber" to sceneNumber,
        "startTime" to startTime,
        "endTime" to endTime,
        "purpose" to purpose,
        "voiceOverNarration" to voiceOverNarration,
        "requiredVisuals" to requiredVisuals,
        "cameraAnimation" to cameraAnimation,
        "archivalPresentation" to archivalPresentation,
        "visualContinuity" to visualContinuity,
        "onScreenText" to onScreenText,
        "bgmMood" to bgmMood,
        "sfx" to sfx,
        "transition" to transition,
        "researchRefs" to researchRefs,
        "searchKeywords" to searchKeywords,
        "alternativeVisualIdea" to alternativeVisualIdea,
        "pronunciationNotes" to pronunciationNotes,
        "pacingEmotionCue" to pacingEmotionCue,
        "audioLocalPath" to audioLocalPath
    )

    companion object {
        fun fromMap(m: Map<String, Any>): BlueprintSceneItem = BlueprintSceneItem(
            sceneNumber = (m["sceneNumber"] as? Number)?.toInt() ?: 1,
            startTime = m["startTime"] as? String ?: "00:00",
            endTime = m["endTime"] as? String ?: "00:20",
            purpose = m["purpose"] as? String ?: "",
            voiceOverNarration = m["voiceOverNarration"] as? String ?: "",
            requiredVisuals = m["requiredVisuals"] as? String ?: "",
            cameraAnimation = m["cameraAnimation"] as? String ?: "",
            archivalPresentation = m["archivalPresentation"] as? String ?: "",
            visualContinuity = m["visualContinuity"] as? String ?: "",
            onScreenText = m["onScreenText"] as? String ?: "",
            bgmMood = m["bgmMood"] as? String ?: "",
            sfx = m["sfx"] as? String ?: "",
            transition = m["transition"] as? String ?: "",
            researchRefs = m["researchRefs"] as? String ?: "",
            searchKeywords = m["searchKeywords"] as? String ?: "",
            alternativeVisualIdea = m["alternativeVisualIdea"] as? String ?: "",
            pronunciationNotes = m["pronunciationNotes"] as? String ?: "",
            pacingEmotionCue = m["pacingEmotionCue"] as? String ?: "",
            audioLocalPath = m["audioLocalPath"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ChecklistEntry(
    val label: String = "",
    val completed: Boolean = false,
    val issueNote: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "label" to label,
        "completed" to completed,
        "issueNote" to issueNote
    )

    companion object {
        fun fromMap(m: Map<String, Any>): ChecklistEntry = ChecklistEntry(
            label = m["label"] as? String ?: "",
            completed = m["completed"] as? Boolean ?: false,
            issueNote = m["issueNote"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class StudioProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topicTitle: String = "",
    val categoryBeat: String = "Current Affairs",
    val language: String = AppLanguage.HINGLISH.name,
    val packageFormat: String = PackageFormat.DUAL.name,
    val angle: String = "",
    val hook: String = "",
    val whyCoverNow: String = "",
    val stage: String = ProjectStage.DISCOVERED.name,
    val topicApproved: Boolean = false,
    val finalApproved: Boolean = false,
    val verifiedFacts: List<Map<String, Any>> = emptyList(),
    val disputedClaims: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val shortScript: String = "",
    val longScript: String = "",
    val visualDirections: String = "",
    val onScreenText: List<String> = emptyList(),
    val titleOptions: List<String> = emptyList(),
    val thumbnailConcepts: List<String> = emptyList(),
    val descriptionAndChapters: String = "",
    val projectOverview: Map<String, Any> = emptyMap(),
    val storyStructure: Map<String, Any> = emptyMap(),
    val blueprintScenes: List<Map<String, Any>> = emptyList(),
    val footageAssets: List<Map<String, Any>> = emptyList(),
    val voiceoverVoice: String = "Kore",
    val voiceoverStatus: String = "NOT_GENERATED",
    val voiceoverLocalPath: String = "",
    val voiceoverDirectionNotes: String = "",
    val subtitlesSrt: String = "",
    val capcutPlan: String = "",
    val capcutChecklist: List<Map<String, Any>> = emptyList(),
    val auditChecklist: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedFacts(): List<VerifiedFactItem> = verifiedFacts.map(VerifiedFactItem::fromMap)
    fun parsedFootage(): List<FootageAssetItem> = footageAssets.map(FootageAssetItem::fromMap)
    fun parsedChecklist(): List<ChecklistEntry> = capcutChecklist.map(ChecklistEntry::fromMap)
    fun parsedAuditChecklist(): List<ChecklistEntry> = auditChecklist.map(ChecklistEntry::fromMap)
    fun parsedOverview(): ProjectOverviewData = ProjectOverviewData.fromMap(projectOverview)
    fun parsedStoryStructure(): StoryStructureData = StoryStructureData.fromMap(storyStructure)
    fun parsedBlueprintScenes(): List<BlueprintSceneItem> =
        blueprintScenes.map(BlueprintSceneItem::fromMap).sortedBy { it.sceneNumber }
}

@Keep
@IgnoreExtraProperties
data class AutomationRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val triggerType: String = "MANUAL",
    val status: String = "COMPLETED",
    val attemptCount: Int = 1,
    val summary: String = "",
    val projectId: String = "",
    val errorMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class TopicCandidate(
    val title: String,
    val category: String,
    val angle: String,
    val hook: String,
    val whyCoverNow: String,
    val sourceHints: List<String>,
    val researchPathway: String = ""
)

data class SrtCue(
    val index: Int,
    val startTime: String,
    val endTime: String,
    val text: String
)

data class BatteryStatusInfo(
    val percentage: Int = -1,
    val charging: Boolean = false,
    val chargeSource: String = "Unknown",
    val healthLabel: String = "Unknown",
    val temperatureCelsius: Float = 0f,
    val voltageMv: Int = 0
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val precisionLabel: String,
    val areaLabel: String
)

enum class ReadinessTier(val label: String) {
    READY_NOW("Works Immediately"),
    FIREBASE_READY("Active via Firebase"),
    PERMISSION_REQUIRED("Permission Gated"),
    EXTERNAL_SETUP_REQUIRED("External Setup Required")
}

data class IntegrationStatusItem(
    val name: String,
    val tier: ReadinessTier,
    val detail: String,
    val setupRequirement: String
)
