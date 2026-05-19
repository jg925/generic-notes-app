package com.genericnotes.app.ui.dictation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.genericnotes.app.hwdn.SourceApplicationName

@Composable
internal fun DictationPreviewSheet(
    state: DictationUiState,
    accentColor: Color,
    onDraftChange: (String) -> Unit,
    onRecordAgain: () -> Unit,
    onStopListening: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRecord = state.status != DictationStatus.Listening &&
        state.status != DictationStatus.Processing &&
        state.status != DictationStatus.RequestingPermission
    val canSave = state.draftText.isNotBlank() && canRecord
    val recordButtonText = if (state.draftText.isBlank()) "Start recording" else "Record again"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .padding(16.dp)
            .navigationBarsPadding()
            .imePadding(),
        color = Color(0xFFF4F4F4),
        contentColor = accentColor,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dictation",
                    color = accentColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.status.displayText,
                    color = if (state.status == DictationStatus.Error || state.status == DictationStatus.PermissionDenied) {
                        Color(0xFFB42318)
                    } else {
                        Color(0xFF555555)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedTextField(
                value = state.draftText,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status != DictationStatus.Listening && state.status != DictationStatus.Processing,
                minLines = 3,
                maxLines = 5,
                label = { Text("Draft understanding") },
                shape = RoundedCornerShape(8.dp),
            )
            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    color = Color(0xFFB42318),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "HWDN preview",
                color = Color(0xFF555555),
                style = MaterialTheme.typography.titleSmall,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = hwdnInterpretationPreview(
                        draftText = state.draftText,
                        savedUnderstanding = state.savedUnderstanding,
                    ),
                    color = Color(0xFF222222),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRecordAgain,
                    enabled = canRecord,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                ) {
                    Text(recordButtonText)
                }
                if (state.status == DictationStatus.Listening) {
                    OutlinedButton(
                        onClick = onStopListening,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    ) {
                        Text("Stop")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

private fun hwdnInterpretationPreview(
    draftText: String,
    savedUnderstanding: DictationUnderstanding?,
): String {
    val plainText = draftText.trim()
    val generatedAt = savedUnderstanding
        ?.takeIf { it.plainText == plainText }
        ?.generatedAt
        ?.toString()
        ?: "set when Save is pressed"

    return """
        canvas.interpretation.plainText:
        ${plainText.ifBlank { "(empty draft)" }}

        canvas.interpretation.source.type: human
        canvas.interpretation.source.name: $SourceApplicationName
        canvas.interpretation.generatedAt: $generatedAt
    """.trimIndent()
}
