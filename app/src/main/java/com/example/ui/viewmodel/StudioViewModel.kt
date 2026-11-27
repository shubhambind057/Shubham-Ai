package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.ChecklistEntry
import com.example.data.model.FootageAssetItem
import com.example.data.model.PackageFormat
import com.example.data.model.ProjectStage
import com.example.data.model.StudioProject
import com.example.data.model.TopicCandidate
import com.example.data.repository.ArchivalPortalLink
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.StockMediaRepository
import com.example.data.repository.StockMediaSearchResult
import com.example.platform.SrtAndZipExporter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StudioSubTab(val label: String) {
    DISCOVER("Topics"),
    RESEARCH("Deep Research"),
    SCRIPTS("Scripts"),
    BLUEPRINT("Blueprint"),
    MEDIA("Clips & VO"),
    SUBTITLES("SRT & Audit"),
    HISTORY("Saved")
}

class StudioViewModel(
    application: Application,
    private val firestoreRepo: FirestoreRepository,
    private val aiRepo: GeminiAiRepository,
    private val stockRepo: StockMediaRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StudioVM"
    }

    val projects: StateFlow<List<StudioProject>> = firestoreRepo.observeStudioProjects()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.e(TAG, "Projects stream error", firestoreError ?: error)
            _errorMessage.value = firestoreError?.localizedMessage ?: "Failed to sync studio projects."
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _activeSubTab = MutableStateFlow(StudioSubTab.DISCOVER)
    val activeSubTab: StateFlow<StudioSubTab> = _activeSubTab.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _discoveredTopics = MutableStateFlow<List<TopicCandidate>>(emptyList())
    val discoveredTopics: StateFlow<List<TopicCandidate>> = _discoveredTopics.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _busyLabel = MutableStateFlow("")
    val busyLabel: StateFlow<String> = _busyLabel.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _stockResult = MutableStateFlow<StockMediaSearchResult?>(null)
    val stockResult: StateFlow<StockMediaSearchResult?> = _stockResult.asStateFlow()

    private val _archivalPortals = MutableStateFlow<List<ArchivalPortalLink>>(emptyList())
    val archivalPortals: StateFlow<List<ArchivalPortalLink>> = _archivalPortals.asStateFlow()

    private val _selectedVoice = MutableStateFlow("Kore")
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _clipSearchQuerySeed = MutableStateFlow("")
    val clipSearchQuerySeed: StateFlow<String> = _clipSearchQuerySeed.asStateFlow()

    private val _clipTargetSceneNumber = MutableStateFlow(0)
    val clipTargetSceneNumber: StateFlow<Int> = _clipTargetSceneNumber.asStateFlow()

    private val _clipTargetTimestamp = MutableStateFlow("")
    val clipTargetTimestamp: StateFlow<String> = _clipTargetTimestamp.asStateFlow()

    fun selectSubTab(tab: StudioSubTab) {
        _activeSubTab.value = tab
    }

    fun selectProject(projectId: String, openTab: StudioSubTab? = null) {
        _selectedProjectId.value = projectId
        if (openTab != null) _activeSubTab.value = openTab
    }

    fun clearAlerts() {
        _errorMessage.value = null
        _statusBanner.value = null
    }

    fun setVoiceOption(voice: String) {
        _selectedVoice.value = voice
    }

    fun activeProject(): StudioProject? {
        val id = _selectedProjectId.value
        val list = projects.value
        return if (id != null) list.firstOrNull { it.id == id } ?: list.firstOrNull()
        else list.firstOrNull()
    }

    /**
     * Launches Scene-Linked Clip Finder directly from any Blueprint scene!
     */
    fun openClipFinderForScene(sceneNumber: Int, timestampRange: String, keywords: String) {
        _clipTargetSceneNumber.value = sceneNumber
        _clipTargetTimestamp.value = timestampRange
        _clipSearchQuerySeed.value = keywords
        _archivalPortals.value = stockRepo.buildArchivalPortalLinks(keywords)
        _activeSubTab.value = StudioSubTab.MEDIA
        if (keywords.isNotBlank()) {
            searchStockMedia("Wikimedia Commons", keywords, "", sceneNumber, timestampRange)
        }
    }

    fun startTodaysResearch(
        beat: String,
        language: AppLanguage,
        customFocus: String = "",
        loadMore: Boolean = false
    ) {
        _activeSubTab.value = StudioSubTab.DISCOVER
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = if (loadMore) {
                "Finding fresh underexplored PEHLU PROJECT angles..."
            } else {
                "Searching reliable web sources for PEHLU PROJECT documentary topics..."
            }
            _errorMessage.value = null
            _statusBanner.value = null
            try {
                val exclude = if (loadMore) _discoveredTopics.value.map { it.title } else emptyList()
                val topics = aiRepo.discoverTopics(
                    beat = beat,
                    language = language,
                    customFocus = customFocus,
                    excludeTitles = exclude,
                    forceRefresh = loadMore
                )
                _discoveredTopics.value = if (loadMore) {
                    (_discoveredTopics.value + topics).distinctBy { it.title }
                } else {
                    topics
                }
                _statusBanner.value = if (topics.isNotEmpty()) {
                    "Found ${topics.size} verifiable PEHLU PROJECT candidates. Review and approve a topic to begin Deep Research."
                } else {
                    "No topics returned for this query. Try a broader beat."
                }
            } catch (e: TimeoutCancellationException) {
                _errorMessage.value = "Topic discovery timed out after 60s. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Topic discovery failed", e)
                _errorMessage.value = GeminiAiRepository.formatUserFriendlyAiError(e, "Topic discovery failed.")
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    fun approveTopicCandidate(
        candidate: TopicCandidate,
        language: AppLanguage,
        packageFormat: PackageFormat
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Saving approved topic to PEHLU PROJECT cloud archive..."
            _errorMessage.value = null
            val project = StudioProject(
                topicTitle = candidate.title,
                categoryBeat = candidate.category,
                language = language.name,
                packageFormat = packageFormat.name,
                angle = candidate.angle,
                hook = candidate.hook,
                whyCoverNow = candidate.whyCoverNow,
                stage = ProjectStage.TOPIC_APPROVED.name,
                topicApproved = true,
                finalApproved = false,
                voiceoverVoice = _selectedVoice.value
            )
            val result = firestoreRepo.createStudioProject(project)
            _isBusy.value = false
            _busyLabel.value = ""
            result.onSuccess { newId ->
                _selectedProjectId.value = newId
                _activeSubTab.value = StudioSubTab.RESEARCH
                _statusBanner.value = "Topic approved! Run 5-Tier Deep Research next."
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Failed to save approved topic."
            }
        }
    }

    fun approveExistingProjectTopic(project: StudioProject) {
        viewModelScope.launch {
            firestoreRepo.updateStudioProject(
                project.id,
                mapOf("topicApproved" to true, "stage" to ProjectStage.TOPIC_APPROVED.name)
            ).onSuccess {
                _statusBanner.value = "Topic approved for PEHLU PROJECT production."
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Could not approve topic."
            }
        }
    }

    fun runFactCheckForActiveProject(project: StudioProject) {
        if (!project.topicApproved) {
            _errorMessage.value = "Please approve the topic before running Deep Research."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Running 5-Tier Deep Research (Primary → Official → Academic → Archives)..."
            _errorMessage.value = null
            try {
                val lang = AppLanguage.fromRaw(project.language)
                val dossier = aiRepo.runFactCheckResearch(
                    topicTitle = project.topicTitle,
                    angle = project.angle,
                    language = lang
                )
                val updatedProject = project.copy(
                    verifiedFacts = dossier.verifiedFacts.map { it.toMap() },
                    disputedClaims = dossier.disputedClaims,
                    assumptions = dossier.assumptions
                )
                val audit = SrtAndZipExporter.buildTenPointAudit(updatedProject)
                firestoreRepo.updateStudioProject(
                    project.id,
                    mapOf(
                        "verifiedFacts" to dossier.verifiedFacts.map { it.toMap() },
                        "disputedClaims" to dossier.disputedClaims,
                        "assumptions" to dossier.assumptions,
                        "auditChecklist" to audit.map { it.toMap() },
                        "stage" to ProjectStage.IN_PRODUCTION.name
                    )
                ).onSuccess {
                    _statusBanner.value = "5-Tier Research saved (${dossier.verifiedFacts.size} classified claims, ${dossier.disputedClaims.size} disputed)."
                }.onFailure {
                    _errorMessage.value = it.localizedMessage ?: "Failed to persist research dossier."
                }
            } catch (e: TimeoutCancellationException) {
                _errorMessage.value = "Deep research timed out after 60s. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fact check failed", e)
                _errorMessage.value = GeminiAiRepository.formatUserFriendlyAiError(e, "Deep research failed.")
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    fun generateDualScriptsAndCapCut(project: StudioProject, economyMode: Boolean = true) {
        if (!project.topicApproved) {
            _errorMessage.value = "Approve the topic first before generating scripts."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Writing PEHLU PROJECT Scripts, 5 Titles, 3 Thumbnails & Chapters..."
            _errorMessage.value = null
            try {
                val lang = AppLanguage.fromRaw(project.language)
                val fmt = PackageFormat.fromRaw(project.packageFormat)
                val pkg = aiRepo.generateProductionPackage(
                    topicTitle = project.topicTitle,
                    angle = project.angle,
                    hook = project.hook,
                    facts = project.parsedFacts(),
                    disputedClaims = project.disputedClaims,
                    language = lang,
                    packageFormat = fmt,
                    economyMode = economyMode
                )
                val autoSrt = SrtAndZipExporter.generateSrtFromScript(
                    pkg.shortScript.ifBlank { pkg.longScript }
                )
                firestoreRepo.updateStudioProject(
                    project.id,
                    mapOf(
                        "shortScript" to pkg.shortScript,
                        "longScript" to pkg.longScript,
                        "visualDirections" to pkg.visualDirections,
                        "onScreenText" to pkg.onScreenText,
                        "titleOptions" to pkg.titleOptions,
                        "thumbnailConcepts" to pkg.thumbnailConcepts,
                        "descriptionAndChapters" to pkg.descriptionAndChapters,
                        "voiceoverDirectionNotes" to pkg.voiceoverDirectionNotes,
                        "subtitlesSrt" to autoSrt,
                        "capcutPlan" to pkg.capcutPlan,
                        "capcutChecklist" to pkg.checklist.map { it.toMap() },
                        "stage" to ProjectStage.IN_PRODUCTION.name
                    )
                ).onSuccess {
                    _activeSubTab.value = StudioSubTab.SCRIPTS
                    _statusBanner.value = "Scripts, Titles, Thumbnails & Chapters generated! Build the Complete Video Blueprint next."
                }.onFailure {
                    _errorMessage.value = it.localizedMessage ?: "Failed to save scripts."
                }
            } catch (e: TimeoutCancellationException) {
                _errorMessage.value = "Script generation timed out after 60s. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Script generation failed", e)
                _errorMessage.value = GeminiAiRepository.formatUserFriendlyAiError(e, "Failed to generate scripts.")
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    /**
     * Module 6: Dedicated Complete Documentary Video Blueprint generator.
     */
    fun generateCompleteVideoBlueprint(project: StudioProject, economyMode: Boolean = true) {
        if (!project.topicApproved) {
            _errorMessage.value = "Approve the topic first before generating the Video Blueprint."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Building Editor-Ready Timestamped Video Blueprint (Scenes, Camera, Archival & Audio)..."
            _errorMessage.value = null
            try {
                val lang = AppLanguage.fromRaw(project.language)
                val fmt = PackageFormat.fromRaw(project.packageFormat)
                val bp = aiRepo.generateVideoBlueprint(
                    topicTitle = project.topicTitle,
                    angle = project.angle,
                    hook = project.hook,
                    shortScript = project.shortScript,
                    longScript = project.longScript,
                    facts = project.parsedFacts(),
                    language = lang,
                    packageFormat = fmt,
                    economyMode = economyMode
                )
                val tempProj = project.copy(
                    projectOverview = bp.overview.toMap(),
                    storyStructure = bp.storyStructure.toMap(),
                    blueprintScenes = bp.scenes.map { it.toMap() }
                )
                val blueprintSrt = SrtAndZipExporter.generateSrtFromBlueprint(tempProj)
                val updatedAudit = SrtAndZipExporter.buildTenPointAudit(tempProj.copy(subtitlesSrt = blueprintSrt))

                firestoreRepo.updateStudioProject(
                    project.id,
                    mapOf(
                        "projectOverview" to bp.overview.toMap(),
                        "storyStructure" to bp.storyStructure.toMap(),
                        "blueprintScenes" to bp.scenes.map { it.toMap() },
                        "subtitlesSrt" to blueprintSrt.ifBlank { project.subtitlesSrt },
                        "auditChecklist" to updatedAudit.map { it.toMap() },
                        "stage" to ProjectStage.IN_PRODUCTION.name
                    )
                ).onSuccess {
                    _activeSubTab.value = StudioSubTab.BLUEPRINT
                    _statusBanner.value = "Complete Video Blueprint ready (${bp.scenes.size} timestamped scenes + synced SRT & 7-column editing sheet)."
                }.onFailure {
                    _errorMessage.value = it.localizedMessage ?: "Failed to save video blueprint."
                }
            } catch (e: TimeoutCancellationException) {
                _errorMessage.value = "Blueprint generation timed out after 60s. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Blueprint generation failed", e)
                _errorMessage.value = GeminiAiRepository.formatUserFriendlyAiError(e, "Failed to build video blueprint.")
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    fun saveEditedScripts(projectId: String, shortScript: String, longScript: String, visualDirections: String) {
        viewModelScope.launch {
            firestoreRepo.updateStudioProject(
                projectId,
                mapOf(
                    "shortScript" to shortScript.take(20000),
                    "longScript" to longScript.take(60000),
                    "visualDirections" to visualDirections.take(25000)
                )
            ).onSuccess { _statusBanner.value = "Scripts saved to cloud archive." }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save edits." }
        }
    }

    fun searchStockMedia(
        provider: String,
        query: String,
        backendProxyUrl: String,
        sceneNumber: Int = _clipTargetSceneNumber.value,
        timestampRange: String = _clipTargetTimestamp.value
    ) {
        _archivalPortals.value = stockRepo.buildArchivalPortalLinks(query)
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Searching $provider..."
            _errorMessage.value = null
            val result = if (provider.equals("Wikimedia Commons", ignoreCase = true)) {
                stockRepo.searchWikimediaCommons(query, sceneNumber = sceneNumber, timestampRange = timestampRange)
            } else {
                stockRepo.searchViaBackendProxy(provider, query, backendProxyUrl, sceneNumber, timestampRange)
            }
            _stockResult.value = result
            _isBusy.value = false
            _busyLabel.value = ""
        }
    }

    fun attachFootageAsset(project: StudioProject, asset: FootageAssetItem) {
        viewModelScope.launch {
            val current = project.parsedFootage()
            val linkedAsset = asset.copy(
                sceneNumber = if (asset.sceneNumber > 0) asset.sceneNumber else _clipTargetSceneNumber.value,
                timestampRange = asset.timestampRange.ifBlank { _clipTargetTimestamp.value }
            )
            if (current.any { it.assetUrl == linkedAsset.assetUrl && it.sceneNumber == linkedAsset.sceneNumber }) {
                _statusBanner.value = "Asset already attached to this scene."
                return@launch
            }
            val updated = (current + linkedAsset).take(40).map { it.toMap() }
            firestoreRepo.updateStudioProject(project.id, mapOf("footageAssets" to updated))
                .onSuccess {
                    val scTag = if (linkedAsset.sceneNumber > 0) " (Scene ${linkedAsset.sceneNumber})" else ""
                    _statusBanner.value = "Attached \"${linkedAsset.title}\"$scTag with license & reuse metadata."
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to attach asset." }
        }
    }

    fun generateAiVoiceover(project: StudioProject, useShortScript: Boolean) {
        val text = if (useShortScript) project.shortScript else project.longScript
        if (text.isBlank()) {
            _errorMessage.value = "Generate or write a script before synthesizing AI voiceover."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Synthesizing optional AI voiceover via Gemini TTS (${_selectedVoice.value})..."
            _errorMessage.value = null
            try {
                val lang = AppLanguage.fromRaw(project.language)
                val result = aiRepo.synthesizeVoiceoverAudio(
                    context = getApplication(),
                    narrationText = text,
                    voiceName = _selectedVoice.value,
                    language = lang,
                    pacingNotes = project.voiceoverDirectionNotes
                )
                firestoreRepo.updateStudioProject(
                    project.id,
                    mapOf(
                        "voiceoverVoice" to _selectedVoice.value,
                        "voiceoverStatus" to "GENERATED_LOCAL",
                        "voiceoverLocalPath" to result.file.absolutePath
                    )
                )
                _statusBanner.value = "Generated real WAV voiceover (${result.byteCount / 1024} KB). Ready to preview and include in ZIP."
            } catch (e: TimeoutCancellationException) {
                _errorMessage.value = "Voiceover generation timed out after 60s."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voiceover generation failed", e)
                _errorMessage.value = GeminiAiRepository.formatUserFriendlyAiError(
                    e,
                    "Gemini TTS did not return audio. No fake audio was created; use free Device TTS preview."
                )
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    /**
     * Module 8: Scene-wise optional audio generation to save quota & allow granular re-takes.
     */
    fun generateSceneVoiceover(project: StudioProject, sceneNumber: Int) {
        val scenes = project.parsedBlueprintScenes()
        val targetIdx = scenes.indexOfFirst { it.sceneNumber == sceneNumber }
        if (targetIdx < 0) {
            _errorMessage.value = "Scene $sceneNumber not found in blueprint."
            return
        }
        val scene = scenes[targetIdx]
        if (scene.voiceOverNarration.isBlank()) {
            _errorMessage.value = "Scene $sceneNumber has no narration text."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Synthesizing Scene $sceneNumber WAV (${_selectedVoice.value})..."
            _errorMessage.value = null
            try {
                val lang = AppLanguage.fromRaw(project.language)
                val res = aiRepo.synthesizeVoiceoverAudio(
                    context = getApplication(),
                    narrationText = scene.voiceOverNarration,
                    voiceName = _selectedVoice.value,
                    language = lang,
                    pacingNotes = "${scene.pacingEmotionCue} ${scene.pronunciationNotes}".trim(),
                    filePrefix = "pehlu_scene_${sceneNumber}"
                )
                val updatedScenes = scenes.toMutableList().apply {
                    this[targetIdx] = scene.copy(audioLocalPath = res.file.absolutePath)
                }
                firestoreRepo.updateStudioProject(
                    project.id,
                    mapOf(
                        "blueprintScenes" to updatedScenes.map { it.toMap() },
                        "voiceoverStatus" to "GENERATED_LOCAL"
                    )
                )
                _statusBanner.value = "Generated Scene $sceneNumber WAV (${res.byteCount / 1024} KB). Included in ZIP export."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scene VO generation failed", e)
                _errorMessage.value = GeminiAiRepository.formatUserFriendlyAiError(
                    e,
                    "Scene $sceneNumber TTS failed. No fake audio created; use free Device TTS preview."
                )
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    fun saveSubtitlesAndCapCut(
        projectId: String,
        srt: String,
        capcutPlan: String,
        checklist: List<ChecklistEntry>,
        auditChecklist: List<ChecklistEntry>
    ) {
        viewModelScope.launch {
            firestoreRepo.updateStudioProject(
                projectId,
                mapOf(
                    "subtitlesSrt" to srt.take(40000),
                    "capcutPlan" to capcutPlan.take(35000),
                    "capcutChecklist" to checklist.take(30).map { it.toMap() },
                    "auditChecklist" to auditChecklist.take(25).map { it.toMap() }
                )
            ).onSuccess { _statusBanner.value = "Subtitles, Editing Plan & 10-Point Audit saved." }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save edits." }
        }
    }

    fun setFinalContentApproved(project: StudioProject, approved: Boolean) {
        viewModelScope.launch {
            val stage = if (approved) ProjectStage.FINAL_APPROVED.name else ProjectStage.IN_PRODUCTION.name
            firestoreRepo.updateStudioProject(
                project.id,
                mapOf("finalApproved" to approved, "stage" to stage)
            ).onSuccess {
                _statusBanner.value = if (approved) {
                    "Final review approved! Full 14-Deliverable PEHLU PROJECT ZIP export is unlocked."
                } else {
                    "Final approval reverted to In Production."
                }
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to update approval state."
            }
        }
    }

    fun buildZipPackageForShare(project: StudioProject, onReady: (File) -> Unit) {
        if (!project.finalApproved) {
            _errorMessage.value = "Complete the 10-Point Final Review and mark Approved before exporting the ZIP."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Bundling all 14 PEHLU PROJECT deliverables into ZIP..."
            try {
                val zipFile = withContext(Dispatchers.IO) {
                    SrtAndZipExporter.exportProjectZipToCache(getApplication(), project)
                }
                firestoreRepo.updateStudioProject(project.id, mapOf("stage" to ProjectStage.EXPORTED.name))
                _statusBanner.value = "Exported ${zipFile.name} (${zipFile.length() / 1024} KB)."
                onReady(zipFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ZIP export failed", e)
                _errorMessage.value = e.localizedMessage ?: "ZIP export failed."
            } finally {
                _isBusy.value = false
                _busyLabel.value = ""
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteStudioProject(projectId)
                .onSuccess {
                    if (_selectedProjectId.value == projectId) _selectedProjectId.value = null
                    _statusBanner.value = "Project deleted from cloud archive."
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete project." }
        }
    }
}
