package com.genericnotes.app.hwdn

import android.net.Uri
import com.genericnotes.app.canvas.InkStroke
import java.time.Instant

internal data class HwdnDocument(
    val fileName: String,
    val strokes: List<InkStroke>,
    val interpretation: HwdnInterpretation? = null,
    val sourceUri: Uri? = null,
)

internal data class HwdnInterpretation(
    val plainText: String,
    val generatedAt: Instant?,
)
