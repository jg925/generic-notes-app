package com.genericnotes.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    var pageLayout: NotePageLayout = NotePageLayout()
        set(value) {
            if (field == value) return
            val couldResetZoom = canResetZoom
            field = value
            clampCanvasOffset()
            if (couldResetZoom != canResetZoom) notifyCanResetZoomChanged()
            invalidate()
        }
    var onCanUndoChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(canUndo)
        }
    var onCanResetZoomChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(canResetZoom)
        }
    var onCanRedoChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(canRedo)
        }
    var onStrokeAddedToHistory: (() -> Unit)? = null
    var onStrokeRemovedFromHistory: (() -> Unit)? = null

    val canUndo: Boolean
        get() = strokes.isNotEmpty()
    val canResetZoom: Boolean
        get() = abs(zoomScale - FullScreenZoomScale) > ZoomStateEpsilon ||
            abs(canvasOffsetX) > ZoomStateEpsilon ||
            abs(canvasOffsetY) > ZoomStateEpsilon

    val canRedo: Boolean
        get() = redoStrokes.isNotEmpty()

    private val strokes = mutableListOf<InkStroke>()
    private val redoStrokes = mutableListOf<InkStroke>()
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
    private val canvasBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    private val canvasBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(96, 17, 17, 17)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val pageDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(205, 205, 205)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val pageGapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(233, 233, 233)
        style = Paint.Style.FILL
    }
    private val eraserXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private var activeStroke: InkStroke? = null
    private var activePointerId: Int? = null
    private var activePointerToolType: Int = MotionEvent.TOOL_TYPE_UNKNOWN
    private var inkBitmap: Bitmap? = null
    private var inkCanvas: Canvas? = null
    private var eraserPreviewX = 0f
    private var eraserPreviewY = 0f
    private var isEraserPreviewVisible = false
    private var zoomScale = FullScreenZoomScale
    private var canvasOffsetX = 0f
    private var canvasOffsetY = 0f
    private var isTransformingCanvas = false
    private var suppressStrokeUntilAllPointersUp = false
    private var transformStartSpan = 0f
    private var transformStartScale = FullScreenZoomScale
    private var transformFocusCanvasX = 0f
    private var transformFocusCanvasY = 0f

    init {
        isFocusable = true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        inkBitmap?.recycle()
        inkBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        inkCanvas = Canvas(inkBitmap!!)
        if (oldWidth <= 0 || oldHeight <= 0) {
            resetZoomToFullScreen()
        } else {
            setCanvasTransform(
                scale = zoomScale,
                offsetX = canvasOffsetX,
                offsetY = canvasOffsetY,
            )
        }
        redrawAllStrokes()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pageGapPaint)
        canvas.save()
        canvas.translate(canvasOffsetX, canvasOffsetY)
        canvas.scale(zoomScale, zoomScale)
        drawPageBackgrounds(canvas)
        inkBitmap?.let { bitmap -> drawInkBitmap(canvas, bitmap) }
        drawPageSeparators(canvas)
        drawPageOutlines(canvas)
        if (isEraserPreviewVisible) {
            canvas.drawCircle(eraserPreviewX, eraserPreviewY, EraserStrokeWidth / 2f, eraserPreviewPaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if ((event.pointerCount >= 2 || isTransformingCanvas) && !isStylusStrokeActive()) {
            handleCanvasTransform(event)
            return true
        }

        if (suppressStrokeUntilAllPointersUp) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                suppressStrokeUntilAllPointersUp = false
            }
            return true
        }

        if (isLocked) {
            hideEraserPreview()
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startStrokeFromPointer(event, event.actionIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = activePointerIndex(event)
                if (pointerIndex != -1) {
                    activeStroke?.let { stroke ->
                        appendHistoricalPoints(stroke, event, pointerIndex)
                        appendPoint(
                            stroke = stroke,
                            x = viewToCanvasX(event.getX(pointerIndex)),
                            y = viewToCanvasY(event.getY(pointerIndex)),
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
                            x = viewToCanvasX(event.getX(pointerIndex)),
                            y = viewToCanvasY(event.getY(pointerIndex)),
                            pressure = event.getPressure(pointerIndex),
                            eventTimeMillis = event.eventTime,
                        )
                    }
                }
                if (isEndingActivePointer) {
                    activePointerId = null
                    activePointerToolType = MotionEvent.TOOL_TYPE_UNKNOWN
                    activeStroke = null
                    hideEraserPreview()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        return true
    }

    private fun drawPageBackgrounds(canvas: Canvas) {
        val shouldDrawIndividualPages = pageLayout.displayMode == PageDisplayMode.Split ||
            pageLayout.pageShape != PageShapePrimitive.Rectangle
        if (!shouldDrawIndividualPages) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), canvasBackgroundPaint)
            return
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pageGapPaint)
        forEachVisiblePageBounds { bounds ->
            drawPageShape(canvas, bounds, canvasBackgroundPaint)
        }
    }

    private fun drawPageSeparators(canvas: Canvas) {
        if (pageLayout.pageShape != PageShapePrimitive.Rectangle) return

        val pageCount = pageLayout.normalizedPageCount
        val direction = pageLayout.scrollDirection ?: return
        if (pageCount <= 1) return

        when (direction) {
            PageScrollDirection.Vertical -> {
                val pageHeight = height.toFloat() / pageCount
                for (pageIndex in 1 until pageCount) {
                    val y = pageHeight * pageIndex
                    drawHorizontalPageSeparator(canvas, y, pageHeight)
                }
            }

            PageScrollDirection.Horizontal -> {
                val pageWidth = width.toFloat() / pageCount
                for (pageIndex in 1 until pageCount) {
                    val x = pageWidth * pageIndex
                    drawVerticalPageSeparator(canvas, x, pageWidth)
                }
            }
        }
    }

    private fun drawHorizontalPageSeparator(canvas: Canvas, y: Float, pageHeight: Float) {
        if (pageLayout.displayMode == PageDisplayMode.Split) {
            val halfGap = (splitPageGapPx(pageHeight) / 2f)
            canvas.drawRect(0f, y - halfGap, width.toFloat(), y + halfGap, pageGapPaint)
        } else {
            canvas.drawLine(0f, y, width.toFloat(), y, pageDividerPaint)
        }
    }

    private fun drawVerticalPageSeparator(canvas: Canvas, x: Float, pageWidth: Float) {
        if (pageLayout.displayMode == PageDisplayMode.Split) {
            val halfGap = (splitPageGapPx(pageWidth) / 2f)
            canvas.drawRect(x - halfGap, 0f, x + halfGap, height.toFloat(), pageGapPaint)
        } else {
            canvas.drawLine(x, 0f, x, height.toFloat(), pageDividerPaint)
        }
    }

    private fun splitPageGapPx(pageExtent: Float): Float =
        min(SplitPageGapDp * resources.displayMetrics.density, pageExtent / 4f)

    private fun drawPageOutlines(canvas: Canvas) {
        val pageCount = pageLayout.normalizedPageCount
        val direction = pageLayout.scrollDirection
        if (
            pageLayout.pageShape == PageShapePrimitive.Rectangle &&
            (pageCount <= 1 || direction == null || pageLayout.displayMode != PageDisplayMode.Split)
        ) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), canvasBorderPaint)
            return
        }

        forEachVisiblePageBounds { bounds ->
            drawPageShape(canvas, bounds, canvasBorderPaint)
        }
    }

    private fun drawInkBitmap(canvas: Canvas, bitmap: Bitmap) {
        if (pageLayout.displayMode != PageDisplayMode.Split && pageLayout.pageShape == PageShapePrimitive.Rectangle) {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            return
        }

        val pagePath = Path()
        forEachVisiblePageBounds { bounds ->
            addPageShapeToPath(pagePath, bounds)
        }

        canvas.save()
        canvas.clipPath(pagePath)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
    }

    private inline fun forEachVisiblePageBounds(block: (RectF) -> Unit) {
        val pageCount = pageLayout.normalizedPageCount
        val direction = pageLayout.scrollDirection
        val bounds = RectF()

        if (pageCount <= 1 || direction == null) {
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            block(bounds)
            return
        }

        when (direction) {
            PageScrollDirection.Vertical -> {
                val pageHeight = height.toFloat() / pageCount
                val halfGap = splitPageHalfGapPx(pageHeight)
                for (pageIndex in 0 until pageCount) {
                    bounds.set(
                        0f,
                        pageHeight * pageIndex + if (pageIndex == 0) 0f else halfGap,
                        width.toFloat(),
                        pageHeight * (pageIndex + 1) - if (pageIndex == pageCount - 1) 0f else halfGap,
                    )
                    block(bounds)
                }
            }

            PageScrollDirection.Horizontal -> {
                val pageWidth = width.toFloat() / pageCount
                val halfGap = splitPageHalfGapPx(pageWidth)
                for (pageIndex in 0 until pageCount) {
                    bounds.set(
                        pageWidth * pageIndex + if (pageIndex == 0) 0f else halfGap,
                        0f,
                        pageWidth * (pageIndex + 1) - if (pageIndex == pageCount - 1) 0f else halfGap,
                        height.toFloat(),
                    )
                    block(bounds)
                }
            }
        }
    }

    private fun splitPageHalfGapPx(pageExtent: Float): Float =
        if (pageLayout.displayMode == PageDisplayMode.Split) {
            splitPageGapPx(pageExtent) / 2f
        } else {
            0f
        }

    private fun drawPageShape(canvas: Canvas, bounds: RectF, paint: Paint) {
        val shapeBounds = bounds.pageShapeBounds()
        when (pageLayout.pageShape) {
            PageShapePrimitive.Rectangle,
            PageShapePrimitive.Square -> canvas.drawRect(shapeBounds, paint)

            PageShapePrimitive.Circle -> canvas.drawOval(shapeBounds, paint)
        }
    }

    private fun addPageShapeToPath(path: Path, bounds: RectF) {
        val shapeBounds = bounds.pageShapeBounds()
        when (pageLayout.pageShape) {
            PageShapePrimitive.Rectangle,
            PageShapePrimitive.Square -> path.addRect(shapeBounds, Path.Direction.CW)

            PageShapePrimitive.Circle -> path.addOval(shapeBounds, Path.Direction.CW)
        }
    }

    private fun RectF.pageShapeBounds(): RectF {
        if (pageLayout.pageShape == PageShapePrimitive.Rectangle) return RectF(this)

        val side = min(width(), height())
        val shapeLeft = left + ((width() - side) / 2f)
        val shapeTop = top + ((height() - side) / 2f)
        return RectF(shapeLeft, shapeTop, shapeLeft + side, shapeTop + side)
    }

    fun loadStrokes(documentStrokes: List<InkStroke>) {
        activeStroke = null
        hideEraserPreview()
        resetZoomToFullScreen()
        strokes.clear()
        redoStrokes.clear()
        strokes.addAll(documentStrokes)
        redrawAllStrokes()
        notifyHistoryChanged()
    }

    fun strokesSnapshot(): List<InkStroke> = strokes.toList()

    fun resetZoomToFullScreen() {
        isTransformingCanvas = false
        suppressStrokeUntilAllPointersUp = false
        setCanvasTransform(
            scale = FullScreenZoomScale,
            offsetX = 0f,
            offsetY = 0f,
        )
    }

    fun undoLastStroke(): Boolean {
        if (strokes.isEmpty()) return false

        val removedStroke = strokes.removeAt(strokes.lastIndex)
        redoStrokes.add(removedStroke)
        if (activeStroke === removedStroke) {
            activeStroke = null
            activePointerId = null
            activePointerToolType = MotionEvent.TOOL_TYPE_UNKNOWN
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        hideEraserPreview()
        redrawAllStrokes()
        notifyHistoryChanged()
        return true
    }

    fun redoLastStroke(): Boolean {
        if (redoStrokes.isEmpty()) return false

        strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
        redrawAllStrokes()
        notifyHistoryChanged()
        return true
    }

    fun clearRedoHistory() {
        if (redoStrokes.isEmpty()) return

        redoStrokes.clear()
        notifyHistoryChanged()
    }

    private fun startStrokeFromPointer(event: MotionEvent, pointerIndex: Int) {
        if (!acceptsInputFrom(event, pointerIndex)) return
        val canvasX = viewToCanvasX(event.getX(pointerIndex))
        val canvasY = viewToCanvasY(event.getY(pointerIndex))
        if (!isPointInsideCanvas(canvasX, canvasY)) return

        parent?.requestDisallowInterceptTouchEvent(true)
        activePointerId = event.getPointerId(pointerIndex)
        activePointerToolType = event.getToolType(pointerIndex)
        activeStroke = InkStroke(
            tool = selectedTool,
            startedAt = Instant.now(),
            startEventTimeMillis = event.eventTime,
        ).also { stroke ->
            redoStrokes.clear()
            strokes.add(stroke)
            onStrokeAddedToHistory?.invoke()
            notifyHistoryChanged()
            appendHistoricalPoints(stroke, event, pointerIndex)
            appendPoint(
                stroke = stroke,
                x = canvasX,
                y = canvasY,
                pressure = event.getPressure(pointerIndex),
                eventTimeMillis = event.eventTime,
            )
        }
    }

    private fun notifyHistoryChanged() {
        onCanUndoChanged?.invoke(canUndo)
        onCanRedoChanged?.invoke(canRedo)
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
                x = viewToCanvasX(event.getHistoricalX(pointerIndex, index)),
                y = viewToCanvasY(event.getHistoricalY(pointerIndex, index)),
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
        if (!isPointInsideCanvas(x, y)) {
            if (stroke.tool == DrawingTool.Eraser) hideEraserPreview()
            return
        }

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
        val viewLeft = canvasOffsetX + (dirtyBounds.left * zoomScale)
        val viewTop = canvasOffsetY + (dirtyBounds.top * zoomScale)
        val viewRight = canvasOffsetX + (dirtyBounds.right * zoomScale)
        val viewBottom = canvasOffsetY + (dirtyBounds.bottom * zoomScale)
        val invalidLeft = min(viewLeft, viewRight).toInt().coerceAtLeast(0)
        val invalidTop = min(viewTop, viewBottom).toInt().coerceAtLeast(0)
        val invalidRight = max(viewLeft, viewRight).toInt().coerceAtMost(width) + 1
        val invalidBottom = max(viewTop, viewBottom).toInt().coerceAtMost(height) + 1
        if (invalidLeft >= invalidRight || invalidTop >= invalidBottom) return

        postInvalidateOnAnimation(
            invalidLeft,
            invalidTop,
            invalidRight,
            invalidBottom,
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

    private fun handleCanvasTransform(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> startCanvasTransform(event)
            MotionEvent.ACTION_MOVE -> {
                if (!isTransformingCanvas) startCanvasTransform(event)
                updateCanvasTransform(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount - 1 >= 2) {
                    startCanvasTransform(event, excludedPointerIndex = event.actionIndex)
                } else {
                    endCanvasTransform(suppressStroke = true)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> endCanvasTransform(suppressStroke = false)
        }
    }

    private fun startCanvasTransform(event: MotionEvent, excludedPointerIndex: Int? = null) {
        val pointerIndices = firstTwoPointerIndices(event, excludedPointerIndex) ?: return
        cancelActiveStroke(removeStroke = true)
        hideEraserPreview()
        parent?.requestDisallowInterceptTouchEvent(true)
        isTransformingCanvas = true
        suppressStrokeUntilAllPointersUp = false
        transformStartSpan = pointerDistance(event, pointerIndices.first, pointerIndices.second)
            .coerceAtLeast(MinTransformSpan)
        transformStartScale = zoomScale
        val focusX = pointerFocusX(event, pointerIndices.first, pointerIndices.second)
        val focusY = pointerFocusY(event, pointerIndices.first, pointerIndices.second)
        transformFocusCanvasX = viewToCanvasX(focusX)
        transformFocusCanvasY = viewToCanvasY(focusY)
    }

    private fun updateCanvasTransform(event: MotionEvent) {
        val pointerIndices = firstTwoPointerIndices(event) ?: return
        val span = pointerDistance(event, pointerIndices.first, pointerIndices.second)
            .coerceAtLeast(MinTransformSpan)
        val focusX = pointerFocusX(event, pointerIndices.first, pointerIndices.second)
        val focusY = pointerFocusY(event, pointerIndices.first, pointerIndices.second)
        val nextScale = (transformStartScale * (span / transformStartSpan))
            .coerceIn(MinCanvasZoomScale, MaxCanvasZoomScale)

        setCanvasTransform(
            scale = nextScale,
            offsetX = focusX - (transformFocusCanvasX * nextScale),
            offsetY = focusY - (transformFocusCanvasY * nextScale),
        )
    }

    private fun endCanvasTransform(suppressStroke: Boolean) {
        isTransformingCanvas = false
        suppressStrokeUntilAllPointersUp = suppressStroke
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelActiveStroke(removeStroke: Boolean) {
        val stroke = activeStroke
        activeStroke = null
        activePointerId = null
        activePointerToolType = MotionEvent.TOOL_TYPE_UNKNOWN
        if (removeStroke && stroke != null && strokes.remove(stroke)) {
            onStrokeRemovedFromHistory?.invoke()
            redrawAllStrokes()
            notifyHistoryChanged()
        }
    }

    private fun firstTwoPointerIndices(event: MotionEvent, excludedPointerIndex: Int? = null): Pair<Int, Int>? {
        var firstIndex: Int? = null
        var secondIndex: Int? = null
        for (index in 0 until event.pointerCount) {
            if (index == excludedPointerIndex) continue
            if (firstIndex == null) {
                firstIndex = index
            } else {
                secondIndex = index
                break
            }
        }

        val first = firstIndex ?: return null
        val second = secondIndex ?: return null
        return first to second
    }

    private fun pointerDistance(event: MotionEvent, firstIndex: Int, secondIndex: Int): Float {
        val dx = event.getX(secondIndex) - event.getX(firstIndex)
        val dy = event.getY(secondIndex) - event.getY(firstIndex)
        return sqrt((dx * dx) + (dy * dy))
    }

    private fun pointerFocusX(event: MotionEvent, firstIndex: Int, secondIndex: Int): Float =
        (event.getX(firstIndex) + event.getX(secondIndex)) / 2f

    private fun pointerFocusY(event: MotionEvent, firstIndex: Int, secondIndex: Int): Float =
        (event.getY(firstIndex) + event.getY(secondIndex)) / 2f

    private fun setCanvasTransform(scale: Float, offsetX: Float, offsetY: Float) {
        val couldResetZoom = canResetZoom
        zoomScale = scale.coerceIn(MinCanvasZoomScale, MaxCanvasZoomScale)
        canvasOffsetX = offsetX
        canvasOffsetY = offsetY
        clampCanvasOffset()
        if (couldResetZoom != canResetZoom) notifyCanResetZoomChanged()
        invalidate()
    }

    private fun clampCanvasOffset() {
        if (width <= 0 || height <= 0) return

        val scaledWidth = width * zoomScale
        val scaledHeight = height * zoomScale
        val pageCount = pageLayout.normalizedPageCount
        val scrollDirection = pageLayout.scrollDirection?.takeIf { pageCount > 1 }

        canvasOffsetX = clampedCanvasOffset(
            currentOffset = canvasOffsetX,
            viewExtent = width,
            scaledExtent = scaledWidth,
            pageCount = pageCount,
            isPagedScrollAxis = scrollDirection == PageScrollDirection.Horizontal,
        )
        canvasOffsetY = clampedCanvasOffset(
            currentOffset = canvasOffsetY,
            viewExtent = height,
            scaledExtent = scaledHeight,
            pageCount = pageCount,
            isPagedScrollAxis = scrollDirection == PageScrollDirection.Vertical,
        )
    }

    private fun clampedCanvasOffset(
        currentOffset: Float,
        viewExtent: Int,
        scaledExtent: Float,
        pageCount: Int,
        isPagedScrollAxis: Boolean,
    ): Float {
        if (scaledExtent > viewExtent) {
            return currentOffset.coerceIn(viewExtent - scaledExtent, 0f)
        }

        val anchorExtent = if (isPagedScrollAxis) {
            viewExtent.toFloat() / pageCount
        } else {
            viewExtent.toFloat()
        }
        return (anchorExtent - (anchorExtent * zoomScale)) / 2f
    }

    private fun notifyCanResetZoomChanged() {
        onCanResetZoomChanged?.invoke(canResetZoom)
    }

    private fun viewToCanvasX(x: Float): Float =
        (x - canvasOffsetX) / zoomScale

    private fun viewToCanvasY(y: Float): Float =
        (y - canvasOffsetY) / zoomScale

    private fun isPointInsideCanvas(x: Float, y: Float): Boolean =
        x in 0f..width.toFloat() &&
            y in 0f..height.toFloat() &&
            isPointInsideVisiblePageShape(x, y)

    private fun isPointInsideVisiblePageShape(x: Float, y: Float): Boolean {
        var isInside = false
        forEachVisiblePageBounds { bounds ->
            if (!isInside && isPointInsidePageShape(x, y, bounds)) {
                isInside = true
            }
        }
        return isInside
    }

    private fun isPointInsidePageShape(x: Float, y: Float, bounds: RectF): Boolean {
        val shapeBounds = bounds.pageShapeBounds()
        if (!shapeBounds.contains(x, y)) return false

        if (pageLayout.pageShape != PageShapePrimitive.Circle) return true

        val radius = min(shapeBounds.width(), shapeBounds.height()) / 2f
        val dx = x - shapeBounds.centerX()
        val dy = y - shapeBounds.centerY()
        return (dx * dx) + (dy * dy) <= radius * radius
    }

    private fun isStylusStrokeActive(): Boolean =
        activeStroke != null &&
            (
                activePointerToolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    activePointerToolType == MotionEvent.TOOL_TYPE_ERASER
                )
}

private const val FullScreenZoomScale = 1f
private const val MinCanvasZoomScale = 0.5f
private const val MaxCanvasZoomScale = 4f
private const val MinTransformSpan = 16f
private const val ZoomStateEpsilon = 0.001f
private const val SplitPageGapDp = 12f
