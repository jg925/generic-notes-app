package com.genericnotes.app.hwdn

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.genericnotes.app.canvas.DrawingTool
import com.genericnotes.app.canvas.InkStroke
import com.genericnotes.app.canvas.drawingToolFromSerializedName
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

internal fun Context.readHwdnDocument(uri: Uri): HwdnDocument {
    val displayName = displayNameFor(uri)
    val bytes = contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.readBytes()
    } ?: error("Unable to open .hwdn file.")

    return parseHwdnPackage(bytes, displayName)
}

internal fun Context.displayNameFor(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (displayNameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(displayNameIndex)
        } else {
            null
        }
    }

internal fun parseHwdnPackage(bytes: ByteArray, fallbackFileName: String?): HwdnDocument {
    var manifestJson: JSONObject? = null
    var noteJson: JSONObject? = null

    ZipInputStream(ByteArrayInputStream(bytes)).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            if (!entry.isDirectory) {
                when (entry.name) {
                    "manifest.json" -> manifestJson = JSONObject(zipInput.readCurrentEntryText())
                    "note.json" -> noteJson = JSONObject(zipInput.readCurrentEntryText())
                }
            }
            zipInput.closeEntry()
        }
    }

    val note = noteJson ?: error("Missing note.json.")
    val canvas = note.optJSONObject("canvas") ?: error("Missing canvas.")
    val document = note.optJSONObject("document")
    val title = document?.optStringOrNull("title")
    val manifestFileName = manifestJson?.optStringOrNull("fileName")
    val fileName = (manifestFileName ?: fallbackFileName ?: title ?: "Untitled Note")
        .withoutHwdnExtension()
        .trim()
        .ifBlank { "Untitled Note" }
        .take(MaxFileNameLength)
    val strokeArray = canvas.optJSONArray("strokes") ?: JSONArray()
    val strokes = buildList {
        for (index in 0 until strokeArray.length()) {
            strokeArray.optJSONObject(index)?.toInkStroke()?.let(::add)
        }
    }

    return HwdnDocument(
        fileName = fileName,
        strokes = strokes,
    )
}

private fun ZipInputStream.readCurrentEntryText(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val bytesRead = read(buffer)
        if (bytesRead == -1) break
        output.write(buffer, 0, bytesRead)
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun JSONObject.toInkStroke(): InkStroke? {
    val tool = drawingToolFromSerializedName(optString("tool")) ?: return null
    val startedAt = runCatching {
        Instant.parse(optString("startedAt"))
    }.getOrDefault(Instant.now())
    val points = optJSONArray("points") ?: return null
    val stroke = InkStroke(
        tool = tool,
        startedAt = startedAt,
        startEventTimeMillis = 0L,
    )

    for (index in 0 until points.length()) {
        val point = points.optJSONObject(index) ?: continue
        val x = point.optFiniteFloat("x") ?: continue
        val y = point.optFiniteFloat("y") ?: continue
        val pressure = point.optFiniteFloat("pressure") ?: 1f
        val eventTimeMillis = point.optFiniteFloat("t")?.toLong()?.coerceAtLeast(0L) ?: 0L
        stroke.addPoint(
            x = x,
            y = y,
            pressure = tool.importPressure(pressure),
            eventTimeMillis = eventTimeMillis,
        )
    }

    return stroke.takeIf { it.size > 0 }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    optString(name).trim().takeIf { it.isNotBlank() }

private fun JSONObject.optFiniteFloat(name: String): Float? {
    val value = optDouble(name, Double.NaN)
    return if (value.isFinite()) value.toFloat() else null
}

private fun DrawingTool.importPressure(pressure: Float): Float =
    when (this) {
        DrawingTool.Pen -> pressure.coerceIn(0.05f, 1.5f)
        DrawingTool.Eraser -> 1f
    }
