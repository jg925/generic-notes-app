package com.genericnotes.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.genericnotes.app.hwdn.HwdnDocument
import com.genericnotes.app.hwdn.HwdnOpenMimeTypes
import com.genericnotes.app.hwdn.readHwdnDocument

@Composable
internal fun HwdnApp() {
    val context = LocalContext.current
    var isEditorOpen by remember { mutableStateOf(false) }
    var initialDocument by remember { mutableStateOf<HwdnDocument?>(null) }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            context.readHwdnDocument(uri)
        }.onSuccess { document ->
            initialDocument = document
            isEditorOpen = true
        }.onFailure {
            Toast.makeText(context, "Could not open .hwdn file", Toast.LENGTH_SHORT).show()
        }
    }

    if (isEditorOpen) {
        NotesCanvasScreen(initialDocument = initialDocument)
    } else {
        OpenOrCreateScreen(
            onOpenExisting = { openDocumentLauncher.launch(HwdnOpenMimeTypes) },
            onCreateNew = {
                initialDocument = null
                isEditorOpen = true
            },
        )
    }
}
