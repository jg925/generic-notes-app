package com.genericnotes.app.ui

import android.hardware.input.InputManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.genericnotes.app.canvas.DrawingTool
import com.genericnotes.app.canvas.InkCanvasView
import com.genericnotes.app.canvas.NotePageLayout
import com.genericnotes.app.canvas.PageDisplayMode
import com.genericnotes.app.canvas.PageScrollDirection
import com.genericnotes.app.hwdn.HwdnDocument
import com.genericnotes.app.hwdn.HwdnMimeType
import com.genericnotes.app.hwdn.MaxFileNameLength
import com.genericnotes.app.hwdn.exportHwdnPackage
import com.genericnotes.app.hwdn.toHwdnFileName
import com.genericnotes.app.hwdn.withoutHwdnExtension
import com.genericnotes.app.settings.AppCanvasSettings
import com.genericnotes.app.settings.loadAppCanvasSettings
import com.genericnotes.app.settings.saveAppCanvasSettings

@Composable
internal fun NotesCanvasScreen(
    initialDocument: HwdnDocument?,
    onDocumentSaved: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val supportsTrueStylusInput = rememberSupportsTrueStylusInput()
    val appCanvasSettings = remember(context) { context.loadAppCanvasSettings() }
    var isLocked by remember(context) { mutableStateOf(appCanvasSettings.isLocked) }
    var selectedTool by remember(context) { mutableStateOf(appCanvasSettings.selectedTool) }
    var ignoreTouchInput by remember(context) { mutableStateOf(appCanvasSettings.ignoreTouchInput) }
    val shouldIgnoreTouchInput = supportsTrueStylusInput && ignoreTouchInput
    val initialPageLayout = initialDocument?.pageLayout ?: NotePageLayout()
    var pageCount by remember(initialDocument) {
        mutableStateOf(initialPageLayout.normalizedPageCount)
    }
    var pageScrollDirection by remember(initialDocument) {
        mutableStateOf(initialPageLayout.scrollDirection)
    }
    var preferredPageDirection by remember(initialDocument) {
        mutableStateOf(initialPageLayout.scrollDirection ?: PageScrollDirection.Vertical)
    }
    var pageDisplayMode by remember(initialDocument) {
        mutableStateOf(initialPageLayout.displayMode)
    }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var previousPageCount by remember(initialDocument) { mutableStateOf(pageCount) }
    var fileName by remember(initialDocument) {
        mutableStateOf(initialDocument?.fileName?.withoutHwdnExtension()?.take(MaxFileNameLength) ?: "")
    }
    var canUndo by remember(initialDocument) { mutableStateOf(initialDocument?.strokes?.isNotEmpty() == true) }
    var canResetZoom by remember(initialDocument) { mutableStateOf(false) }
    var canRedo by remember(initialDocument) { mutableStateOf(false) }
    var inkCanvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var pendingDocumentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(pageCount, pageScrollDirection) {
        if (pageCount > previousPageCount) {
            when (pageScrollDirection ?: preferredPageDirection) {
                PageScrollDirection.Vertical -> verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                PageScrollDirection.Horizontal -> horizontalScrollState.animateScrollTo(horizontalScrollState.maxValue)
            }
        }
        previousPageCount = pageCount
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE9E9E9))
    ) {
        val density = LocalDensity.current
        val savedPageWidth = initialPageLayout.pageWidthPx
            ?.takeIf { it > 0 }
            ?.let { with(density) { it.toDp() } }
        val savedPageHeight = initialPageLayout.pageHeightPx
            ?.takeIf { it > 0 }
            ?.let { with(density) { it.toDp() } }
        val pageWidth = savedPageWidth ?: maxWidth
        val pageHeight = savedPageHeight ?: maxHeight
        val renderedDirection = pageScrollDirection ?: preferredPageDirection
        val canvasWidth = if (renderedDirection == PageScrollDirection.Horizontal) {
            pageWidth * pageCount.toFloat()
        } else {
            pageWidth
        }
        val canvasHeight = if (renderedDirection == PageScrollDirection.Vertical) {
            pageHeight * pageCount.toFloat()
        } else {
            pageHeight
        }
        val renderedPageLayout = NotePageLayout(
            pageCount = pageCount,
            scrollDirection = pageScrollDirection,
            displayMode = pageDisplayMode,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when (renderedDirection) {
                        PageScrollDirection.Vertical -> Modifier.verticalScroll(verticalScrollState)
                        PageScrollDirection.Horizontal -> Modifier.horizontalScroll(horizontalScrollState)
                    },
                ),
        ) {
            AndroidView(
                factory = { viewContext ->
                    InkCanvasView(viewContext).also { canvasView ->
                        canvasView.pageLayout = renderedPageLayout
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
                    inkCanvas.pageLayout = renderedPageLayout
                },
                modifier = Modifier.size(width = canvasWidth, height = canvasHeight),
            )
        }

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
                    pageLayout = NotePageLayout(
                        pageCount = pageCount,
                        scrollDirection = pageScrollDirection,
                        displayMode = pageDisplayMode,
                    ),
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
                    icon = UndoIcon,
                    contentDescription = "undo",
                    selected = false,
                    enabled = canUndo,
                    onClick = {
                        inkCanvasView?.let { canvasView ->
                            canvasView.undoLastStroke()
                            canUndo = canvasView.canUndo
                        }
                    },
                )
                ToolButton(
                    icon = RedoIcon,
                    contentDescription = "redo",
                    selected = false,
                    enabled = canRedo,
                    onClick = {
                        inkCanvasView?.let { canvasView ->
                            canvasView.redoLastStroke()
                            canRedo = canvasView.canRedo
                        }
                    },
                )
                ToolButton(
                    icon = FitScreenIcon,
                    contentDescription = "reset zoom",
                    selected = false,
                    enabled = canResetZoom,
                    onClick = { inkCanvasView?.resetZoomToFullScreen() },
                )
                ToolButton(
                    icon = PenIcon,
                    contentDescription = "pen",
                    selected = selectedTool == DrawingTool.Pen,
                    onClick = { selectedTool = DrawingTool.Pen },
                )
                ToolButton(
                    icon = EraserIcon,
                    contentDescription = "eraser",
                    selected = selectedTool == DrawingTool.Eraser,
                    onClick = { selectedTool = DrawingTool.Eraser },
                )
                if (supportsTrueStylusInput) {
                    ToolButton(
                        icon = HandIcon,
                        contentDescription = "palm reject",
                        selected = ignoreTouchInput,
                        onClick = { ignoreTouchInput = !ignoreTouchInput },
                        struckThrough = ignoreTouchInput,
                    )
                }
            }
        }

        PageControls(
            pageCount = pageCount,
            preferredDirection = preferredPageDirection,
            lockedDirection = pageScrollDirection,
            displayMode = pageDisplayMode,
            onPreferredDirectionChange = { direction ->
                if (pageScrollDirection == null) {
                    preferredPageDirection = direction
                }
            },
            onDisplayModeChange = { pageDisplayMode = it },
            onAddPage = {
                if (pageScrollDirection == null) {
                    pageScrollDirection = preferredPageDirection
                }
                pageCount += 1
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )

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
private fun rememberSupportsTrueStylusInput(): Boolean {
    val context = LocalContext.current
    var supportsTrueStylusInput by remember(context) { mutableStateOf(context.supportsTrueStylusInput()) }

    DisposableEffect(context) {
        val inputManager = context.getSystemService(InputManager::class.java)
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                supportsTrueStylusInput = context.supportsTrueStylusInput()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                supportsTrueStylusInput = context.supportsTrueStylusInput()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                supportsTrueStylusInput = context.supportsTrueStylusInput()
            }
        }

        inputManager?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            inputManager?.unregisterInputDeviceListener(listener)
        }
    }

    return supportsTrueStylusInput
}

