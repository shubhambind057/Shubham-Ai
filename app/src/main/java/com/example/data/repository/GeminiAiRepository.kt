package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AppLanguage
import com.example.data.model.BlueprintSceneItem
import com.example.data.model.ChecklistEntry
import com.example.data.model.CitationItem
import com.example.data.model.ClaimClassification
import com.example.data.model.PackageFormat
import com.example.data.model.ProjectOverviewData
import com.example.data.model.StoryStructureData
import com.example.data.model.TopicCandidate
import com.example.data.model.VerifiedFactItem
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

data class ChatStreamChunk(
    val accumulatedText: String,
    val citations: List<CitationItem>,
    val completed: Boolean,
    val usedWebSearch: Boolean = false
)

data class FactCheckResult(
    val verifiedFacts: List<VerifiedFactItem>,
    val disputedClaims: List<String>,
    val assumptions: List<String>
)

data class ProductionPackageResult(
    val shortScript: String,
    val longScript: String,
    val visualDirections: String,
    val onScreenText: List<String>,
    val titleOptions: List<String>,
    val thumbnailConcepts: List<String>,
    val descriptionAndChapters: String,
    val voiceoverDirectionNotes: String,
    val capcutPlan: String,
    val checklist: List<ChecklistEntry>
)

data class VideoBlueprintResult(
    val overview: ProjectOverviewData,
    val storyStructure: StoryStructureData,
    val scenes: List<BlueprintSceneItem>
)

data class VoiceoverGenerationResult(
    val file: File,
    val mimeType: String,
    val byteCount: Long
)

/**
 * Quota-Resilient Dual-Tier Gemini AI Repository for SHUBHAM AI × PEHLU PROJECT:
 * - Avoids paid/0-quota Google Search Grounding tool calls on Firebase Spark (free) tier
 *   by pairing live keyless Wikipedia MediaWiki reference lookup (`FreeWebResearchProvider`)
 *   with free-tier-friendly `gemini-3.5-flash-lite` and `gemini-3.8-flash` models.
 * - Automatically cascades between `gemini-3.5-flash-lite` and `gemini-3.8-flash` on
 *   HTTP 429 / rate-limit errors with short backoff so a single model bucket limit does not
 *   block Topic Discovery, Deep Research, Chat, Scripts, or Video Blueprints.
 * - Preserves topic caching and surfaces transparent error details if all tiers fail.
 */
