package com.genericnotes.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.genericnotes.app.hwdn.HwdnInterpretation
import com.genericnotes.app.hwdn.HwdnMimeType
import com.genericnotes.app.hwdn.MaxFileNameLength
import com.genericnotes.app.hwdn.displayNameFor
import com.genericnotes.app.hwdn.exportHwdnPackage
import com.genericnotes.app.hwdn.toHwdnFileName
import com.genericnotes.app.hwdn.withoutHwdnExtension
import com.genericnotes.app.settings.AppCanvasSettings
import com.genericnotes.app.settings.loadAppCanvasSettings
import com.genericnotes.app.settings.saveAppCanvasSettings
import com.genericnotes.app.ui.dictation.DictationPreviewSheet
import com.genericnotes.app.ui.dictation.DictationUnderstanding
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
    var fileName by remember(initialDocument) {
        mutableStateOf(initialDocument?.fileName?.withoutHwdnExtension()?.take(MaxFileNameLength) ?: "")
    }
    val undoActions = remember(initialDocument) {
        mutableStateListOf<CanvasHistoryAction>().apply {
            repeat(initialDocument?.strokes?.size ?: 0) {
                add(CanvasHistoryAction.Stroke)
            }
        }
    }
    val redoActions = remember(initialDocument) { mutableStateListOf<CanvasHistoryAction>() }
    var canResetZoom by remember(initialDocument) { mutableStateOf(false) }
    var inkCanvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var pendingDocumentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var currentDocumentUri by remember(initialDocument) { mutableStateOf(initialDocument?.sourceUri) }
    val dictationController = rememberDictationController(
        resetKey = initialDocument,
        initialUnderstanding = initialDocument?.interpretation?.toDictationUnderstanding(),
    )
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
            context.writeHwdnBytes(uri, documentBytes)
        }.onSuccess {
            currentDocumentUri = uri
            fileName = (context.displayNameFor(uri) ?: documentName)
                .withoutHwdnExtension()
                .take(MaxFileNameLength)
            onDocumentSaved(uri, documentName)
            Toast.makeText(context, "Saved $documentName", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun currentPageLayout(): NotePageLayout =
        NotePageLayout(
            pageCount = pageCount,
            scrollDirection = pageScrollDirection,
            displayMode = pageDisplayMode,
        )

    fun recordNewStrokeAction() {
        undoActions.add(CanvasHistoryAction.Stroke)
        redoActions.clear()
    }

    fun removePendingStrokeAction() {
        if (undoActions.lastOrNull() == CanvasHistoryAction.Stroke) {
            undoActions.removeAt(undoActions.lastIndex)
        }
    }

    fun addPage() {
        val nextScrollDirection = pageScrollDirection ?: preferredPageDirection
        val action = CanvasHistoryAction.PageAdded(
            beforePageCount = pageCount,
            beforeScrollDirection = pageScrollDirection,
            afterPageCount = pageCount + 1,
            afterScrollDirection = nextScrollDirection,
        )

        inkCanvasView?.clearRedoHistory()
        redoActions.clear()
        pageScrollDirection = nextScrollDirection
        pageCount += 1
        undoActions.add(action)
    }

    fun undoLastAction() {
        val action = undoActions.lastOrNull() ?: return
        undoActions.removeAt(undoActions.lastIndex)

        when (action) {
            CanvasHistoryAction.Stroke -> {
                if (inkCanvasView?.undoLastStroke() == true) {
                    redoActions.add(action)
                }
            }

            is CanvasHistoryAction.PageAdded -> {
                pageCount = action.beforePageCount
                pageScrollDirection = action.beforeScrollDirection
                redoActions.add(action)
            }
        }
    }

    fun redoLastAction() {
        val action = redoActions.lastOrNull() ?: return
        redoActions.removeAt(redoActions.lastIndex)

        when (action) {
            CanvasHistoryAction.Stroke -> {
                if (inkCanvasView?.redoLastStroke() == true) {
                    undoActions.add(action)
                }
            }

            is CanvasHistoryAction.PageAdded -> {
                pageCount = action.afterPageCount
                pageScrollDirection = action.afterScrollDirection
                undoActions.add(action)
            }
        }
    }

    fun exportCurrentDocument(interpretation: HwdnInterpretation?): Pair<String, ByteArray>? {
        val canvasView = inkCanvasView ?: return null
        val hwdnFileName = fileName.toHwdnFileName()
        val documentBytes = exportHwdnPackage(
            strokes = canvasView.strokesSnapshot(),
            fileName = hwdnFileName,
            canvasWidth = canvasView.width,
            canvasHeight = canvasView.height,
            pageLayout = currentPageLayout(),
            interpretation = interpretation,
        )
        return hwdnFileName to documentBytes
    }

    fun saveAsNewDocument(
        interpretation: HwdnInterpretation? = dictationState.savedUnderstanding?.toHwdnInterpretation(),
    ) {
        val (hwdnFileName, documentBytes) = exportCurrentDocument(interpretation) ?: return
        pendingDocumentBytes = documentBytes
        pendingFileName = hwdnFileName
        saveDocumentLauncher.launch(hwdnFileName)
    }

    fun writeExistingDocument(uri: Uri, interpretation: HwdnInterpretation?) {
        val (hwdnFileName, documentBytes) = exportCurrentDocument(interpretation) ?: return

        runCatching {
            context.writeHwdnBytes(uri, documentBytes)
        }.onSuccess {
            currentDocumentUri = uri
            onDocumentSaved(uri, hwdnFileName)
            Toast.makeText(context, "Saved $hwdnFileName", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Could not update opened file", Toast.LENGTH_SHORT).show()
            saveAsNewDocument(interpretation)
        }
    }

    fun saveCurrentDocument() {
        val interpretation = dictationState.savedUnderstanding?.toHwdnInterpretation()
        val targetUri = currentDocumentUri
        if (targetUri != null) {
            writeExistingDocument(targetUri, interpretation)
        } else {
            saveAsNewDocument(interpretation)
        }
    }

    fun persistSavedInterpretation(understanding: DictationUnderstanding) {
        val interpretation = understanding.toHwdnInterpretation()
        val targetUri = currentDocumentUri
        if (targetUri != null) {
            writeExistingDocument(targetUri, interpretation)
        } else {
            saveAsNewDocument(interpretation)
        }
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
        val currentPage = when (renderedDirection) {
            PageScrollDirection.Vertical -> currentPageNumber(
                scrollOffsetPx = verticalScrollState.value,
                pageExtentPx = with(density) { pageHeight.toPx() },
                pageCount = pageCount,
            )

            PageScrollDirection.Horizontal -> currentPageNumber(
                scrollOffsetPx = horizontalScrollState.value,
                pageExtentPx = with(density) { pageWidth.toPx() },
                pageCount = pageCount,
            )
        }
        val renderedPageLayout = currentPageLayout()

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
                        canvasView.onCanResetZoomChanged = { canResetZoom = it }
                        canvasView.onStrokeAddedToHistory = ::recordNewStrokeAction
                        canvasView.onStrokeRemovedFromHistory = ::removePendingStrokeAction
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
            accentColor = accentColor,
            onSave = { saveCurrentDocument() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        )

        NotesCanvasToolbar(
            canUndo = undoActions.isNotEmpty(),
            canRedo = redoActions.isNotEmpty(),
            canResetZoom = canResetZoom,
            selectedTool = selectedTool,
            supportsTrueStylusInput = supportsTrueStylusInput,
            ignoreTouchInput = ignoreTouchInput,
            accentColor = accentColor,
            onUndo = ::undoLastAction,
            onRedo = ::redoLastAction,
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
            PageControls(
                currentPage = currentPage,
                pageCount = pageCount,
                preferredDirection = preferredPageDirection,
                lockedDirection = pageScrollDirection,
                displayMode = pageDisplayMode,
                accentColor = accentColor,
                onPreferredDirectionChange = { direction ->
                    if (pageScrollDirection == null) {
                        preferredPageDirection = direction
                    }
                },
                onDisplayModeChange = { pageDisplayMode = it },
                onAddPage = ::addPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )

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
                onSave = {
                    dictationController.saveDraft()?.let(::persistSavedInterpretation)
                },
                onCancel = dictationController.closeSheet,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun android.content.Context.writeHwdnBytes(uri: Uri, documentBytes: ByteArray) {
    val outputStream = runCatching {
        contentResolver.openOutputStream(uri, "wt")
    }.getOrNull()
        ?: contentResolver.openOutputStream(uri)
        ?: error("Unable to open save destination.")

    outputStream.use { stream ->
        stream.write(documentBytes)
    }
}

private fun HwdnInterpretation.toDictationUnderstanding(): DictationUnderstanding =
    DictationUnderstanding(
        plainText = plainText,
        generatedAt = generatedAt,
    )

private fun DictationUnderstanding.toHwdnInterpretation(): HwdnInterpretation =
    HwdnInterpretation(
        plainText = plainText,
        generatedAt = generatedAt,
    )

private sealed interface CanvasHistoryAction {
    object Stroke : CanvasHistoryAction

    data class PageAdded(
        val beforePageCount: Int,
        val beforeScrollDirection: PageScrollDirection?,
        val afterPageCount: Int,
        val afterScrollDirection: PageScrollDirection,
    ) : CanvasHistoryAction
}

private fun currentPageNumber(
    scrollOffsetPx: Int,
    pageExtentPx: Float,
    pageCount: Int,
): Int {
    if (pageCount <= 1 || pageExtentPx <= 0f) return 1

    return ((scrollOffsetPx / pageExtentPx).toInt() + 1).coerceIn(1, pageCount)
}

@Composable
private fun PageControls(
    currentPage: Int,
    pageCount: Int,
    preferredDirection: PageScrollDirection,
    lockedDirection: PageScrollDirection?,
    displayMode: PageDisplayMode,
    accentColor: Color,
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
        contentColor = accentColor,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$currentPage / $pageCount",
                color = accentColor,
                modifier = Modifier.padding(end = 8.dp),
            )
            PageControlButton(
                icon = VerticalPagesIcon,
                contentDescription = "vertical pages",
                selected = selectedDirection == PageScrollDirection.Vertical,
                enabled = !isDirectionLocked,
                accentColor = accentColor,
                onClick = { onPreferredDirectionChange(PageScrollDirection.Vertical) },
            )
            PageControlButton(
                icon = HorizontalPagesIcon,
                contentDescription = "horizontal pages",
                selected = selectedDirection == PageScrollDirection.Horizontal,
                enabled = !isDirectionLocked,
                accentColor = accentColor,
                onClick = { onPreferredDirectionChange(PageScrollDirection.Horizontal) },
            )
            PageControlButton(
                icon = SeamlessPagesIcon,
                contentDescription = "seamless pages",
                selected = displayMode == PageDisplayMode.Seamless,
                accentColor = accentColor,
                onClick = { onDisplayModeChange(PageDisplayMode.Seamless) },
            )
            PageControlButton(
                icon = SplitPagesIcon,
                contentDescription = "split pages",
                selected = displayMode == PageDisplayMode.Split,
                accentColor = accentColor,
                onClick = { onDisplayModeChange(PageDisplayMode.Split) },
            )
            PageControlButton(
                icon = AddPageIcon,
                contentDescription = "add page",
                selected = false,
                accentColor = accentColor,
                onClick = onAddPage,
            )
        }
    }
}

@Composable
private fun PageControlButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    enabled: Boolean = true,
) {
    StylusHoverTooltipBox(
        tooltipText = contentDescription,
        containerColor = accentColor,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) accentColor else Color.Transparent,
                contentColor = if (selected) Color.White else accentColor,
                disabledContentColor = accentColor.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
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
