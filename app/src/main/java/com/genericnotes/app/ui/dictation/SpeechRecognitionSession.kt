package com.genericnotes.app.ui.dictation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

internal class SpeechRecognitionSession(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeSessionId = 0
    private var recordingState = RecordingSessionState.Idle
    private var lastPartialResult: String? = null

    fun startListening(callbacks: SpeechRecognitionCallbacks): SpeechRecognitionStartResult {
        release(cancel = true)
        if (!isOnDeviceRecognitionAvailable(context)) {
            return SpeechRecognitionStartResult.OnDeviceUnavailable
        }

        val sessionId = nextSessionId()
        val activeSpeechRecognizer = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }.getOrElse {
            return SpeechRecognitionStartResult.OnDeviceUnavailable
        }

        speechRecognizer = activeSpeechRecognizer
        recordingState = RecordingSessionState.Starting

        runCatching {
            activeSpeechRecognizer.setRecognitionListener(
                DictationRecognitionListener(
                    onReadyForSpeech = {
                        if (isCurrentSession(sessionId)) {
                            recordingState = RecordingSessionState.Listening
                            callbacks.onReadyForSpeech()
                        }
                    },
                    onEndOfSpeech = {
                        if (isCurrentSession(sessionId)) {
                            recordingState = RecordingSessionState.Stopping
                            callbacks.onEndOfSpeech()
                        }
                    },
                    onPartialResult = { result ->
                        if (isCurrentSession(sessionId) &&
                            recordingState == RecordingSessionState.Listening &&
                            result != lastPartialResult
                        ) {
                            lastPartialResult = result
                            callbacks.onPartialResult(result)
                        }
                    },
                    onFinalResult = { result ->
                        if (isCurrentSession(sessionId)) {
                            callbacks.onFinalResult(result)
                            releaseAfterCallback(sessionId, cancel = false)
                        }
                    },
                    onError = { error ->
                        if (isCurrentSession(sessionId)) {
                            callbacks.onErrorMessage(dictationErrorMessageFor(error))
                            releaseAfterCallback(sessionId, cancel = false)
                        }
                    },
                ),
            )
        }.onFailure {
            release(cancel = false)
            return SpeechRecognitionStartResult.ListenerError
        }

        return runCatching {
            recordingState = RecordingSessionState.Listening
            activeSpeechRecognizer.startListening(createDictationIntent())
            SpeechRecognitionStartResult.Started
        }.getOrElse {
            release(cancel = false)
            SpeechRecognitionStartResult.StartError
        }
    }

    fun stopListening(): SpeechRecognitionStopResult {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            return SpeechRecognitionStopResult.NoActiveSession
        }
        if (recordingState == RecordingSessionState.Stopping) {
            return SpeechRecognitionStopResult.Stopping
        }
        if (recordingState != RecordingSessionState.Listening) {
            return SpeechRecognitionStopResult.NoActiveSession
        }

        return runCatching {
            recordingState = RecordingSessionState.Stopping
            recognizer.stopListening()
            SpeechRecognitionStopResult.Stopping
        }.getOrElse {
            release(cancel = true)
            SpeechRecognitionStopResult.StopError
        }
    }

    fun release(cancel: Boolean) {
        val recognizer = detachRecognizer()
        if (recognizer == null) return
        destroyRecognizer(recognizer, cancel)
    }

    companion object {
        fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    private fun nextSessionId(): Int {
        activeSessionId += 1
        return activeSessionId
    }

    private fun isCurrentSession(sessionId: Int): Boolean =
        speechRecognizer != null &&
            activeSessionId == sessionId &&
            recordingState != RecordingSessionState.Released

    private fun releaseAfterCallback(sessionId: Int, cancel: Boolean) {
        if (!isCurrentSession(sessionId)) return
        val recognizer = detachRecognizer()
        if (recognizer == null) return
        mainHandler.post {
            destroyRecognizer(recognizer, cancel)
        }
    }

    private fun detachRecognizer(): SpeechRecognizer? {
        val recognizer = speechRecognizer
        speechRecognizer = null
        recordingState = RecordingSessionState.Released
        lastPartialResult = null
        activeSessionId += 1
        return recognizer
    }

    private fun destroyRecognizer(recognizer: SpeechRecognizer, cancel: Boolean) {
        if (cancel) {
            runCatching { recognizer.cancel() }
        }
        runCatching { recognizer.destroy() }
    }
}

private enum class RecordingSessionState {
    Idle,
    Starting,
    Listening,
    Stopping,
    Released,
}

internal data class SpeechRecognitionCallbacks(
    val onReadyForSpeech: () -> Unit,
    val onEndOfSpeech: () -> Unit,
    val onPartialResult: (String) -> Unit,
    val onFinalResult: (String) -> Unit,
    val onErrorMessage: (String) -> Unit,
)

internal enum class SpeechRecognitionStartResult {
    Started,
    OnDeviceUnavailable,
    ListenerError,
    StartError,
}

internal enum class SpeechRecognitionStopResult {
    NoActiveSession,
    Stopping,
    StopError,
}

private class DictationRecognitionListener(
    private val onReadyForSpeech: () -> Unit,
    private val onEndOfSpeech: () -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (Int) -> Unit,
) : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {
        onReadyForSpeech()
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        onEndOfSpeech()
    }

    override fun onError(error: Int) {
        onError(error)
    }

    override fun onResults(results: Bundle?) {
        val result = results.firstRecognitionResult()
        if (result == null) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
        } else {
            onFinalResult(result)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults.firstRecognitionResult()?.let(onPartialResult)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private fun createDictationIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

private fun Bundle?.firstRecognitionResult(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun dictationErrorMessageFor(error: Int): String =
    when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Could not read microphone audio."
        SpeechRecognizer.ERROR_CLIENT -> "Dictation stopped."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for dictation."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "On-device dictation could not process the recording."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Try recording again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Dictation is already listening."
        SpeechRecognizer.ERROR_SERVER -> "On-device dictation could not process the recording."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard. Try recording again."
        else -> "Dictation failed. Try recording again."
    }