class GeminiAiRepository(
    private val webResearch: FreeWebResearchProvider = FreeWebResearchProvider()
) {

    companion object {
        private const val TAG = "GeminiAiRepository"
        const val ECONOMY_MODEL = "gemini-3.5-flash-lite"
        const val RESEARCH_MODEL = "gemini-3.8-flash"
        const val TTS_MODEL = "gemini-3.1-flash-tts-preview"
        private const val INFERENCE_TIMEOUT_MS = 60_000L
        private const val RETRY_BACKOFF_MS = 1_500L
        private const val MAX_CHAT_HISTORY_TURNS = 6
        private const val MAX_VOICE_HISTORY_TURNS = 5

        fun isQuotaOrRateLimitError(error: Throwable): Boolean {
            val combined = generateSequence(error) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
            return combined.contains("429") ||
                combined.contains("quota") ||
                combined.contains("resource_exhausted") ||
                combined.contains("rate limit") ||
                combined.contains("too many requests")
        }

        fun formatUserFriendlyAiError(error: Throwable, fallback: String): String {
            val rawDetail = error.localizedMessage?.trim()?.take(140).orEmpty()
            return if (isQuotaOrRateLimitError(error)) {
                val suffix = if (rawDetail.isNotBlank()) " ($rawDetail)" else ""
                "Free-tier AI rate limit reached on both Flash-Lite and Flash right now. Wait 60 seconds and retry.$suffix"
            } else {
                rawDetail.ifBlank { fallback }
            }
        }
    }

    private val topicCache = ConcurrentHashMap<String, List<TopicCandidate>>()

    // Free-tier text/chat models (NO paid Tool.googleSearch() attached)
    private val lightweightChatModel by lazy {
        Firebase.ai.generativeModel(modelName = ECONOMY_MODEL)
    }

    private val standardChatModel by lazy {
        Firebase.ai.generativeModel(modelName = RESEARCH_MODEL)
    }

    private val visionModelEconomy by lazy {
        Firebase.ai.generativeModel(modelName = ECONOMY_MODEL)
    }

    private val visionModelStandard by lazy {
        Firebase.ai.generativeModel(modelName = RESEARCH_MODEL)
    }

    private val topicSchema = Schema.obj(
        properties = mapOf(
            "topics" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Original PEHLU PROJECT documentary headline"),
                        "category" to Schema.string(description = "History, Science, Technology, Psychology, Society, Real Events, or Mystery"),
                        "angle" to Schema.string(description = "Fresh, underexplored documentary angle avoiding clichéd creator tropes"),
                        "hook" to Schema.string(description = "Strong truthful 5-second opening hook"),
                        "whyCoverNow" to Schema.string(description = "Why this story deserves a documentary without fabricating fake trending metrics"),
                        "researchPathway" to Schema.string(description = "Concrete primary/official archives and research possibilities to investigate"),
                        "sourceHints" to Schema.array(
                            items = Schema.string(),
                            description = "Credible institutions, archives, or publications to verify"
                        )
                    )
                ),
                description = "4 original, verifiable documentary topic candidates"
            )
        )
    )

    private val topicDiscoveryEconomy by lazy {
        Firebase.ai.generativeModel(
            modelName = ECONOMY_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = topicSchema
            }
        )
    }

    private val topicDiscoveryStandard by lazy {
        Firebase.ai.generativeModel(
            modelName = RESEARCH_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = topicSchema
            }
        )
    }

    private val deepResearchSchema = Schema.obj(
        properties = mapOf(
            "claims" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "claim" to Schema.string(description = "Exact factual claim with dates and historical context"),
                        "classification" to Schema.string(description = "One of: CONFIRMED, SUPPORTED, DISPUTED, ESTIMATED, UNVERIFIED"),
                        "sourceType" to Schema.string(description = "PRIMARY or SECONDARY"),
                        "sourceTitle" to Schema.string(description = "Exact document, paper, archive, or article title"),
                        "publisher" to Schema.string(description = "Official institution, journal, archive, or newsroom"),
                        "sourceUrl" to Schema.string(description = "Verified source URL from provided references or empty string if uncertain"),
                        "publishedDate" to Schema.string(description = "Publication date or year"),
                        "supportingEvidence" to Schema.string(description = "Independent corroborating evidence"),
                        "contradictoryEvidence" to Schema.string(description = "Alternative accounts or contradicting evidence, if any"),
                        "limitations" to Schema.string(description = "Gaps, sample limits, or caveats of the evidence")
                    )
                )
            ),
            "disputedClaims" to Schema.array(
                items = Schema.string(),
                description = "Summary list of disputed or conflicting narratives"
            ),
            "assumptions" to Schema.array(
                items = Schema.string(),
                description = "Interpretations, open questions, or assumptions clearly separated from confirmed facts"
            )
        )
    )

    private val factCheckStandard by lazy {
        Firebase.ai.generativeModel(
            modelName = RESEARCH_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = deepResearchSchema
            }
        )
    }

    private val factCheckEconomy by lazy {
        Firebase.ai.generativeModel(
            modelName = ECONOMY_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = deepResearchSchema
            }
        )
    }

    private val scriptPackagingSchema = Schema.obj(
        properties = mapOf(
            "shortScript" to Schema.string(description = "Original 45-60s Shorts/Reels script in natural Hindi/Hinglish with timestamps and source callouts"),
            "longScript" to Schema.string(description = "Original multi-chapter documentary script for PEHLU PROJECT with balanced perspectives and citations"),
            "visualDirections" to Schema.string(description = "High-level visual storytelling & documentary style guidelines"),
            "onScreenText" to Schema.array(items = Schema.string(), description = "6-10 punchy on-screen stat/date/source callouts"),
            "titleOptions" to Schema.array(items = Schema.string(), description = "5 distinct, compelling non-clickbait YouTube documentary title options"),
            "thumbnailConcepts" to Schema.array(items = Schema.string(), description = "3 concrete thumbnail visual concepts with 2-4 word text overlay ideas"),
            "descriptionAndChapters" to Schema.string(description = "YouTube description with source credits placeholder and timestamped chapters"),
            "voiceoverDirectionNotes" to Schema.string(description = "Overall narrator tone, pacing, pronunciation guide for tough names, and pause cues"),
            "capcutPlan" to Schema.string(description = "Editing instructions for music placement, SFX, B-roll, zoom, and pacing"),
            "checklist" to Schema.array(items = Schema.string(), description = "6-8 production editing checks")
        )
    )

    private val blueprintSchema = Schema.obj(
        properties = mapOf(
            "overview" to Schema.obj(
                properties = mapOf(
                    "documentaryTitle" to Schema.string(),
                    "centralIdea" to Schema.string(),
                    "uniqueAngle" to Schema.string(),
                    "targetAudience" to Schema.string(),
                    "estimatedDuration" to Schema.string(),
                    "formatAspectRatio" to Schema.string(),
                    "mainObjective" to Schema.string()
                )
            ),
            "storyStructure" to Schema.obj(
                properties = mapOf(
                    "openingHook" to Schema.string(),
                    "introduction" to Schema.string(),
                    "background" to Schema.string(),
                    "mainChapters" to Schema.array(items = Schema.string()),
                    "keyDiscoveries" to Schema.string(),
                    "climax" to Schema.string(),
                    "conclusion" to Schema.string()
                )
            ),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "sceneNumber" to Schema.integer(),
                        "startTime" to Schema.string(description = "MM:SS start timestamp"),
                        "endTime" to Schema.string(description = "MM:SS end timestamp"),
                        "purpose" to Schema.string(description = "Narrative goal of this scene"),
                        "voiceOverNarration" to Schema.string(description = "Exact Hindi/Hinglish narration spoken in this scene"),
                        "requiredVisuals" to Schema.string(description = "Exact footage, photos, maps, charts, or documents needed"),
                        "cameraAnimation" to Schema.string(description = "Ken Burns zoom, pan, map animation, or motion graphic instruction"),
                        "archivalPresentation" to Schema.string(description = "How archival docs/highlights/citations appear on screen"),
                        "visualContinuity" to Schema.string(description = "How this scene visually connects to the next"),
                        "onScreenText" to Schema.string(description = "Exact caption/date/lower-third shown"),
                        "bgmMood" to Schema.string(description = "Royalty-free background music mood & energy"),
                        "sfx" to Schema.string(description = "Sound effects placement"),
                        "transition" to Schema.string(description = "Cut, L-cut, cross-dissolve, whip, etc."),
                        "researchRefs" to Schema.string(description = "Relevant verified research source/date for this scene"),
                        "searchKeywords" to Schema.string(description = "Exact English archival/stock search keywords for Clip Finder"),
                        "alternativeVisualIdea" to Schema.string(description = "Original map, timeline, or graphic fallback if archival clip is unavailable"),
                        "pronunciationNotes" to Schema.string(description = "Pronunciation guidance for hard names/terms"),
                        "pacingEmotionCue" to Schema.string(description = "Pause, emphasis, pacing & emotion instruction for narrator")
                    )
                ),
                description = "8 to 10 detailed sequential timestamped scenes covering the documentary"
            )
        )
    )

    private val scriptModelEconomy by lazy {
        Firebase.ai.generativeModel(
            modelName = ECONOMY_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scriptPackagingSchema
            }
        )
    }

    private val scriptModelDeep by lazy {
        Firebase.ai.generativeModel(
            modelName = RESEARCH_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scriptPackagingSchema
            }
        )
    }

    private val blueprintModelEconomy by lazy {
        Firebase.ai.generativeModel(
            modelName = ECONOMY_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = blueprintSchema
            }
        )
    }

    private val blueprintModelDeep by lazy {
        Firebase.ai.generativeModel(
            modelName = RESEARCH_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = blueprintSchema
            }
        )
    }

    private val ttsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = TTS_MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    /**
     * Runs [primary] and seamlessly falls back to [fallback] if [primary] hits a
     * free-tier 429 / quota / rate-limit error (with a short backoff).
     */
    private suspend fun generateWithQuotaFallback(
        primary: GenerativeModel,
        fallback: GenerativeModel,
        prompt: String
    ): GenerateContentResponse {
        return try {
            withTimeout(INFERENCE_TIMEOUT_MS) {
                primary.generateContent(prompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isQuotaOrRateLimitError(e)) throw e
            Log.w(TAG, "Primary model hit rate/quota limit; cascading to fallback model: ${e.message}")
            delay(RETRY_BACKOFF_MS)
            withTimeout(INFERENCE_TIMEOUT_MS) {
                fallback.generateContent(prompt)
            }
        }
    }

    /**
     * Quota-resilient streaming chat:
     * - Default (`useWebSearch = false`): Streams on `gemini-3.5-flash-lite` (auto-cascades to `gemini-3.8-flash` on 429).
     * - `useWebSearch = true`: Fetches real keyless live references via `FreeWebResearchProvider` (zero search quota!)
     *   and streams grounded answers with clickable citations.
     */
    fun streamPersonalChat(
        userPrompt: String,
        historyPairs: List<Pair<String, String>>,
        language: AppLanguage,
        memoryNotes: List<String>,
        useWebSearch: Boolean = false,
        locationHint: String? = null
    ): Flow<ChatStreamChunk> = flow {
        val liveRefs = if (useWebSearch) {
            webResearch.searchReferences(userPrompt, limit = 4)
        } else {
            emptyList()
        }
        val citations = liveRefs.map { it.toCitation() }

        val systemPreamble = buildString {
            appendLine("You are Shubham AI, a lightweight personal AI assistant and documentary strategist for PEHLU PROJECT.")
            appendLine(language.promptDirective)
            appendLine("RULES:")
            appendLine("1. Reply naturally in the user's language (Hindi, Hinglish, or English). Keep simple answers concise and fast.")
            appendLine("2. Distinguish Confirmed Facts, Opinions, and Uncertainty honestly. Never fabricate citations or claim you performed an Android device action unless executed.")
            appendLine("3. If the user asks about a deep documentary topic, offer concise help and remind them they can open PEHLU PROJECT Studio for 5-tier research and full video blueprints.")
            if (memoryNotes.isNotEmpty()) {
                appendLine("USER-APPROVED MEMORY:")
                memoryNotes.take(10).forEach { appendLine("- $it") }
            }
            if (!locationHint.isNullOrBlank()) {
                appendLine("OPT-IN LOCATION CONTEXT: $locationHint")
            }
            if (liveRefs.isNotEmpty()) {
                appendLine("LIVE VERIFIED WEB REFERENCES (use and cite accurately, do not invent extra links):")
                liveRefs.forEach { ref ->
                    appendLine("- ${ref.title} (${ref.lastEdited}): ${ref.snippet} [${ref.url}]")
                }
            }
        }

        val chatHistory = buildList {
            add(content("user") { text("System instructions:\n$systemPreamble") })
            add(content("model") { text("Understood.") })
            historyPairs.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { (role, msg) ->
                val mappedRole = if (role == "assistant") "model" else "user"
                add(content(mappedRole) { text(msg.take(1200)) })
            }
        }

        suspend fun streamFrom(model: GenerativeModel): String {
            val chat = model.startChat(history = chatHistory)
            val accumulated = StringBuilder()
            withTimeout(INFERENCE_TIMEOUT_MS) {
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        accumulated.append(text)
                        emit(
                            ChatStreamChunk(
                                accumulatedText = accumulated.toString(),
                                citations = citations,
                                completed = false,
                                usedWebSearch = useWebSearch
                            )
                        )
                    }
                }
            }
            return accumulated.toString()
        }

        val primary = if (useWebSearch) standardChatModel else lightweightChatModel
        val fallback = if (useWebSearch) lightweightChatModel else standardChatModel

        val finalText = try {
            streamFrom(primary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isQuotaOrRateLimitError(e)) throw e
            Log.w(TAG, "Chat primary model hit rate/quota limit; switching to fallback model")
            delay(RETRY_BACKOFF_MS)
            streamFrom(fallback)
        }

        emit(
            ChatStreamChunk(
                accumulatedText = finalText,
                citations = citations,
                completed = true,
                usedWebSearch = useWebSearch
            )
        )
    }

    /**
     * Low-latency, low-quota voice replies using `gemini-3.5-flash-lite` with auto-fallback.
     */
    suspend fun generateVoiceReply(
        spokenText: String,
        historyPairs: List<Pair<String, String>>,
        language: AppLanguage,
        memoryNotes: List<String>
    ): String {
        val prompt = buildString {
            appendLine("You are Shubham AI in a real-time spoken voice conversation for PEHLU PROJECT.")
            appendLine(language.promptDirective)
            appendLine("Keep replies natural for listening aloud: 2 to 4 crisp sentences unless asked for detail. Avoid markdown bullets or raw URLs.")
            appendLine("Be honest about facts vs uncertainty. Never pretend you completed a device action.")
            if (memoryNotes.isNotEmpty()) {
                appendLine("Preferences: ${memoryNotes.take(6).joinToString("; ")}")
            }
            if (historyPairs.isNotEmpty()) {
                appendLine("Recent turns:")
                historyPairs.takeLast(MAX_VOICE_HISTORY_TURNS).forEach { (role, text) ->
                    appendLine("${role.uppercase()}: ${text.take(300)}")
                }
            }
            appendLine("USER SAID: $spokenText")
        }

        val response = generateWithQuotaFallback(
            primary = lightweightChatModel,
            fallback = standardChatModel,
            prompt = prompt
        )
        return response.text?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalStateException("Assistant returned an empty voice response.")
    }

    /**
     * Module 3: Quota-safe Topic Discovery for PEHLU PROJECT.
     * Pairs free live Wikipedia reference context with `gemini-3.5-flash-lite` (auto-fallback to `gemini-3.8-flash`),
     * completely avoiding the paid/0-quota Google Search grounding tool error on Spark free tier.
     */
    suspend fun discoverTopics(
        beat: String,
        language: AppLanguage,
        customFocus: String = "",
        excludeTitles: List<String> = emptyList(),
        forceRefresh: Boolean = false
    ): List<TopicCandidate> {
        val cacheKey = "${beat.lowercase()}|${language.name}|${customFocus.trim().lowercase()}"
        if (!forceRefresh && excludeTitles.isEmpty()) {
            topicCache[cacheKey]?.let { cached ->
                if (cached.isNotEmpty()) return cached
            }
        }

        val refQuery = customFocus.trim().ifBlank { "$beat documentary history science India" }
        val liveRefs = webResearch.searchReferences(refQuery, limit = 5)

        val prompt = buildString {
            appendLine("You are the Senior Documentary Researcher for PEHLU PROJECT inside Shubham AI.")
            appendLine("Discover 4 original, underexplored, evidence-rich documentary topics.")
            appendLine("Beat: $beat (History, Science, Technology, Psychology, Society, Real Events, Mysteries, Unexplored Perspectives).")
            if (customFocus.isNotBlank()) {
                appendLine("Specific User Topic / Keyword: $customFocus")
            }
            if (excludeTitles.isNotEmpty()) {
                appendLine("DO NOT repeat these previously suggested topics: ${excludeTitles.joinToString(" | ")}")
            }
            if (liveRefs.isNotEmpty()) {
                appendLine("LIVE VERIFIED REFERENCE LEADS (optional inspiration; always verify facts):")
                liveRefs.forEach { r ->
                    appendLine("- ${r.title}: ${r.snippet}")
                }
            }
            appendLine(language.promptDirective)
            appendLine("RULES:")
            appendLine("- Suggest unique documentary angles rather than generic overdone creator topics.")
            appendLine("- NEVER invent trending status, fake view statistics, or fabricated events.")
            appendLine("- Provide preliminary primary/institutional archives and research pathways.")
        }

        val response = generateWithQuotaFallback(
            primary = topicDiscoveryEconomy,
            fallback = topicDiscoveryStandard,
            prompt = prompt
        )
        val raw = response.text ?: throw IllegalStateException("Topic discovery returned empty output.")
        val json = JSONObject(raw)
        val arr = json.optJSONArray("topics") ?: return emptyList()
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val hintsArr = obj.optJSONArray("sourceHints")
                val hints = buildList {
                    if (hintsArr != null) {
                        for (j in 0 until hintsArr.length()) {
                            add(hintsArr.optString(j))
                        }
                    }
                }.filter { it.isNotBlank() }
                add(
                    TopicCandidate(
                        title = obj.optString("title").trim(),
                        category = obj.optString("category", beat).trim(),
                        angle = obj.optString("angle").trim(),
                        hook = obj.optString("hook").trim(),
                        whyCoverNow = obj.optString("whyCoverNow").trim(),
                        sourceHints = hints,
                        researchPathway = obj.optString("researchPathway").trim()
                    )
                )
            }
        }.filter { it.title.isNotBlank() }

        if (excludeTitles.isEmpty() && parsed.isNotEmpty()) {
            topicCache[cacheKey] = parsed
        }
        return parsed
    }

    /**
     * Module 4: Deep Research Engine with 5-tier claim classification & live keyless reference URLs.
     * Cascades `gemini-3.8-flash` -> `gemini-3.5-flash-lite` cleanly without triggering Google Search 429 limits.
     */
    suspend fun runFactCheckResearch(
        topicTitle: String,
        angle: String,
        language: AppLanguage
    ): FactCheckResult {
        val liveRefs = webResearch.searchReferences(topicTitle, limit = 6)

        val prompt = buildString {
            appendLine("Perform rigorous Deep Research & 5-Tier Fact Verification for PEHLU PROJECT.")
            appendLine("Topic: $topicTitle")
            appendLine("Angle: $angle")
            appendLine(language.promptDirective)
            if (liveRefs.isNotEmpty()) {
                appendLine("LIVE RETRIEVED REFERENCE ARTICLES & VERIFIED URLS:")
                liveRefs.forEachIndexed { idx, ref ->
                    appendLine("${idx + 1}. ${ref.title} (Edited ${ref.lastEdited}) — ${ref.snippet} | URL: ${ref.url}")
                }
            }
            appendLine("SOURCE PRIORITY ORDER: 1) Primary documents/records 2) Official government/institutional sources 3) Academic papers 4) Books & historical archives 5) Reliable journalism 6) Expert secondary analysis.")
            appendLine("CLASSIFY EVERY CLAIM STRICTLY AS ONE OF:")
            appendLine("- CONFIRMED: Strong reliable independent evidence")
            appendLine("- SUPPORTED: Credible evidence, but minor limitations remain")
            appendLine("- DISPUTED: Reliable sources disagree")
            appendLine("- ESTIMATED: Based on estimates or incomplete data")
            appendLine("- UNVERIFIED: Insufficient reliable evidence")
            appendLine("RULES: Never fabricate facts, quotes, statistics, or URLs. Use ONLY verified URLs from the retrieved references above or leave sourceUrl empty if uncertain. Distinguish PRIMARY vs SECONDARY sources, include dates & historical context, note contradictory evidence and limitations fairly. Return 5 to 7 claims.")
        }

        val response = generateWithQuotaFallback(
            primary = factCheckEconomy,
            fallback = factCheckStandard,
            prompt = prompt
        )
        val raw = response.text ?: throw IllegalStateException("Deep research returned empty output.")
        val json = JSONObject(raw)

        val claimsArr = json.optJSONArray("claims") ?: json.optJSONArray("verifiedFacts")
        val facts = buildList {
            if (claimsArr != null) {
                for (i in 0 until claimsArr.length()) {
                    val f = claimsArr.optJSONObject(i) ?: continue
                    val rawUrl = f.optString("sourceUrl").trim()
                    val resolvedUrl = if (rawUrl.startsWith("http")) {
                        rawUrl
                    } else {
                        liveRefs.getOrNull(i)?.url.orEmpty()
                    }
                    add(
                        VerifiedFactItem(
                            claim = f.optString("claim").trim(),
                            classification = ClaimClassification.fromRaw(f.optString("classification")).name,
                            sourceType = if (f.optString("sourceType").equals("PRIMARY", ignoreCase = true)) "PRIMARY" else "SECONDARY",
                            sourceTitle = f.optString("sourceTitle", liveRefs.getOrNull(i)?.title ?: "Verify Source").trim(),
                            publisher = f.optString("publisher").trim(),
                            sourceUrl = resolvedUrl,
                            publishedDate = f.optString("publishedDate", "Verify date").trim(),
                            supportingEvidence = f.optString("supportingEvidence").trim(),
                            contradictoryEvidence = f.optString("contradictoryEvidence").trim(),
                            limitations = f.optString("limitations").trim()
                        )
                    )
                }
            }
        }.filter { it.claim.isNotBlank() }

        val disputedArr = json.optJSONArray("disputedClaims")
        val disputed = buildList {
            if (disputedArr != null) {
                for (i in 0 until disputedArr.length()) {
                    add(disputedArr.optString(i).trim())
                }
            }
        }.filter { it.isNotBlank() }

        val assumptionsArr = json.optJSONArray("assumptions")
        val assumptions = buildList {
            if (assumptionsArr != null) {
                for (i in 0 until assumptionsArr.length()) {
                    add(assumptionsArr.optString(i).trim())
                }
            }
        }.filter { it.isNotBlank() }

        return FactCheckResult(
            verifiedFacts = facts,
            disputedClaims = disputed,
            assumptions = assumptions
        )
    }

    /**
     * Module 5: Original Storytelling, Dual Scripts & YouTube Packaging (with quota cascade).
     */
    suspend fun generateProductionPackage(
        topicTitle: String,
        angle: String,
        hook: String,
        facts: List<VerifiedFactItem>,
        disputedClaims: List<String>,
        language: AppLanguage,
        packageFormat: PackageFormat,
        economyMode: Boolean = true
    ): ProductionPackageResult {
        val factBlock = facts.joinToString("\n") {
            "- [${it.classification} | ${it.sourceType}] ${it.claim} (Source: ${it.sourceTitle}, ${it.publisher}, ${it.publishedDate})"
        }
        val disputedBlock = disputedClaims.joinToString("\n") { "- DISPUTED/UNVERIFIED: $it" }

        val prompt = buildString {
            appendLine("Write original PEHLU PROJECT documentary scripts & packaging based STRICTLY on verified research.")
            appendLine("Topic: $topicTitle")
            appendLine("Angle: $angle")
            appendLine("Hook: $hook")
            appendLine("Format: ${packageFormat.label}")
            appendLine(language.promptDirective)
            appendLine("STYLE: Natural Hindi/Hinglish storytelling, clear explanations, cinematic depth without fake drama or exaggeration, zero copying of other creators, fair presentation of alternative perspectives.")
            appendLine("VERIFIED RESEARCH DOSSIER:")
            appendLine(factBlock.ifBlank { "Use strictly confirmed general knowledge and clearly mark items requiring verification." })
            if (disputedBlock.isNotBlank()) {
                appendLine("DISPUTED/UNVERIFIED (frame carefully, never present as confirmed):")
                appendLine(disputedBlock)
            }
            appendLine("Produce: shortScript (45-60s), longScript (chronological/thematic chapters with inline source references), visualDirections, 6-10 onScreenText items, 5 titleOptions, 3 thumbnailConcepts (visual + short text), descriptionAndChapters, voiceoverDirectionNotes (pronunciation + pacing + pause cues), capcutPlan, and 6-8 checklist items.")
        }

        val primary = if (economyMode) scriptModelEconomy else scriptModelDeep
        val fallback = if (economyMode) scriptModelDeep else scriptModelEconomy
        val response = generateWithQuotaFallback(primary, fallback, prompt)
        val raw = response.text ?: throw IllegalStateException("Script generation returned empty output.")
        val json = JSONObject(raw)

        fun readStrings(key: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotBlank()) add(s)
                }
            }
        }

        val checklist = readStrings("checklist").map { ChecklistEntry(label = it, completed = false) }

        return ProductionPackageResult(
            shortScript = json.optString("shortScript").trim(),
            longScript = json.optString("longScript").trim(),
            visualDirections = json.optString("visualDirections").trim(),
            onScreenText = readStrings("onScreenText"),
            titleOptions = readStrings("titleOptions"),
            thumbnailConcepts = readStrings("thumbnailConcepts"),
            descriptionAndChapters = json.optString("descriptionAndChapters").trim(),
            voiceoverDirectionNotes = json.optString("voiceoverDirectionNotes").trim(),
            capcutPlan = json.optString("capcutPlan").trim(),
            checklist = checklist
        )
    }

    /**
     * Module 6: Complete Production-Ready Documentary Video Blueprint (with quota cascade).
     */
    suspend fun generateVideoBlueprint(
        topicTitle: String,
        angle: String,
        hook: String,
        shortScript: String,
        longScript: String,
        facts: List<VerifiedFactItem>,
        language: AppLanguage,
        packageFormat: PackageFormat,
        economyMode: Boolean = true
    ): VideoBlueprintResult {
        val factSummary = facts.take(10).joinToString("\n") {
            "- [${it.classification}] ${it.claim} (${it.sourceTitle}, ${it.publishedDate})"
        }
        val scriptContext = longScript.ifBlank { shortScript }.take(6000)

        val prompt = buildString {
            appendLine("Create a COMPLETE, EDITOR-READY PEHLU PROJECT DOCUMENTARY VIDEO BLUEPRINT.")
            appendLine("An editor must be able to cut the entire documentary without guessing anything.")
            appendLine("Topic: $topicTitle")
            appendLine("Angle: $angle")
            appendLine("Hook: $hook")
            appendLine("Target Format: ${packageFormat.label}")
            appendLine(language.promptDirective)
            if (factSummary.isNotBlank()) {
                appendLine("RESEARCH REFERENCES:\n$factSummary")
            }
            if (scriptContext.isNotBlank()) {
                appendLine("APPROVED SCRIPT EXCERPT:\n$scriptContext")
            }
            appendLine("INCLUDE:")
            appendLine("A. overview: documentaryTitle, centralIdea, uniqueAngle, targetAudience, estimatedDuration, formatAspectRatio, mainObjective.")
            appendLine("B. storyStructure: openingHook, introduction, background, mainChapters, keyDiscoveries, climax (only if truthful to the real story), conclusion.")
            appendLine("C. scenes (8 to 10 sequential timestamped scenes): exact voiceOverNarration, requiredVisuals, cameraAnimation (zoom/pan/map/timeline), archivalPresentation, visualContinuity, onScreenText, bgmMood, sfx, transition, researchRefs, exact English searchKeywords for Clip Finder, alternativeVisualIdea (original graphics/map if clip unavailable), pronunciationNotes, pacingEmotionCue.")
        }

        val primary = if (economyMode) blueprintModelEconomy else blueprintModelDeep
        val fallback = if (economyMode) blueprintModelDeep else blueprintModelEconomy
        val response = generateWithQuotaFallback(primary, fallback, prompt)
        val raw = response.text ?: throw IllegalStateException("Video blueprint returned empty output.")
        val json = JSONObject(raw)

        val ov = json.optJSONObject("overview")
        val overview = ProjectOverviewData(
            documentaryTitle = ov?.optString("documentaryTitle").orEmpty().ifBlank { topicTitle },
            centralIdea = ov?.optString("centralIdea").orEmpty(),
            uniqueAngle = ov?.optString("uniqueAngle").orEmpty().ifBlank { angle },
            targetAudience = ov?.optString("targetAudience").orEmpty(),
            estimatedDuration = ov?.optString("estimatedDuration").orEmpty(),
            formatAspectRatio = ov?.optString("formatAspectRatio").orEmpty().ifBlank { "16:9 YouTube + 9:16 Shorts" },
            mainObjective = ov?.optString("mainObjective").orEmpty()
        )

        val st = json.optJSONObject("storyStructure")
        val chaptersArr = st?.optJSONArray("mainChapters")
        val chapters = buildList {
            if (chaptersArr != null) {
                for (i in 0 until chaptersArr.length()) {
                    val ch = chaptersArr.optString(i).trim()
                    if (ch.isNotBlank()) add(ch)
                }
            }
        }
        val structure = StoryStructureData(
            openingHook = st?.optString("openingHook").orEmpty().ifBlank { hook },
            introduction = st?.optString("introduction").orEmpty(),
            background = st?.optString("background").orEmpty(),
            mainChapters = chapters,
            keyDiscoveries = st?.optString("keyDiscoveries").orEmpty(),
            climax = st?.optString("climax").orEmpty(),
            conclusion = st?.optString("conclusion").orEmpty()
        )

        val scenesArr = json.optJSONArray("scenes")
        val scenes = buildList {
            if (scenesArr != null) {
                for (i in 0 until scenesArr.length()) {
                    val s = scenesArr.optJSONObject(i) ?: continue
                    add(
                        BlueprintSceneItem(
                            sceneNumber = s.optInt("sceneNumber", i + 1),
                            startTime = s.optString("startTime", "00:00").trim(),
                            endTime = s.optString("endTime", "00:30").trim(),
                            purpose = s.optString("purpose").trim(),
                            voiceOverNarration = s.optString("voiceOverNarration").trim(),
                            requiredVisuals = s.optString("requiredVisuals").trim(),
                            cameraAnimation = s.optString("cameraAnimation").trim(),
                            archivalPresentation = s.optString("archivalPresentation").trim(),
                            visualContinuity = s.optString("visualContinuity").trim(),
                            onScreenText = s.optString("onScreenText").trim(),
                            bgmMood = s.optString("bgmMood").trim(),
                            sfx = s.optString("sfx").trim(),
                            transition = s.optString("transition").trim(),
                            researchRefs = s.optString("researchRefs").trim(),
                            searchKeywords = s.optString("searchKeywords").trim(),
                            alternativeVisualIdea = s.optString("alternativeVisualIdea").trim(),
                            pronunciationNotes = s.optString("pronunciationNotes").trim(),
                            pacingEmotionCue = s.optString("pacingEmotionCue").trim()
                        )
                    )
                }
            }
        }

        return VideoBlueprintResult(overview = overview, storyStructure = structure, scenes = scenes)
    }

    suspend fun analyzeVisualContent(
        bitmap: Bitmap,
        question: String,
        language: AppLanguage
    ): String {
        val scaled = scaleBitmapForInference(bitmap, maxDim = 1280)
        val prompt = buildString {
            appendLine("You are Shubham AI Visual Assistant.")
            appendLine(language.promptDirective)
            appendLine("Describe only what is clearly visible in this image or document. If text is blurry or uncertain, state that honestly instead of guessing.")
            appendLine("User question: ${question.ifBlank { "Explain what is shown in this image or document clearly." }}")
        }

        val input = content {
            image(scaled)
            text(prompt)
        }
        val response = try {
            withTimeout(INFERENCE_TIMEOUT_MS) { visionModelEconomy.generateContent(input) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isQuotaOrRateLimitError(e)) throw e
            delay(RETRY_BACKOFF_MS)
            withTimeout(INFERENCE_TIMEOUT_MS) { visionModelStandard.generateContent(input) }
        }

        return response.text?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalStateException("Vision analysis returned an empty response.")
    }

    /**
     * Module 8: Optional scene-wise or script voiceover via Gemini TTS.
     * Never fakes audio if the provider returns no audio bytes or hits quota.
     */
    suspend fun synthesizeVoiceoverAudio(
        context: Context,
        narrationText: String,
        voiceName: String,
        language: AppLanguage,
        pacingNotes: String = "",
        filePrefix: String = "pehlu_vo"
    ): VoiceoverGenerationResult {
        val cleanText = narrationText.trim().take(2500)
        require(cleanText.isNotEmpty()) { "Script text is empty; review or write narration first." }

        val prompt = buildString {
            appendLine("Read the following PEHLU PROJECT documentary narration aloud in a clear, grounded tone ($voiceName style, ${language.label}). Do not add extra commentary:")
            if (pacingNotes.isNotBlank()) {
                appendLine("Delivery direction: ${pacingNotes.take(200)}")
            }
            appendLine(cleanText)
        }

        val response = withTimeout(INFERENCE_TIMEOUT_MS) {
            ttsModel.generateContent(prompt)
        }

        val audioPart = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.filterIsInstance<InlineDataPart>()
            ?.firstOrNull { it.mimeType.startsWith("audio/") }
            ?: throw IllegalStateException(
                "Gemini TTS did not return inline audio bytes. No fake audio was created; use free Device TTS preview or retry later."
            )

        val rawBytes = audioPart.inlineData
        if (rawBytes.isEmpty()) {
            throw IllegalStateException("Gemini TTS returned an empty audio payload.")
        }

        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "voiceovers").apply { mkdirs() }
            val outFile = File(dir, "${filePrefix}_${System.currentTimeMillis()}.wav")
            val finalBytes = if (audioPart.mimeType.contains("wav", ignoreCase = true) ||
                (rawBytes.size >= 4 && rawBytes[0] == 'R'.code.toByte() && rawBytes[1] == 'I'.code.toByte())
            ) {
                rawBytes
            } else {
                wrapPcm16AsWav(rawBytes, sampleRate = 24000, channels = 1)
            }
            FileOutputStream(outFile).use { it.write(finalBytes) }
            VoiceoverGenerationResult(
                file = outFile,
                mimeType = "audio/wav",
                byteCount = outFile.length()
            )
        }
    }

    private fun scaleBitmapForInference(source: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide <= maxDim) return source
        val scale = maxDim.toFloat() / maxSide.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun wrapPcm16AsWav(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val out = ByteArrayOutputStream(pcmData.size + 44)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }
}
