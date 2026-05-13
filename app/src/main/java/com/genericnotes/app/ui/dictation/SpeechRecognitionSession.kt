package com.genericnotes.app.ui.dictation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

internal class SpeechRecognitionSession(
    private val context: Context,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive = false

    fun startListening(callbacks: SpeechRecognitionCallbacks): SpeechRecognitionStartResult {
        release(cancel = true)
        val activeSpeechRecognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse {
            return SpeechRecognitionStartResult.Unavailable
        }

        speechRecognizer = activeSpeechRecognizer
        isActive = true

        runCatching {
            activeSpeechRecognizer.setRecognitionListener(
                DictationRecognitionListener(
                    onReadyForSpeech = {
                        if (isActive) {
                            callbacks.onReadyForSpeech()
                        }
                    },
                    onEndOfSpeech = {
                        if (isActive) {
                            callbacks.onEndOfSpeech()
                        }
                    },
                    onPartialResult = { result ->
                        if (isActive) {
                            callbacks.onPartialResult(result)
                        }
                    },
                    onFinalResult = { result ->
                        if (isActive) {
                            callbacks.onFinalResult(result)
                            release(cancel = false)
                        }
                    },
                    onError = { error ->
                        if (isActive) {
                            callbacks.onErrorMessage(dictationErrorMessageFor(error))
                            release(cancel = false)
                        }
                    },
                ),
            )
        }.onFailure {
            release(cancel = false)
            return SpeechRecognitionStartResult.ListenerError
        }

        return runCatching {
            activeSpeechRecognizer.startListening(createDictationIntent())
            SpeechRecognitionStartResult.Started
        }.getOrElse {
            release(cancel = false)
            SpeechRecognitionStartResult.StartError
        }
    }

    fun stopListening(): SpeechRecognitionStopResult {
        val recognizer = speechRecognizer
        if (recognizer == null || !isActive) {
            release(cancel = false)
            return SpeechRecognitionStopResult.NoActiveSession
        }

        return runCatching {
            recognizer.stopListening()
            SpeechRecognitionStopResult.Stopping
        }.getOrElse {
            release(cancel = true)
            SpeechRecognitionStopResult.StopError
        }
    }

    fun release(cancel: Boolean) {
        val recognizer = speechRecognizer
        speechRecognizer = null
        isActive = false
        if (recognizer == null) return
        if (cancel) {
            runCatching { recognizer.cancel() }
        }
        runCatching { recognizer.destroy() }
    }

    companion object {
        fun isRecognitionAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
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
    Unavailable,
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
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Dictation needs a working speech service."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Try recording again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Dictation is already listening."
        SpeechRecognizer.ERROR_SERVER -> "The speech service could not process the recording."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard. Try recording again."
        else -> "Dictation failed. Try recording again."
    }
