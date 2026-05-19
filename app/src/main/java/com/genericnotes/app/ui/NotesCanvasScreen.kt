package com.genericnotes.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.genericnotes.app.canvas.InkCanvasView
import com.genericnotes.app.hwdn.HwdnDocument
import com.genericnotes.app.hwdn.HwdnMimeType
import com.genericnotes.app.hwdn.MaxFileNameLength
import com.genericnotes.app.hwdn.exportHwdnPackage
import com.genericnotes.app.hwdn.toHwdnFileName
import com.genericnotes.app.hwdn.withoutHwdnExtension
import com.genericnotes.app.settings.AppCanvasSettings
import com.genericnotes.app.settings.loadAppCanvasSettings
import com.genericnotes.app.settings.saveAppCanvasSettings
import com.genericnotes.app.ui.dictation.DictationPreviewSheet
import com.genericnotes.app.ui.dictation.rememberDictationController

@Composable
internal fun NotesCanvasScreen(
    initialDocument: HwdnDocument?,
    accentColor: Color,
    onDocumentSaved: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val supportsTrueStylusInput = rememberSupportsTrueStylusInput()
    val appCanvasSettings = remember(context) { context.loadAppCanvasSettings() }
    var isLocked by remember(context) { mutableStateOf(appCanvasSettings.isLocked) }
    var selectedTool by remember(context) { mutableStateOf(appCanvasSettings.selectedTool) }
    var ignoreTouchInput by remember(context) { mutableStateOf(appCanvasSettings.ignoreTouchInput) }
    val shouldIgnoreTouchInput = supportsTrueStylusInput && ignoreTouchInput
    var fileName by remember(initialDocument) {
        mutableStateOf(initialDocument?.fileName?.withoutHwdnExtension()?.take(MaxFileNameLength) ?: "")
    }
    var canUndo by remember(initialDocument) { mutableStateOf(initialDocument?.strokes?.isNotEmpty() == true) }
    var canResetZoom by remember(initialDocument) { mutableStateOf(false) }
    var canRedo by remember(initialDocument) { mutableStateOf(false) }
    var inkCanvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var pendingDocumentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    val dictationController = rememberDictationController(resetKey = initialDocument)
    val dictationState = dictationController.state

    LaunchedEffect(context, isLocked, selectedTool, ignoreTouchInput) {
        context.saveAppCanvasSettings(
            AppCanvasSettings(
                isLocked = isLocked,
                selectedTool = selectedTool,
                ignoreTouchInput = ignoreTouchInput,
            ),
        )
    }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(HwdnMimeType),
    ) { uri ->
        val documentBytes = pendingDocumentBytes
        val documentName = pendingFileName
        pendingDocumentBytes = null
        pendingFileName = null

        if (uri == null || documentBytes == null || documentName == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(documentBytes)
            } ?: error("Unable to open save destination.")
        }.onSuccess {
            onDocumentSaved(uri, documentName)
            Toast.makeText(context, "Saved $documentName", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        AndroidView(
            factory = { viewContext ->
                InkCanvasView(viewContext).also { canvasView ->
                    canvasView.onCanUndoChanged = { canUndo = it }
                    canvasView.onCanResetZoomChanged = { canResetZoom = it }
                    canvasView.onCanRedoChanged = { canRedo = it }
                    initialDocument?.strokes?.let(canvasView::loadStrokes)
                    inkCanvasView = canvasView
                }
            },
            update = { inkCanvas ->
                inkCanvas.isLocked = isLocked
                inkCanvas.selectedTool = selectedTool
                inkCanvas.ignoreTouchInput = shouldIgnoreTouchInput
            },
            modifier = Modifier.fillMaxSize(),
        )

        FilePanel(
            fileName = fileName,
            onFileNameChange = { fileName = it.withoutHwdnExtension().take(MaxFileNameLength) },
            accentColor = accentColor,
            onSave = {
                val hwdnFileName = fileName.toHwdnFileName()
                val canvasView = inkCanvasView ?: return@FilePanel
                pendingDocumentBytes = exportHwdnPackage(
                    strokes = canvasView.strokesSnapshot(),
                    fileName = hwdnFileName,
                    canvasWidth = canvasView.width,
                    canvasHeight = canvasView.height,
                )
                pendingFileName = hwdnFileName
                saveDocumentLauncher.launch(hwdnFileName)
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        )

        NotesCanvasToolbar(
            canUndo = canUndo,
            canRedo = canRedo,
            canResetZoom = canResetZoom,
            selectedTool = selectedTool,
            supportsTrueStylusInput = supportsTrueStylusInput,
            ignoreTouchInput = ignoreTouchInput,
            accentColor = accentColor,
            onUndo = {
                inkCanvasView?.let { canvasView ->
                    canvasView.undoLastStroke()
                    canUndo = canvasView.canUndo
                }
            },
            onRedo = {
                inkCanvasView?.let { canvasView ->
                    canvasView.redoLastStroke()
                    canRedo = canvasView.canRedo
                }
            },
            onResetZoom = { inkCanvasView?.resetZoomToFullScreen() },
            onSelectTool = { selectedTool = it },
            onTogglePalmReject = { ignoreTouchInput = !ignoreTouchInput },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .offset(x = 64.dp),
        )

        Button(
            onClick = { isLocked = !isLocked },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLocked) accentColor else Color(0xFFECECEC),
                contentColor = if (isLocked) Color.White else accentColor,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isLocked) "Unlock edits" else "Lock edits")
        }

        if (!dictationState.isSheetVisible) {
            InterpretationActionButton(
                accentColor = accentColor,
                onClick = dictationController.openSheet,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .navigationBarsPadding(),
            )
        }

        if (dictationState.isSheetVisible) {
            DictationPreviewSheet(
                state = dictationState,
                accentColor = accentColor,
                onDraftChange = dictationController.onDraftChange,
                onRecordAgain = { dictationController.beginDictation(true) },
                onStopListening = dictationController.stopDictation,
                onSave = dictationController.saveDraft,
                onCancel = dictationController.closeSheet,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun InterpretationActionButton(
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MicrophoneIcon,
                contentDescription = "set interpretation",
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Set Interpretation",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
