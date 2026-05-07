package dev.blazelight.p4oc.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.blazelight.p4oc.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener, RecognitionListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false

    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        if (tts == null) {
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AppLog.e(TAG, "TTS Language not supported")
            } else {
                isTtsInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _voiceState.update { it.copy(isSpeaking = true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        _voiceState.update { it.copy(isSpeaking = false) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _voiceState.update { it.copy(isSpeaking = false) }
                        AppLog.e(TAG, "TTS Error")
                    }
                })
            }
        } else {
            AppLog.e(TAG, "TTS Initialization failed")
        }
    }

    // --- TTS ---

    fun speak(text: String, flush: Boolean = false) {
        if (!isTtsInitialized || text.isBlank()) return

        // Use streaming audio focus if available
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        val params = Bundle()
        // Optional: request audio focus if needed via AudioAttributes in newer APIs

        tts?.speak(text, queueMode, params, "utterance_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
            _voiceState.update { it.copy(isSpeaking = false) }
        }
    }

    // --- STT ---

    fun startListening() {
        // If speaking, stop it
        stopSpeaking()

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Optional: You could request specific audio source if using bluetooth headsets
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error starting speech recognizer: ${e.message}", e)
            _voiceState.update { it.copy(error = e.message, isListening = false) }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun cancelListening() {
        speechRecognizer?.cancel()
        _voiceState.update { it.copy(isListening = false, partialText = "") }
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _voiceState.update { it.copy(isListening = true, error = null, partialText = "") }
    }

    override fun onBeginningOfSpeech() {
        // User started speaking
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Sound level
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
        // User stopped speaking, waiting for final result
        _voiceState.update { it.copy(isListening = false) }
    }

    override fun onError(error: Int) {
        val errorMessage = getErrorText(error)
        AppLog.e(TAG, "Speech recognition error: $errorMessage")
        _voiceState.update { it.copy(isListening = false, error = errorMessage, partialText = "") }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            _voiceState.update {
                it.copy(
                    isListening = false,
                    partialText = "",
                    finalResult = text
                )
            }
        } else {
            _voiceState.update { it.copy(isListening = false, partialText = "") }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            _voiceState.update { it.copy(partialText = text) }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }

    fun clearResult() {
        _voiceState.update { it.copy(finalResult = null) }
    }

    fun clearError() {
        _voiceState.update { it.copy(error = null) }
    }

    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        tts = null

        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Didn't understand, please try again."
        }
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}

data class VoiceState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialText: String = "",
    val finalResult: String? = null,
    val error: String? = null
)
