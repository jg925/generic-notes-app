package com.genericnotes.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.genericnotes.app.canvas.DrawingTool
import com.genericnotes.app.canvas.InkCanvasView
import com.genericnotes.app.hwdn.HwdnDocument
import com.genericnotes.app.hwdn.HwdnMimeType
import com.genericnotes.app.hwdn.MaxFileNameLength
import com.genericnotes.app.hwdn.exportHwdnPackage
import com.genericnotes.app.hwdn.toHwdnFileName
import com.genericnotes.app.hwdn.withoutHwdnExtension

@Composable
internal fun NotesCanvasScreen(
    initialDocument: HwdnDocument?,
    onDocumentSaved: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    var isLocked by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(DrawingTool.Pen) }
    var fileName by remember(initialDocument) {
        mutableStateOf(initialDocument?.fileName?.withoutHwdnExtension()?.take(MaxFileNameLength) ?: "Untitled Note")
    }
    var inkCanvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var pendingDocumentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
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
                    initialDocument?.strokes?.let(canvasView::loadStrokes)
                    inkCanvasView = canvasView
                }
            },
            update = { inkCanvas ->
                inkCanvas.isLocked = isLocked
                inkCanvas.selectedTool = selectedTool
            },
            modifier = Modifier.fillMaxSize(),
        )

        FilePanel(
            fileName = fileName,
            onFileNameChange = { fileName = it.withoutHwdnExtension().take(MaxFileNameLength) },
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

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            color = Color(0xFFF4F4F4),
            contentColor = Color(0xFF111111),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(
                    icon = PenIcon,
                    contentDescription = "Pen",
                    selected = selectedTool == DrawingTool.Pen,
                    onClick = { selectedTool = DrawingTool.Pen },
                )
                ToolButton(
                    icon = EraserIcon,
                    contentDescription = "Eraser",
                    selected = selectedTool == DrawingTool.Eraser,
                    onClick = { selectedTool = DrawingTool.Eraser },
                )
            }
        }

        Button(
            onClick = { isLocked = !isLocked },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLocked) Color(0xFF111111) else Color(0xFFECECEC),
                contentColor = if (isLocked) Color.White else Color(0xFF111111),
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isLocked) "Unlock edits" else "Lock edits")
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) Color(0xFF111111) else Color.Transparent,
            contentColor = if (selected) Color.White else Color(0xFF111111),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}
