package com.genericnotes.app.hwdn

import com.genericnotes.app.canvas.InkStroke
import com.genericnotes.app.canvas.NotePageLayout

internal data class HwdnDocument(
    val fileName: String,
    val strokes: List<InkStroke>,
    val pageLayout: NotePageLayout = NotePageLayout(),
)
