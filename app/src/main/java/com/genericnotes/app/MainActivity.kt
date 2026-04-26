package com.genericnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen(window)

        setContent {
            MaterialTheme {
                Surface(color = Color.White) {
                    NotesCanvasScreen()
                }
            }
        }
    }
}

@Composable
private fun NotesCanvasScreen() {
    val context = LocalContext.current
    var isLocked by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(DrawingTool.Pen) }
    var fileName by remember { mutableStateOf("Untitled Note") }
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
                InkCanvasView(viewContext).also { inkCanvasView = it }
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
                pendingDocumentBytes = canvasView.exportHwdnPackage(hwdnFileName)
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
private fun FilePanel(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            BasicTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF111111)),
                cursorBrush = SolidColor(Color(0xFF111111)),
                modifier = Modifier.width(156.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (fileName.isBlank()) {
                            Text(
                                text = "Untitled Note",
                                color = Color(0xFF777777),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                text = HwdnExtension,
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF111111),
                ),
            ) {
                Icon(
                    imageVector = SaveIcon,
                    contentDescription = "Save",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
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

private class InkCanvasView(context: Context) : View(context) {
    var isLocked: Boolean = false
        set(value) {
            field = value
            if (value) hideEraserPreview()
        }
    var selectedTool: DrawingTool = DrawingTool.Pen
        set(value) {
            field = value
            if (value != DrawingTool.Eraser) hideEraserPreview()
        }

    private val strokes = mutableListOf<InkStroke>()
    private val dirtyBounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val eraserPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(170, 17, 17, 17)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val eraserXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private var activeStroke: InkStroke? = null
    private var inkBitmap: Bitmap? = null
    private var inkCanvas: Canvas? = null
    private var eraserPreviewX = 0f
    private var eraserPreviewY = 0f
    private var isEraserPreviewVisible = false

    init {
        setBackgroundColor(android.graphics.Color.WHITE)
        isFocusable = true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        inkBitmap?.recycle()
        inkBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        inkCanvas = Canvas(inkBitmap!!)
        redrawAllStrokes()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        inkBitmap?.let { bitmap -> canvas.drawBitmap(bitmap, 0f, 0f, null) }
        if (isEraserPreviewVisible) {
            canvas.drawCircle(eraserPreviewX, eraserPreviewY, EraserStrokeWidth / 2f, eraserPreviewPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeStroke = InkStroke(
                    tool = selectedTool,
                    startedAt = Instant.now(),
                    startEventTimeMillis = event.eventTime,
                ).also { stroke ->
                    strokes.add(stroke)
                    appendHistoricalPoints(stroke, event)
                    appendPoint(stroke, event.x, event.y, event.pressure, event.eventTime)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                activeStroke?.let { stroke ->
                    appendHistoricalPoints(stroke, event)
                    appendPoint(stroke, event.x, event.y, event.pressure, event.eventTime)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activeStroke?.let { stroke ->
                    appendPoint(stroke, event.x, event.y, event.pressure, event.eventTime)
                }
                activeStroke = null
                hideEraserPreview()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    private fun appendHistoricalPoints(stroke: InkStroke, event: MotionEvent) {
        for (index in 0 until event.historySize) {
            appendPoint(
                stroke = stroke,
                x = event.getHistoricalX(index),
                y = event.getHistoricalY(index),
                pressure = event.getHistoricalPressure(index),
                eventTimeMillis = event.getHistoricalEventTime(index),
            )
        }
    }

    private fun appendPoint(
        stroke: InkStroke,
        x: Float,
        y: Float,
        pressure: Float,
        eventTimeMillis: Long,
    ) {
        val previousIndex = stroke.lastIndex
        val coercedPressure = pressure.coerceIn(0.05f, 1.5f)
        stroke.addPoint(x, y, coercedPressure, eventTimeMillis)
        if (stroke.tool == DrawingTool.Eraser) {
            showEraserPreview(x, y)
        }

        if (previousIndex == -1) {
            drawDot(stroke.tool, x, y, coercedPressure, invalidate = true)
        } else {
            drawSegment(
                tool = stroke.tool,
                startX = stroke.xAt(previousIndex),
                startY = stroke.yAt(previousIndex),
                startPressure = stroke.pressureAt(previousIndex),
                endX = x,
                endY = y,
                endPressure = coercedPressure,
                invalidate = true,
            )
        }
    }

    private fun drawDot(tool: DrawingTool, x: Float, y: Float, pressure: Float, invalidate: Boolean) {
        val canvas = inkCanvas ?: return
        val radius = tool.strokeWidth(pressure) / 2f
        configurePaint(tool, Paint.Style.FILL, radius * 2f)
        canvas.drawCircle(x, y, radius, paint)
        paint.xfermode = null

        if (invalidate) {
            invalidateDirty(
                left = x - radius,
                top = y - radius,
                right = x + radius,
                bottom = y + radius,
                padding = radius,
            )
        }
    }

    private fun drawSegment(
        tool: DrawingTool,
        startX: Float,
        startY: Float,
        startPressure: Float,
        endX: Float,
        endY: Float,
        endPressure: Float,
        invalidate: Boolean,
    ) {
        val canvas = inkCanvas ?: return
        val strokeWidth = max(tool.strokeWidth(startPressure), tool.strokeWidth(endPressure))
        configurePaint(tool, Paint.Style.STROKE, strokeWidth)
        canvas.drawLine(startX, startY, endX, endY, paint)
        paint.xfermode = null

        if (invalidate) {
            invalidateDirty(
                left = min(startX, endX),
                top = min(startY, endY),
                right = max(startX, endX),
                bottom = max(startY, endY),
                padding = strokeWidth,
            )
        }
    }

    private fun invalidateDirty(left: Float, top: Float, right: Float, bottom: Float, padding: Float) {
        dirtyBounds.set(left - padding, top - padding, right + padding, bottom + padding)
        postInvalidateOnAnimation(
            dirtyBounds.left.toInt().coerceAtLeast(0),
            dirtyBounds.top.toInt().coerceAtLeast(0),
            dirtyBounds.right.toInt().coerceAtMost(width) + 1,
            dirtyBounds.bottom.toInt().coerceAtMost(height) + 1,
        )
    }

    private fun redrawAllStrokes() {
        val canvas = inkCanvas ?: return
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (stroke in strokes) {
            drawStrokeToBackingCanvas(stroke)
        }
        invalidate()
    }

    private fun drawStrokeToBackingCanvas(stroke: InkStroke) {
        when (stroke.size) {
            0 -> return
            1 -> {
                drawDot(
                    tool = stroke.tool,
                    x = stroke.xAt(0),
                    y = stroke.yAt(0),
                    pressure = stroke.pressureAt(0),
                    invalidate = false,
                )
            }

            else -> {
                for (index in 0 until stroke.lastIndex) {
                    drawSegment(
                        tool = stroke.tool,
                        startX = stroke.xAt(index),
                        startY = stroke.yAt(index),
                        startPressure = stroke.pressureAt(index),
                        endX = stroke.xAt(index + 1),
                        endY = stroke.yAt(index + 1),
                        endPressure = stroke.pressureAt(index + 1),
                        invalidate = false,
                    )
                }
            }
        }
    }

    private fun configurePaint(tool: DrawingTool, style: Paint.Style, strokeWidth: Float) {
        paint.style = style
        paint.strokeWidth = strokeWidth
        paint.xfermode = if (tool == DrawingTool.Eraser) eraserXfermode else null
    }

    private fun showEraserPreview(x: Float, y: Float) {
        val wasVisible = isEraserPreviewVisible
        val oldX = eraserPreviewX
        val oldY = eraserPreviewY
        eraserPreviewX = x
        eraserPreviewY = y
        isEraserPreviewVisible = true

        if (wasVisible) {
            invalidateEraserPreview(oldX, oldY)
        }
        invalidateEraserPreview(x, y)
    }

    private fun hideEraserPreview() {
        if (!isEraserPreviewVisible) return
        val oldX = eraserPreviewX
        val oldY = eraserPreviewY
        isEraserPreviewVisible = false
        invalidateEraserPreview(oldX, oldY)
    }

    private fun invalidateEraserPreview(x: Float, y: Float) {
        val radius = EraserStrokeWidth / 2f
        invalidateDirty(
            left = x - radius,
            top = y - radius,
            right = x + radius,
            bottom = y + radius,
            padding = eraserPreviewPaint.strokeWidth + 2f,
        )
    }

    fun exportHwdnPackage(fileName: String): ByteArray {
        val exportedAt = Instant.now()
        val packageId = "note-${UUID.randomUUID()}"
        val documentId = "doc-${UUID.randomUUID()}"
        val canvasId = "canvas-${UUID.randomUUID()}"
        val title = fileName.withoutHwdnExtension().trim().ifBlank { "Untitled Note" }
        val canvasWidth = width.coerceAtLeast(1)
        val canvasHeight = height.coerceAtLeast(1)
        val noteJson = createNoteJson(
            documentId = documentId,
            canvasId = canvasId,
            title = title,
            timestamp = exportedAt,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
        )
        val manifestJson = createManifestJson(
            packageId = packageId,
            fileName = fileName,
            timestamp = exportedAt,
        )

        return ByteArrayOutputStream().use { byteOutput ->
            ZipOutputStream(byteOutput).use { zipOutput ->
                zipOutput.writeJsonEntry("manifest.json", manifestJson)
                zipOutput.writeJsonEntry("note.json", noteJson)
                zipOutput.putNextEntry(ZipEntry("assets/"))
                zipOutput.closeEntry()
            }
            byteOutput.toByteArray()
        }
    }

    private fun createManifestJson(packageId: String, fileName: String, timestamp: Instant): JSONObject =
        JSONObject()
            .put("format", "hwdn")
            .put("formatVersion", HwdnFormatVersion)
            .put("id", packageId)
            .put("fileName", fileName)
            .put("createdAt", timestamp.toString())
            .put("modifiedAt", timestamp.toString())
            .put(
                "createdBy",
                JSONObject()
                    .put("name", SourceApplicationName)
                    .put("version", SourceApplicationVersion),
            )
            .put("payload", "note.json")
            .put(
                "features",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("name", "canvas-strokes")
                            .put("required", true)
                            .put("version", HwdnFormatVersion),
                    ),
            )

    private fun createNoteJson(
        documentId: String,
        canvasId: String,
        title: String,
        timestamp: Instant,
        canvasWidth: Int,
        canvasHeight: Int,
    ): JSONObject {
        val canvasSize = JSONObject()
            .put("width", canvasWidth)
            .put("height", canvasHeight)

        return JSONObject()
            .put(
                "document",
                JSONObject()
                    .put("id", documentId)
                    .put("title", title)
                    .put("createdAt", timestamp.toString())
                    .put("modifiedAt", timestamp.toString())
                    .put("units", "px")
                    .put("canvasSize", canvasSize)
                    .put(
                        "sourceApplication",
                        JSONObject()
                            .put("name", SourceApplicationName)
                            .put("version", SourceApplicationVersion),
                    ),
            )
            .put(
                "canvas",
                JSONObject()
                    .put("id", canvasId)
                    .put("size", canvasSize)
                    .put(
                        "background",
                        JSONObject()
                            .put("color", "#ffffff")
                            .put("style", "blank"),
                    )
                    .put(
                        "strokes",
                        JSONArray().apply {
                            strokes.forEachIndexed { index, stroke ->
                                put(stroke.toJson(index + 1))
                            }
                        },
                    )
                    .put("ocr", JSONArray()),
            )
    }
}

private fun enterFullscreen(window: Window) {
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private enum class DrawingTool {
    Pen,
    Eraser,
}

private fun DrawingTool.strokeWidth(pressure: Float): Float =
    when (this) {
        DrawingTool.Pen -> pressure.strokeWidth()
        DrawingTool.Eraser -> EraserStrokeWidth
    }

private val DrawingTool.serializedName: String
    get() = when (this) {
        DrawingTool.Pen -> "pen"
        DrawingTool.Eraser -> "eraser"
    }

private class InkStroke(
    val tool: DrawingTool,
    private val startedAt: Instant,
    private val startEventTimeMillis: Long,
) {
    private var points = FloatArray(InitialPointCapacity * PointStride)
    var size = 0
        private set

    val lastIndex: Int
        get() = size - 1

    fun addPoint(x: Float, y: Float, pressure: Float, eventTimeMillis: Long) {
        ensureCapacity(size + 1)
        val offset = size * PointStride
        points[offset] = x
        points[offset + 1] = y
        points[offset + 2] = pressure
        points[offset + 3] = (eventTimeMillis - startEventTimeMillis).coerceAtLeast(0).toFloat()
        size += 1
    }

    fun xAt(index: Int): Float = points[index * PointStride]

    fun yAt(index: Int): Float = points[index * PointStride + 1]

    fun pressureAt(index: Int): Float = points[index * PointStride + 2]

    private fun tAt(index: Int): Float = points[index * PointStride + 3]

    fun toJson(strokeNumber: Int): JSONObject =
        JSONObject()
            .put("id", "stroke-${strokeNumber.toString().padStart(6, '0')}")
            .put("tool", tool.serializedName)
            .put("brush", tool.toBrushJson())
            .put("startedAt", startedAt.toString())
            .put(
                "points",
                JSONArray().apply {
                    for (index in 0 until size) {
                        put(
                            JSONObject()
                                .put("x", xAt(index).toDouble())
                                .put("y", yAt(index).toDouble())
                                .put("t", tAt(index).toDouble())
                                .put("pressure", tool.exportPressure(pressureAt(index)).toDouble()),
                        )
                    }
                },
            )

    private fun ensureCapacity(pointCapacity: Int) {
        val requiredSize = pointCapacity * PointStride
        if (requiredSize <= points.size) return
        points = points.copyOf(max(requiredSize, points.size * 2))
    }

    private companion object {
        const val InitialPointCapacity = 128
        const val PointStride = 4
    }
}

private fun Float.strokeWidth(): Float = 2f + (this * 8f)

private fun String.withoutHwdnExtension(): String =
    if (endsWith(HwdnExtension, ignoreCase = true)) {
        dropLast(HwdnExtension.length)
    } else {
        this
    }

private fun String.toHwdnFileName(): String {
    val baseName = withoutHwdnExtension()
        .trim()
        .replace(InvalidFileNameCharactersRegex, "-")
        .replace(WhitespaceRegex, " ")
        .ifBlank { "Untitled Note" }
        .take(MaxFileNameLength)
    return "$baseName$HwdnExtension"
}

private fun DrawingTool.toBrushJson(): JSONObject =
    when (this) {
        DrawingTool.Pen ->
            JSONObject()
                .put("color", "#111111")
                .put("baseWidth", PenNominalStrokeWidth.toDouble())
                .put("opacity", 1)
                .put("pressureCurve", "linear")

        DrawingTool.Eraser ->
            JSONObject()
                .put("color", "#ffffff")
                .put("baseWidth", EraserStrokeWidth.toDouble())
                .put("opacity", 1)
                .put("pressureCurve", "none")
    }

private fun DrawingTool.exportPressure(pressure: Float): Float =
    when (this) {
        DrawingTool.Pen -> pressure.coerceIn(0f, 1f)
        DrawingTool.Eraser -> 1f
    }

private fun ZipOutputStream.writeJsonEntry(path: String, jsonObject: JSONObject) {
    putNextEntry(ZipEntry(path))
    write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
    closeEntry()
}

private const val PenNominalStrokeWidth = 10f
private const val EraserStrokeWidth = 48f
private const val HwdnExtension = ".hwdn"
private const val HwdnMimeType = "application/zip"
private const val HwdnFormatVersion = "0.1.0"
private const val MaxFileNameLength = 80
private const val SourceApplicationName = "generic-notes-app"
private const val SourceApplicationVersion = "0.1.0"

private val InvalidFileNameCharactersRegex = Regex("""[\\/:*?"<>|]+""")
private val WhitespaceRegex = Regex("""\s+""")

private val PenIcon: ImageVector =
    ImageVector.Builder(
        name = "Pen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14.06f, 4.94f)
            lineTo(19.06f, 9.94f)
            lineTo(7.25f, 21.75f)
            lineTo(2.25f, 21.75f)
            lineTo(2.25f, 16.75f)
            close()
            moveTo(15.47f, 3.53f)
            lineTo(16.88f, 2.12f)
            curveTo(17.66f, 1.34f, 18.93f, 1.34f, 19.71f, 2.12f)
            lineTo(21.88f, 4.29f)
            curveTo(22.66f, 5.07f, 22.66f, 6.34f, 21.88f, 7.12f)
            lineTo(20.47f, 8.53f)
            close()
        }
    }.build()

private val EraserIcon: ImageVector =
    ImageVector.Builder(
        name = "Eraser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.24f, 3.56f)
            curveTo(17.02f, 2.78f, 18.29f, 2.78f, 19.07f, 3.56f)
            lineTo(21.44f, 5.93f)
            curveTo(22.22f, 6.71f, 22.22f, 7.98f, 21.44f, 8.76f)
            lineTo(10.7f, 19.5f)
            lineTo(5.94f, 19.5f)
            lineTo(2.56f, 16.12f)
            curveTo(1.78f, 15.34f, 1.78f, 14.07f, 2.56f, 13.29f)
            close()
            moveTo(4.0f, 14.7f)
            lineTo(7.3f, 18.0f)
            lineTo(10.08f, 18.0f)
            lineTo(14.0f, 14.08f)
            lineTo(9.92f, 10.0f)
            close()
            moveTo(12.0f, 21.5f)
            lineTo(22.0f, 21.5f)
            lineTo(22.0f, 19.5f)
            lineTo(12.0f, 19.5f)
            close()
        }
    }.build()

private val SaveIcon: ImageVector =
    ImageVector.Builder(
        name = "Save",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.0f, 3.0f)
            lineTo(17.0f, 3.0f)
            lineTo(21.0f, 7.0f)
            lineTo(21.0f, 21.0f)
            lineTo(3.0f, 21.0f)
            lineTo(3.0f, 4.0f)
            curveTo(3.0f, 3.45f, 3.45f, 3.0f, 4.0f, 3.0f)
            close()
            moveTo(7.0f, 5.0f)
            lineTo(7.0f, 10.0f)
            lineTo(16.0f, 10.0f)
            lineTo(16.0f, 5.0f)
            close()
            moveTo(7.0f, 14.0f)
            lineTo(7.0f, 19.0f)
            lineTo(17.0f, 19.0f)
            lineTo(17.0f, 14.0f)
            close()
        }
    }.build()
