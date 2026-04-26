package com.genericnotes.app.hwdn

import com.genericnotes.app.canvas.InkStroke

internal data class HwdnDocument(
    val fileName: String,
    val strokes: List<InkStroke>,
)
