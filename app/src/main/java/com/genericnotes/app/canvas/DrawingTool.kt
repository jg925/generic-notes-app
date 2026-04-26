package com.genericnotes.app.canvas

internal enum class DrawingTool {
    Pen,
    Eraser,
}

internal fun DrawingTool.strokeWidth(pressure: Float): Float =
    when (this) {
        DrawingTool.Pen -> pressure.strokeWidth()
        DrawingTool.Eraser -> EraserStrokeWidth
    }

internal val DrawingTool.serializedName: String
    get() = when (this) {
        DrawingTool.Pen -> "pen"
        DrawingTool.Eraser -> "eraser"
    }

internal fun drawingToolFromSerializedName(name: String): DrawingTool? =
    when (name.lowercase()) {
        "pen" -> DrawingTool.Pen
        "eraser" -> DrawingTool.Eraser
        else -> null
    }

private fun Float.strokeWidth(): Float = 2f + (this * 8f)
