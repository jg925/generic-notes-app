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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

internal class SpeechRecognitionSession(
    private val context: Context,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeSessionId = 0
    private var recordingState = RecordingSessionState.Idle
    private var lastPartialResult: String? = null

    fun startListening(callbacks: SpeechRecognitionCallbacks): SpeechRecognitionStartResult =
        runOnMainThread {
            startListeningOnMainThread(callbacks)
        }

    fun stopListening(): SpeechRecognitionStopResult =
        runOnMainThread {
            stopListeningOnMainThread()
        }

    fun release(cancel: Boolean) {
        runOnMainThread {
            releaseOnMainThread(cancel)
        }
    }

    companion object {
        fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
            runOnMainThread {
                isOnDeviceRecognitionAvailableOnMainThread(context)
            }
    }

    private fun startListeningOnMainThread(
        callbacks: SpeechRecognitionCallbacks,
    ): SpeechRecognitionStartResult {
        releaseOnMainThread(cancel = true)
        if (!isOnDeviceRecognitionAvailableOnMainThread(context)) {
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
                    handleReadyForSpeech = {
                        if (isCurrentSession(sessionId)) {
                            recordingState = RecordingSessionState.Listening
                            callbacks.onReadyForSpeech()
                        }
                    },
                    handleEndOfSpeech = {
                        if (isCurrentSession(sessionId)) {
                            recordingState = RecordingSessionState.Stopping
                            callbacks.onEndOfSpeech()
                        }
                    },
                    handlePartialResult = { result ->
                        if (isCurrentSession(sessionId) &&
                            recordingState == RecordingSessionState.Listening &&
                            result != lastPartialResult
                        ) {
                            lastPartialResult = result
                            callbacks.onPartialResult(result)
                        }
                    },
                    handleFinalResult = { result ->
                        if (isCurrentSession(sessionId)) {
                            callbacks.onFinalResult(result)
                            releaseAfterCallback(sessionId, cancel = false)
                        }
                    },
                    handleError = { error ->
                        if (isCurrentSession(sessionId)) {
                            callbacks.onErrorMessage(dictationErrorMessageFor(error))
                            releaseAfterCallback(sessionId, cancel = false)
                        }
                    },
                ),
            )
        }.onFailure {
            releaseOnMainThread(cancel = false)
            return SpeechRecognitionStartResult.ListenerError
        }

        return runCatching {
            recordingState = RecordingSessionState.Listening
            activeSpeechRecognizer.startListening(createDictationIntent())
            SpeechRecognitionStartResult.Started
        }.getOrElse {
            releaseOnMainThread(cancel = false)
            SpeechRecognitionStartResult.StartError
        }
    }

    private fun stopListeningOnMainThread(): SpeechRecognitionStopResult {
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
            releaseOnMainThread(cancel = true)
            SpeechRecognitionStopResult.StopError
        }
    }

    private fun releaseOnMainThread(cancel: Boolean) {
        val recognizer = detachRecognizer()
        if (recognizer == null) return
        destroyRecognizer(recognizer, cancel)
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
        postOnMainThread {
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

private val speechRecognizerMainHandler = Handler(Looper.getMainLooper())

private fun isOnDeviceRecognitionAvailableOnMainThread(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

private fun <T> runOnMainThread(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return block()
    }

    val result = AtomicReference<T>()
    val failure = AtomicReference<Throwable>()
    val latch = CountDownLatch(1)
    val posted = speechRecognizerMainHandler.post {
        try {
            result.set(block())
        } catch (throwable: Throwable) {
            failure.set(throwable)
        } finally {
            latch.countDown()
        }
    }

    check(posted) { "Unable to dispatch speech recognition work to the main thread." }
    try {
        latch.await()
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IllegalStateException("Interrupted while waiting for main-thread speech recognition work.", exception)
    }
    failure.get()?.let { throw it }
    return result.get()
}

private fun postOnMainThread(block: () -> Unit) {
    val posted = speechRecognizerMainHandler.post(block)
    check(posted) { "Unable to dispatch speech recognition cleanup to the main thread." }
}

private fun callOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        postOnMainThread(block)
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
    private val handleReadyForSpeech: () -> Unit,
    private val handleEndOfSpeech: () -> Unit,
    private val handlePartialResult: (String) -> Unit,
    private val handleFinalResult: (String) -> Unit,
    private val handleError: (Int) -> Unit,
) : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {
        callOnMainThread {
            handleReadyForSpeech()
        }
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        callOnMainThread {
            handleEndOfSpeech()
        }
    }

    override fun onError(error: Int) {
        callOnMainThread {
            handleError(error)
        }
    }

    override fun onResults(results: Bundle?) {
        callOnMainThread {
            val result = results.firstRecognitionResult()
            if (result == null) {
                handleError(SpeechRecognizer.ERROR_NO_MATCH)
            } else {
                handleFinalResult(result)
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        callOnMainThread {
            partialResults.firstRecognitionResult()?.let(handlePartialResult)
        }
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
