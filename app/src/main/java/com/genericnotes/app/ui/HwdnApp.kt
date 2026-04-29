package com.genericnotes.app.ui

import android.content.Intent
import android.net.Uri
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
import com.genericnotes.app.hwdn.forgetRecentHwdnFile
import com.genericnotes.app.hwdn.loadRecentHwdnFiles
import com.genericnotes.app.hwdn.rememberRecentHwdnFile
import com.genericnotes.app.hwdn.readHwdnDocument

@Composable
internal fun HwdnApp() {
    val context = LocalContext.current
    var isEditorOpen by remember { mutableStateOf(false) }
    var initialDocument by remember { mutableStateOf<HwdnDocument?>(null) }
    var recentFiles by remember { mutableStateOf(context.loadRecentHwdnFiles()) }

    fun refreshRecentFiles() {
        recentFiles = context.loadRecentHwdnFiles()
    }

    fun persistUriPermission(uri: Uri, permissionFlags: Int) {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
        }.isSuccess

        if (!persisted && permissionFlags != Intent.FLAG_GRANT_READ_URI_PERMISSION) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun rememberRecentFile(uri: Uri, displayName: String) {
        context.rememberRecentHwdnFile(uri, displayName)
        refreshRecentFiles()
    }

    fun openDocumentUri(uri: Uri, persistPermission: Boolean) {
        runCatching {
            context.readHwdnDocument(uri)
        }.onSuccess { document ->
            if (persistPermission) {
                persistUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            rememberRecentFile(uri, document.fileName)
            initialDocument = document
            isEditorOpen = true
        }.onFailure {
            if (!persistPermission) {
                context.forgetRecentHwdnFile(uri)
                refreshRecentFiles()
            }

            Toast.makeText(
                context,
                if (persistPermission) "Could not open .hwdn file" else "Could not reopen recent file",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        openDocumentUri(uri, persistPermission = true)
    }

    if (isEditorOpen) {
        NotesCanvasScreen(
            initialDocument = initialDocument,
            onDocumentSaved = { uri, documentName ->
                persistUriPermission(
                    uri = uri,
                    permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                rememberRecentFile(uri, documentName)
            },
        )
    } else {
        OpenOrCreateScreen(
            onOpenExisting = { openDocumentLauncher.launch(HwdnOpenMimeTypes) },
            onCreateNew = {
                initialDocument = null
                isEditorOpen = true
            },
            recentFiles = recentFiles,
            onOpenRecent = { recentFile ->
                openDocumentUri(recentFile.uri, persistPermission = false)
            },
        )
    }
}
