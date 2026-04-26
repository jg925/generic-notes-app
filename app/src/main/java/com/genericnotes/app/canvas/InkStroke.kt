package com.genericnotes.app.canvas

import java.time.Instant
import kotlin.math.max

internal class InkStroke(
    internal val tool: DrawingTool,
    internal val startedAt: Instant,
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

    fun tAt(index: Int): Float = points[index * PointStride + 3]

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
