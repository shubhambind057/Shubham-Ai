package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.ChatMessageRecord
import com.example.data.model.CitationItem
import com.example.data.model.UserPreferenceProfile
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.example.platform.AssistantCommand
import com.example.platform.AssistantCommandRouter
import com.example.platform.DeviceControlManager
import com.example.platform.VoiceEngineState
import com.example.platform.VoiceSessionController
import com.example.platform.VoiceTurn
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data object Loading : ChatStreamState
    data class Streaming(val text: String, val citations: List<CitationItem>, val usedWebSearch: Boolean) : ChatStreamState
    data class Error(val message: String, val retryPrompt: String? = null) : ChatStreamState
}

class AssistantViewModel(
    application: Application,
    private val firestoreRepo: FirestoreRepository,
    private val aiRepo: GeminiAiRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AssistantVM"
    }

    val voiceController = VoiceSessionController(application.applicationContext)
    private val deviceControl = DeviceControlManager(application.applicationContext)

    val preferences: StateFlow<UserPreferenceProfile> = firestoreRepo.observePreferences()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.e(TAG, "Preferences stream error", firestoreError ?: error)
            emit(UserPreferenceProfile())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferenceProfile()
        )

    val chatMessages: StateFlow<List<ChatMessageRecord>> = firestoreRepo.observeChatMessages()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.e(TAG, "Chat stream error", firestoreError ?: error)
            _chatStreamState.value = ChatStreamState.Error(
                firestoreError?.localizedMessage ?: "Failed to load cloud chat history."
            )
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedLanguage = MutableStateFlow(AppLanguage.HINGLISH)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    /**
     * Quota-saving toggle: OFF by default so ordinary chat uses fast economical `gemini-3.5-flash-lite`
     * without burning Google Search grounding quota. Turn ON only when live web lookup is needed.
     */
    private val _chatWebSearchEnabled = MutableStateFlow(false)
    val chatWebSearchEnabled: StateFlow<Boolean> = _chatWebSearchEnabled.asStateFlow()

    private val _pendingDocumentarySeed = MutableStateFlow<String?>(null)
    val pendingDocumentarySeed: StateFlow<String?> = _pendingDocumentarySeed.asStateFlow()

    private val _chatStreamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val chatStreamState: StateFlow<ChatStreamState> = _chatStreamState.asStateFlow()

    private val _voiceTurns = MutableStateFlow<List<VoiceTurn>>(emptyList())
    val voiceTurns: StateFlow<List<VoiceTurn>> = _voiceTurns.asStateFlow()

    private val _voiceSessionRunning = MutableStateFlow(false)
    val voiceSessionRunning: StateFlow<Boolean> = _voiceSessionRunning.asStateFlow()

    private val _useGeminiTtsForVoice = MutableStateFlow(false)
    val useGeminiTtsForVoice: StateFlow<Boolean> = _useGeminiTtsForVoice.asStateFlow()

    private val _voiceAiError = MutableStateFlow<String?>(null)
    val voiceAiError: StateFlow<String?> = _voiceAiError.asStateFlow()

    private var activeChatJob: Job? = null
    private var activeVoiceReplyJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.map { AppLanguage.fromRaw(it.defaultLanguage) }.collect { lang ->
                _selectedLanguage.value = lang
                voiceController.setLanguage(lang)
            }
        }
        viewModelScope.launch {
            preferences.map { it.chatWebSearchDefault }.collect { defaultSearch ->
                _chatWebSearchEnabled.value = defaultSearch
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _selectedLanguage.value = lang
        voiceController.setLanguage(lang)
    }

    fun setChatWebSearchEnabled(enabled: Boolean) {
        _chatWebSearchEnabled.value = enabled
    }

    fun toggleVoiceTtsMode(useGeminiTts: Boolean) {
        _useGeminiTtsForVoice.value = useGeminiTts
    }

    fun clearPendingDocumentarySeed() {
        _pendingDocumentarySeed.value = null
    }

    fun sendChatMessage(rawPrompt: String, locationHint: String? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            val lang = _selectedLanguage.value
            val prefs = preferences.value
            val priorHistory = chatMessages.value.map { it.role to it.content }

            _chatStreamState.value = ChatStreamState.Loading
            firestoreRepo.addChatMessage(
                role = "user",
                content = prompt,
                language = lang,
                certaintyTag = "USER"
            )

            // Step 1: Zero-API-cost local command router (Jarvis quick actions & Documentary handoff)
            val localCommand = AssistantCommandRouter.parse(prompt)
            if (localCommand !is AssistantCommand.None) {
                val replyText = executeLocalCommand(localCommand)
                firestoreRepo.addChatMessage(
                    role = "assistant",
                    content = replyText,
                    language = lang,
                    certaintyTag = "VERIFIED"
                )
                _chatStreamState.value = ChatStreamState.Idle
                return@launch
            }

            // Step 2: Economical LLM routing (Tier 1 lite vs Tier 2 grounded search)
            val useSearch = _chatWebSearchEnabled.value
            try {
                var finalText = ""
                var finalCitations: List<CitationItem> = emptyList()
                aiRepo.streamPersonalChat(
                    userPrompt = prompt,
                    historyPairs = priorHistory,
                    language = lang,
                    memoryNotes = prefs.memoryNotes,
                    useWebSearch = useSearch,
                    locationHint = locationHint
                ).collect { chunk ->
                    finalText = chunk.accumulatedText
                    finalCitations = chunk.citations
                    _chatStreamState.value = ChatStreamState.Streaming(finalText, finalCitations, chunk.usedWebSearch)
                }

                if (finalText.isBlank()) {
                    _chatStreamState.value = ChatStreamState.Error("Assistant returned an empty reply.", retryPrompt = prompt)
                    return@launch
                }

                val certainty = when {
                    finalCitations.isNotEmpty() -> "VERIFIED"
                    finalText.contains("uncertain", ignoreCase = true) ||
                        finalText.contains("unverified", ignoreCase = true) ||
                        finalText.contains("confirm nahi", ignoreCase = true) -> "UNCERTAIN"
                    else -> "MIXED"
                }

                firestoreRepo.addChatMessage(
                    role = "assistant",
                    content = finalText,
                    language = lang,
                    citations = finalCitations,
                    certaintyTag = certainty
                )
                _chatStreamState.value = ChatStreamState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _chatStreamState.value = ChatStreamState.Error(
                    "Response timed out after 60 seconds. Check your connection and retry.",
                    retryPrompt = prompt
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat inference failed", e)
                _chatStreamState.value = ChatStreamState.Error(
                    GeminiAiRepository.formatUserFriendlyAiError(e, "Failed to generate assistant response."),
                    retryPrompt = prompt
                )
            }
        }
    }

    private fun executeLocalCommand(command: AssistantCommand): String {
        return when (command) {
            is AssistantCommand.Flashlight -> {
                val res = deviceControl.setFlashlight(command.enable)
                if (res.success) "Quick Action Executed: ${res.message}"
                else "Quick Action Unsupported: ${res.message}"
            }
            is AssistantCommand.OpenYouTube -> {
                val res = deviceControl.openYouTube("")
                if (res.success) "Quick Action Executed: ${res.message}"
                else "Quick Action Unsupported: ${res.message}"
            }
            is AssistantCommand.SearchYouTube -> {
                val res = deviceControl.openYouTube(command.query)
                if (res.success) "Quick Action Executed: Searching YouTube for \"${command.query}\"."
                else "Quick Action Unsupported: ${res.message}"
            }
            is AssistantCommand.OpenWebsite -> {
                val res = deviceControl.openUrl(command.url)
                if (res.success) "Quick Action Executed: Opened ${command.url}."
                else "Quick Action Blocked/Unsupported: ${res.message}"
            }
            is AssistantCommand.OpenInstalledApp -> {
                val res = deviceControl.launchAppByName(command.appNameQuery)
                if (res.success) "Quick Action Executed: ${res.message}"
                else "Quick Action Unsupported: ${res.message}"
            }
            is AssistantCommand.CheckBattery -> {
                val b = deviceControl.readBatteryStatus()
                if (b.percentage >= 0) {
                    "Battery Status: ${b.percentage}% (${b.chargeSource}), Health: ${b.healthLabel}, Temp: ${String.format("%.1f", b.temperatureCelsius)}°C."
                } else {
                    "Battery status could not be read on this device."
                }
            }
            is AssistantCommand.StartDocumentary -> {
                _pendingDocumentarySeed.value = command.topicSeed
                "PEHLU PROJECT Documentary Mode ready for \"${command.topicSeed}\"! Tap the Launch PEHLU PROJECT Studio button above to start Topic Discovery → Deep 5-Tier Research → Scripts → Complete Video Blueprint → Clip Finder → Voice-Over → Editing Sheet."
            }
            AssistantCommand.None -> ""
        }
    }

    fun clearChatError() {
        if (_chatStreamState.value is ChatStreamState.Error) {
            _chatStreamState.value = ChatStreamState.Idle
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val ids = chatMessages.value.map { it.id }.filter { it.isNotBlank() }
            firestoreRepo.clearChatHistory(ids)
        }
    }

    fun startVoiceSession() {
        _voiceAiError.value = null
        _voiceSessionRunning.value = true
        voiceController.startSession(_selectedLanguage.value) { spokenText ->
            handleSpokenUserTurn(spokenText)
        }
    }

    fun submitTypedVoicePrompt(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!_voiceSessionRunning.value) {
            _voiceSessionRunning.value = true
        }
        handleSpokenUserTurn(clean)
    }

    fun interruptAssistantNow() {
        activeVoiceReplyJob?.cancel()
        activeVoiceReplyJob = null
        voiceController.interruptAndListen()
        val turns = _voiceTurns.value
        if (turns.lastOrNull()?.role == "assistant" && !turns.last().interrupted) {
            _voiceTurns.value = turns.dropLast(1) + turns.last().copy(interrupted = true)
        }
    }

    fun toggleVoiceMute() {
        voiceController.toggleMute()
    }

    fun endVoiceSession() {
        activeVoiceReplyJob?.cancel()
        activeVoiceReplyJob = null
        _voiceSessionRunning.value = false
        voiceController.endSession()
    }

    private fun handleSpokenUserTurn(spokenText: String) {
        activeVoiceReplyJob?.cancel()
        _voiceAiError.value = null
        _voiceTurns.value = _voiceTurns.value + VoiceTurn(role = "user", text = spokenText)
        voiceController.setThinking()

        activeVoiceReplyJob = viewModelScope.launch {
            val lang = _selectedLanguage.value

            // Zero-API-cost local Jarvis commands inside Voice too
            val localCmd = AssistantCommandRouter.parse(spokenText)
            if (localCmd !is AssistantCommand.None) {
                val reply = executeLocalCommand(localCmd)
                _voiceTurns.value = _voiceTurns.value + VoiceTurn(role = "assistant", text = reply)
                voiceController.speakText(reply, lang)
                return@launch
            }

            val history = _voiceTurns.value.dropLast(1).map { it.role to it.text }
            try {
                val reply = aiRepo.generateVoiceReply(
                    spokenText = spokenText,
                    historyPairs = history,
                    language = lang,
                    memoryNotes = preferences.value.memoryNotes
                )
                _voiceTurns.value = _voiceTurns.value + VoiceTurn(role = "assistant", text = reply)

                if (_useGeminiTtsForVoice.value) {
                    try {
                        val audio = aiRepo.synthesizeVoiceoverAudio(
                            context = getApplication(),
                            narrationText = reply,
                            voiceName = "Kore",
                            language = lang
                        )
                        voiceController.playWavFile(audio.file)
                    } catch (ttsErr: Exception) {
                        if (ttsErr is CancellationException) throw ttsErr
                        Log.w(TAG, "Gemini TTS failed in voice session; falling back to device TTS", ttsErr)
                        voiceController.speakText(reply, lang)
                    }
                } else {
                    voiceController.speakText(reply, lang)
                }
            } catch (e: TimeoutCancellationException) {
                _voiceAiError.value = "Voice reply timed out. Tap Mic to speak again."
                if (_voiceSessionRunning.value) {
                    voiceController.interruptAndListen()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice reply failed", e)
                _voiceAiError.value = GeminiAiRepository.formatUserFriendlyAiError(e, "Voice reply failed.")
                if (_voiceSessionRunning.value && voiceController.engineState.value != VoiceEngineState.OFFLINE) {
                    voiceController.stopPlaybackImmediately()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
