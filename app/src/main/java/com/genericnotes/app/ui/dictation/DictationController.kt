package com.genericnotes.app.ui.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.Instant

internal class DictationController(
    val state: DictationUiState,
    val onDraftChange: (String) -> Unit,
    val openSheet: () -> Unit,
    val beginDictation: (clearDraft: Boolean) -> Unit,
    val stopDictation: () -> Unit,
    val saveDraft: () -> Unit,
    val closeSheet: () -> Unit,
)

@Composable
internal fun rememberDictationController(resetKey: Any?): DictationController {
    val context = LocalContext.current
    var isSheetVisible by remember(resetKey) { mutableStateOf(false) }
    var draftText by remember(resetKey) { mutableStateOf("") }
    var savedUnderstanding by remember(resetKey) { mutableStateOf<DictationUnderstanding?>(null) }
    var status by remember(resetKey) { mutableStateOf(DictationStatus.Idle) }
    var errorMessage by remember(resetKey) { mutableStateOf<String?>(null) }
    var clearDraftAfterPermissionGrant by remember(resetKey) { mutableStateOf(false) }
    val onDeviceSpeechRecognitionAvailable = remember(context) {
        SpeechRecognitionSession.isOnDeviceRecognitionAvailable(context)
    }
    val speechRecognitionSession = remember(context, resetKey) {
        SpeechRecognitionSession(context)
    }

    DisposableEffect(speechRecognitionSession) {
        onDispose {
            speechRecognitionSession.release(cancel = true)
        }
    }

    fun startDictationNow(clearDraft: Boolean) {
        if (clearDraft) {
            draftText = ""
        }
        errorMessage = null
        status = DictationStatus.Listening

        when (
            speechRecognitionSession.startListening(
                SpeechRecognitionCallbacks(
                    onReadyForSpeech = {
                        status = DictationStatus.Listening
                        errorMessage = null
                    },
                    onEndOfSpeech = {
                        status = DictationStatus.Processing
                    },
                    onPartialResult = { result ->
                        draftText = result
                    },
                    onFinalResult = { result ->
                        draftText = result
                        status = DictationStatus.Idle
                        errorMessage = null
                    },
                    onErrorMessage = { message ->
                        status = DictationStatus.Error
                        errorMessage = message
                    },
                ),
            )
        ) {
            SpeechRecognitionStartResult.Started -> Unit
            SpeechRecognitionStartResult.OnDeviceUnavailable -> {
                status = DictationStatus.Unavailable
                errorMessage = "On-device dictation is not available on this device."
            }
            SpeechRecognitionStartResult.ListenerError -> {
                status = DictationStatus.Error
                errorMessage = "Dictation could not listen for results."
            }
            SpeechRecognitionStartResult.StartError -> {
                status = DictationStatus.Error
                errorMessage = "Dictation could not start recording."
            }
        }
    }

    val requestAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDictationNow(clearDraft = clearDraftAfterPermissionGrant)
        } else {
            clearDraftAfterPermissionGrant = false
            status = DictationStatus.PermissionDenied
            errorMessage = "Microphone permission is required for dictation."
        }
    }

    fun openDictationSheet() {
        speechRecognitionSession.release(cancel = true)
        draftText = savedUnderstanding?.plainText.orEmpty()
        status = DictationStatus.Idle
        errorMessage = null
        clearDraftAfterPermissionGrant = false
        isSheetVisible = true
    }

    fun beginDictation(clearDraft: Boolean) {
        isSheetVisible = true
        if (!onDeviceSpeechRecognitionAvailable) {
            status = DictationStatus.Unavailable
            errorMessage = "On-device dictation is not available on this device."
            return
        }

        if (!context.hasRecordAudioPermission()) {
            clearDraftAfterPermissionGrant = clearDraft
            status = DictationStatus.RequestingPermission
            errorMessage = null
            runCatching {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }.onFailure {
                clearDraftAfterPermissionGrant = false
                status = DictationStatus.Error
                errorMessage = "Microphone permission could not be requested."
            }
            return
        }

        clearDraftAfterPermissionGrant = false
        startDictationNow(clearDraft = clearDraft)
    }

    fun stopDictation() {
        when (speechRecognitionSession.stopListening()) {
            SpeechRecognitionStopResult.NoActiveSession -> {
                status = DictationStatus.Idle
                errorMessage = null
            }
            SpeechRecognitionStopResult.Stopping -> {
                status = DictationStatus.Processing
            }
            SpeechRecognitionStopResult.StopError -> {
                status = DictationStatus.Error
                errorMessage = "Dictation could not stop recording cleanly."
            }
        }
    }

    fun closeDictationSheet() {
        speechRecognitionSession.release(cancel = true)
        draftText = savedUnderstanding?.plainText.orEmpty()
        status = DictationStatus.Idle
        errorMessage = null
        clearDraftAfterPermissionGrant = false
        isSheetVisible = false
    }

    fun saveDictationDraft() {
        val plainText = draftText.trim()
        if (plainText.isBlank()) return

        speechRecognitionSession.release(cancel = true)
        savedUnderstanding = DictationUnderstanding(
            plainText = plainText,
            generatedAt = Instant.now(),
        )
        draftText = plainText
        status = DictationStatus.Saved
        errorMessage = null
        clearDraftAfterPermissionGrant = false
        isSheetVisible = false
    }

    return DictationController(
        state = DictationUiState(
            isSheetVisible = isSheetVisible,
            draftText = draftText,
            savedUnderstanding = savedUnderstanding,
            status = status,
            errorMessage = errorMessage,
        ),
        onDraftChange = { draftText = it },
        openSheet = ::openDictationSheet,
        beginDictation = ::beginDictation,
        stopDictation = ::stopDictation,
        saveDraft = ::saveDictationDraft,
        closeSheet = ::closeDictationSheet,
    )
}

private fun Context.hasRecordAudioPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
