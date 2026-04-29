package com.genericnotes.app.hwdn

import com.genericnotes.app.canvas.DrawingTool
import com.genericnotes.app.canvas.EraserStrokeWidth
import com.genericnotes.app.canvas.InkStroke
import com.genericnotes.app.canvas.PenNominalStrokeWidth
import com.genericnotes.app.canvas.serializedName
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal fun exportHwdnPackage(
    strokes: List<InkStroke>,
    fileName: String,
    canvasWidth: Int,
    canvasHeight: Int,
): ByteArray {
    val exportedAt = Instant.now()
    val packageId = "note-${UUID.randomUUID()}"
    val documentId = "doc-${UUID.randomUUID()}"
    val canvasId = "canvas-${UUID.randomUUID()}"
    val title = fileName.withoutHwdnExtension().trim().ifBlank { "Untitled Note" }
    val noteJson = createNoteJson(
        strokes = strokes,
        documentId = documentId,
        canvasId = canvasId,
        title = title,
        timestamp = exportedAt,
        canvasWidth = canvasWidth.coerceAtLeast(1),
        canvasHeight = canvasHeight.coerceAtLeast(1),
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
    strokes: List<InkStroke>,
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
                ),
        )
}

private fun InkStroke.toJson(strokeNumber: Int): JSONObject =
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
