package com.example.platform

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.UUID

enum class VoiceEngineState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    THINKING("Thinking..."),
    SPEAKING("Speaking (Tap or speak to interrupt)"),
    MUTED("Microphone Muted"),
    OFFLINE("Offline — Waiting to Reconnect")
}

data class VoiceTurn(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    val text: String,
    val interrupted: Boolean = false
)

/**
 * Real-time two-way voice controller on Android:
 * - Uses native SpeechRecognizer for live Hindi/Hinglish/English STT + RMS level metering.
 * - Uses native TextToSpeech (low-latency) or synthesized Gemini TTS WAV playback.
 * - Supports immediate barge-in interruption stopping playback & clearing active queues.
 * - Monitors ConnectivityManager to handle network drops and automatic reconnection.
 * - Zero prerecorded audio or simulated speech.
 */
class VoiceSessionController(private val context: Context) {

    companion object {
        private const val TAG = "VoiceSessionCtrl"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    private var onFinalSpeechCallback: ((String) -> Unit)? = null
    private var currentLanguage: AppLanguage = AppLanguage.HINGLISH
    private var sessionActive = false
    private var isMuted = false

    private val _engineState = MutableStateFlow(VoiceEngineState.IDLE)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isOnline = MutableStateFlow(checkCurrentOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    init {
        initTts()
        registerNetworkMonitor()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    applyTtsLocale(currentLanguage)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _engineState.value = VoiceEngineState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                if (sessionActive && !isMuted && _isOnline.value) {
                                    startListeningInternal()
                                } else {
                                    _engineState.value = if (isMuted) VoiceEngineState.MUTED else VoiceEngineState.IDLE
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                _engineState.value = if (sessionActive) VoiceEngineState.IDLE else VoiceEngineState.IDLE
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android TextToSpeech", e)
        }
    }

    private fun applyTtsLocale(language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.HINGLISH, AppLanguage.HINDI -> Locale("hi", "IN")
            AppLanguage.ENGLISH -> Locale("en", "IN")
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
    }

    private fun checkCurrentOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerNetworkMonitor() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    if (sessionActive && _engineState.value == VoiceEngineState.OFFLINE) {
                        _errorBanner.value = null
                        if (!isMuted) {
                            mainHandler.post { startListeningInternal() }
                        } else {
                            _engineState.value = VoiceEngineState.MUTED
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                    if (sessionActive) {
                        stopPlaybackImmediately()
                        mainHandler.post { stopListeningInternal() }
                        _engineState.value = VoiceEngineState.OFFLINE
                        _errorBanner.value = "Network connection lost. Waiting to reconnect with your conversation context intact..."
                    }
                }
            }
            connectivityCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register network monitor: ${e.message}")
        }
    }

    fun startSession(language: AppLanguage, onFinalSpeech: (String) -> Unit) {
        currentLanguage = language
        applyTtsLocale(language)
        onFinalSpeechCallback = onFinalSpeech
        sessionActive = true
        isMuted = false
        _errorBanner.value = null

        if (!_isOnline.value) {
            _engineState.value = VoiceEngineState.OFFLINE
            _errorBanner.value = "Device is offline. Voice conversation will auto-resume when network returns."
            return
        }

        mainHandler.post { startListeningInternal() }
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
        applyTtsLocale(language)
    }

    fun setThinking() {
        _partialTranscript.value = ""
        _rmsLevel.value = 0f
        _engineState.value = VoiceEngineState.THINKING
    }

    /**
     * Immediate barge-in interruption: halts active TTS or WAV playback in < 300ms
     * and transitions straight into live microphone listening.
     */
    fun interruptAndListen() {
        stopPlaybackImmediately()
        _errorBanner.value = null
        if (!sessionActive) return
        if (!_isOnline.value) {
            _engineState.value = VoiceEngineState.OFFLINE
            return
        }
        isMuted = false
        mainHandler.post { startListeningInternal() }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        if (isMuted) {
            stopPlaybackImmediately()
            mainHandler.post { stopListeningInternal() }
            _engineState.value = VoiceEngineState.MUTED
            _rmsLevel.value = 0f
        } else if (sessionActive) {
            if (_isOnline.value) {
                mainHandler.post { startListeningInternal() }
            } else {
                _engineState.value = VoiceEngineState.OFFLINE
            }
        }
        return isMuted
    }

    fun speakText(text: String, language: AppLanguage) {
        currentLanguage = language
        applyTtsLocale(language)
        stopPlaybackImmediately()
        if (!ttsReady || tts == null) {
            _errorBanner.value = "Android Text-to-Speech engine not ready on this device."
            _engineState.value = VoiceEngineState.IDLE
            return
        }
        _engineState.value = VoiceEngineState.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shubham_${System.currentTimeMillis()}")
    }

    fun playWavFile(file: File, onComplete: () -> Unit = {}) {
        stopPlaybackImmediately()
        if (!file.exists() || file.length() == 0L) {
            _errorBanner.value = "Audio file is unavailable for playback."
            return
        }
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _engineState.value = VoiceEngineState.IDLE
                    onComplete()
                    if (sessionActive && !isMuted && _isOnline.value) {
                        mainHandler.post { startListeningInternal() }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    _engineState.value = VoiceEngineState.IDLE
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _engineState.value = VoiceEngineState.SPEAKING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play synthesized WAV file", e)
            _errorBanner.value = "Playback error: ${e.localizedMessage}"
            _engineState.value = VoiceEngineState.IDLE
        }
    }

    fun stopPlaybackImmediately() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {
        }
    }

    fun endSession() {
        sessionActive = false
        isMuted = false
        stopPlaybackImmediately()
        mainHandler.post {
            stopListeningInternal()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        _partialTranscript.value = ""
        _rmsLevel.value = 0f
        _engineState.value = VoiceEngineState.IDLE
    }

    fun release() {
        endSession()
        try {
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityCallback?.let { cm?.unregisterNetworkCallback(it) }
            connectivityCallback = null
        } catch (_: Exception) {
        }
    }

    private fun startListeningInternal() {
        if (!sessionActive || isMuted) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorBanner.value = "Android Speech Recognition service is unavailable on this device. You can still use typed chat or install Google Speech Services."
            _engineState.value = VoiceEngineState.IDLE
            return
        }

        stopPlaybackImmediately()
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _engineState.value = VoiceEngineState.LISTENING
                        _errorBanner.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        // Hardware barge-in safety: ensure playback is halted if user starts talking
                        stopPlaybackImmediately()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _rmsLevel.value = 0f
                        if (!sessionActive || isMuted) return
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Restart listening cleanly for hands-free flow
                                mainHandler.postDelayed({ startListeningInternal() }, 350L)
                            }
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                _engineState.value = VoiceEngineState.OFFLINE
                                _errorBanner.value = "Speech recognition network error. Tap Mic to retry when online."
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                _errorBanner.value = "Microphone permission is required for voice conversation."
                                sessionActive = false
                                _engineState.value = VoiceEngineState.IDLE
                            }
                            else -> {
                                _errorBanner.value = "Speech recognizer error (code $error). Tap Mic to resume."
                                _engineState.value = VoiceEngineState.IDLE
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        _rmsLevel.value = 0f
                        if (spoken.isNotBlank()) {
                            onFinalSpeechCallback?.invoke(spoken)
                        } else if (sessionActive && !isMuted) {
                            mainHandler.postDelayed({ startListeningInternal() }, 300L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            _engineState.value = VoiceEngineState.LISTENING
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _errorBanner.value = "Could not start microphone listener: ${e.localizedMessage}"
            _engineState.value = VoiceEngineState.IDLE
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }
}
