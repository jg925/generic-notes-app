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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.min

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
    var isLocked by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(DrawingTool.Pen) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        AndroidView(
            factory = { context -> InkCanvasView(context) },
            update = { inkCanvas ->
                inkCanvas.isLocked = isLocked
                inkCanvas.selectedTool = selectedTool
            },
            modifier = Modifier.fillMaxSize(),
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
                activeStroke = InkStroke(selectedTool).also { stroke ->
                    strokes.add(stroke)
                    appendHistoricalPoints(stroke, event)
                    appendPoint(stroke, event.x, event.y, event.pressure)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                activeStroke?.let { stroke ->
                    appendHistoricalPoints(stroke, event)
                    appendPoint(stroke, event.x, event.y, event.pressure)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activeStroke?.let { stroke ->
                    appendPoint(stroke, event.x, event.y, event.pressure)
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
            )
        }
    }

    private fun appendPoint(stroke: InkStroke, x: Float, y: Float, pressure: Float) {
        val previousIndex = stroke.lastIndex
        val coercedPressure = pressure.coerceIn(0.05f, 1.5f)
        stroke.addPoint(x, y, coercedPressure)
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

private class InkStroke(val tool: DrawingTool) {
    private var points = FloatArray(InitialPointCapacity * PointStride)
    var size = 0
        private set

    val lastIndex: Int
        get() = size - 1

    fun addPoint(x: Float, y: Float, pressure: Float) {
        ensureCapacity(size + 1)
        val offset = size * PointStride
        points[offset] = x
        points[offset + 1] = y
        points[offset + 2] = pressure
        size += 1
    }

    fun xAt(index: Int): Float = points[index * PointStride]

    fun yAt(index: Int): Float = points[index * PointStride + 1]

    fun pressureAt(index: Int): Float = points[index * PointStride + 2]

    private fun ensureCapacity(pointCapacity: Int) {
        val requiredSize = pointCapacity * PointStride
        if (requiredSize <= points.size) return
        points = points.copyOf(max(requiredSize, points.size * 2))
    }

    private companion object {
        const val InitialPointCapacity = 128
        const val PointStride = 3
    }
}

private fun Float.strokeWidth(): Float = 2f + (this * 8f)

private const val EraserStrokeWidth = 48f

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