private fun android.content.Context.supportsTrueStylusInput(): Boolean =
    InputDevice.getDeviceIds().any { deviceId ->
        InputDevice.getDevice(deviceId)?.let { inputDevice ->
            inputDevice.supportsSource(InputDevice.SOURCE_STYLUS) ||
                inputDevice.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS)
        } == true
    }

// TODO: when a scroll direction is locked, the icon should still show up, the other option is removed.
// The number of pages should be a fraction showing which page is currently mostly on screen / total.
@Composable
private fun PageControls(
    pageCount: Int,
    preferredDirection: PageScrollDirection,
    lockedDirection: PageScrollDirection?,
    displayMode: PageDisplayMode,
    onPreferredDirectionChange: (PageScrollDirection) -> Unit,
    onDisplayModeChange: (PageDisplayMode) -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDirection = lockedDirection ?: preferredDirection
    val isDirectionLocked = lockedDirection != null

    Surface(
        modifier = modifier,
        color = Color(0xFFF4F4F4),
        contentColor = Color(0xFF111111),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pageCount.toString(),
                color = Color(0xFF111111),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 6.dp),
            )
            ToolButton(
                icon = VerticalPagesIcon,
                contentDescription = "vertical pages",
                selected = selectedDirection == PageScrollDirection.Vertical,
                enabled = !isDirectionLocked,
                onClick = { onPreferredDirectionChange(PageScrollDirection.Vertical) },
            )
            ToolButton(
                icon = HorizontalPagesIcon,
                contentDescription = "horizontal pages",
                selected = selectedDirection == PageScrollDirection.Horizontal,
                enabled = !isDirectionLocked,
                onClick = { onPreferredDirectionChange(PageScrollDirection.Horizontal) },
            )
            ToolButton(
                icon = SeamlessPagesIcon,
                contentDescription = "seamless pages",
                selected = displayMode == PageDisplayMode.Seamless,
                onClick = { onDisplayModeChange(PageDisplayMode.Seamless) },
            )
            ToolButton(
                icon = SplitPagesIcon,
                contentDescription = "split pages",
                selected = displayMode == PageDisplayMode.Split,
                onClick = { onDisplayModeChange(PageDisplayMode.Split) },
            )
            ToolButton(
                icon = AddPageIcon,
                contentDescription = "add page",
                selected = false,
                onClick = onAddPage,
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    struckThrough: Boolean = false,
) {
    StylusHoverTooltipBox(tooltipText = contentDescription) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) Color(0xFF111111) else Color.Transparent,
                contentColor = if (selected) Color.White else Color(0xFF111111),
            ),
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
                if (struckThrough) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = if (selected) Color(0xFF111111) else Color(0xFFF4F4F4),
                            start = Offset(size.width * 0.18f, size.height * 0.82f),
                            end = Offset(size.width * 0.82f, size.height * 0.18f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
