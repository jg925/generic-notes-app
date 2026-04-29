package com.genericnotes.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

internal class InkCanvasView(context: Context) : View(context) {
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
    var ignoreTouchInput: Boolean = false
    var onCanUndoChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(canUndo)
        }

    val canUndo: Boolean
        get() = strokes.isNotEmpty()

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
    private var activePointerId: Int? = null
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
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activeStroke == null) {
                    startStrokeFromPointer(event, event.actionIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = activePointerIndex(event)
                if (pointerIndex != -1) {
                    activeStroke?.let { stroke ->
                        appendHistoricalPoints(stroke, event, pointerIndex)
                        appendPoint(
                            stroke = stroke,
                            x = event.getX(pointerIndex),
                            y = event.getY(pointerIndex),
                            pressure = event.getPressure(pointerIndex),
                            eventTimeMillis = event.eventTime,
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = activePointerIndex(event)
                val isEndingActivePointer = event.actionMasked == MotionEvent.ACTION_CANCEL ||
                    pointerIndex == event.actionIndex

                if (isEndingActivePointer && pointerIndex != -1) {
                    activeStroke?.let { stroke ->
                        appendPoint(
                            stroke = stroke,
                            x = event.getX(pointerIndex),
                            y = event.getY(pointerIndex),
                            pressure = event.getPressure(pointerIndex),
                            eventTimeMillis = event.eventTime,
                        )
                    }
                }
                if (isEndingActivePointer) {
                    activePointerId = null
                    activeStroke = null
                    hideEraserPreview()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        return true
    }

    fun loadStrokes(documentStrokes: List<InkStroke>) {
        activeStroke = null
        hideEraserPreview()
        strokes.clear()
        strokes.addAll(documentStrokes)
        redrawAllStrokes()
        notifyCanUndoChanged()
    }

    fun strokesSnapshot(): List<InkStroke> = strokes.toList()

    fun undoLastStroke(): Boolean {
        if (strokes.isEmpty()) return false

        val removedStroke = strokes.removeAt(strokes.lastIndex)
        if (activeStroke === removedStroke) {
            activeStroke = null
            activePointerId = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        hideEraserPreview()
        redrawAllStrokes()
        notifyCanUndoChanged()
        return true
    }

    private fun startStrokeFromPointer(event: MotionEvent, pointerIndex: Int) {
        if (!acceptsInputFrom(event, pointerIndex)) return

        parent?.requestDisallowInterceptTouchEvent(true)
        activePointerId = event.getPointerId(pointerIndex)
        activeStroke = InkStroke(
            tool = selectedTool,
            startedAt = Instant.now(),
            startEventTimeMillis = event.eventTime,
        ).also { stroke ->
            strokes.add(stroke)
            notifyCanUndoChanged()
            appendHistoricalPoints(stroke, event, pointerIndex)
            appendPoint(
                stroke = stroke,
                x = event.getX(pointerIndex),
                y = event.getY(pointerIndex),
                pressure = event.getPressure(pointerIndex),
                eventTimeMillis = event.eventTime,
            )
        }
    }

    private fun notifyCanUndoChanged() {
        onCanUndoChanged?.invoke(canUndo)
    }

    private fun activePointerIndex(event: MotionEvent): Int {
        val pointerId = activePointerId ?: return -1
        return event.findPointerIndex(pointerId)
    }

    private fun acceptsInputFrom(event: MotionEvent, pointerIndex: Int): Boolean {
        if (!ignoreTouchInput) return true

        return when (event.getToolType(pointerIndex)) {
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_ERASER -> true
            else -> false
        }
    }

    private fun appendHistoricalPoints(stroke: InkStroke, event: MotionEvent, pointerIndex: Int) {
        for (index in 0 until event.historySize) {
            appendPoint(
                stroke = stroke,
                x = event.getHistoricalX(pointerIndex, index),
                y = event.getHistoricalY(pointerIndex, index),
                pressure = event.getHistoricalPressure(pointerIndex, index),
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
}
