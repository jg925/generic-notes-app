package com.genericnotes.app.ui.dictation

import java.time.Instant

internal data class DictationUnderstanding(
    val plainText: String,
    val generatedAt: Instant,
)

internal enum class DictationStatus(val displayText: String) {
    Idle("Ready"),
    RequestingPermission("Permission"),
    Listening("Listening"),
    Processing("Processing"),
    Saved("Saved"),
    Error("Error"),
    PermissionDenied("Permission denied"),
    Unavailable("Unavailable"),
}

internal data class DictationUiState(
    val isSheetVisible: Boolean,
    val draftText: String,
    val savedUnderstanding: DictationUnderstanding?,
    val status: DictationStatus,
    val errorMessage: String?,
)
