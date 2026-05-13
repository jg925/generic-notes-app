package com.genericnotes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.genericnotes.app.canvas.DrawingTool

@Composable
internal fun NotesCanvasToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    canResetZoom: Boolean,
    isDictationSelected: Boolean,
    selectedTool: DrawingTool,
    supportsTrueStylusInput: Boolean,
    ignoreTouchInput: Boolean,
    accentColor: Color,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    onOpenDictation: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onTogglePalmReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF4F4F4),
        contentColor = accentColor,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(
                icon = UndoIcon,
                contentDescription = "undo",
                selected = false,
                enabled = canUndo,
                accentColor = accentColor,
                onClick = onUndo,
            )
            ToolButton(
                icon = RedoIcon,
                contentDescription = "redo",
                selected = false,
                enabled = canRedo,
                accentColor = accentColor,
                onClick = onRedo,
            )
            ToolButton(
                icon = FitScreenIcon,
                contentDescription = "reset zoom",
                selected = false,
                enabled = canResetZoom,
                accentColor = accentColor,
                onClick = onResetZoom,
            )
            ToolButton(
                icon = MicrophoneIcon,
                contentDescription = "dictation",
                selected = isDictationSelected,
                accentColor = accentColor,
                onClick = onOpenDictation,
            )
            ToolButton(
                icon = PenIcon,
                contentDescription = "pen",
                selected = selectedTool == DrawingTool.Pen,
                accentColor = accentColor,
                onClick = { onSelectTool(DrawingTool.Pen) },
            )
            ToolButton(
                icon = EraserIcon,
                contentDescription = "eraser",
                selected = selectedTool == DrawingTool.Eraser,
                accentColor = accentColor,
                onClick = { onSelectTool(DrawingTool.Eraser) },
            )
            if (supportsTrueStylusInput) {
                ToolButton(
                    icon = HandIcon,
                    contentDescription = "palm reject",
                    selected = ignoreTouchInput,
                    accentColor = accentColor,
                    onClick = onTogglePalmReject,
                    struckThrough = ignoreTouchInput,
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    enabled: Boolean = true,
    struckThrough: Boolean = false,
) {
    val toolbarBackgroundColor = Color(0xFFF4F4F4)

    StylusHoverTooltipBox(
        tooltipText = contentDescription,
        containerColor = accentColor,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) accentColor else Color.Transparent,
                contentColor = if (selected) Color.White else accentColor,
                disabledContentColor = accentColor.copy(alpha = 0.38f),
            ),
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
                if (struckThrough) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = if (selected) accentColor else toolbarBackgroundColor,
                            start = Offset(size.width * 0.18f, size.height * 0.82f),
                            end = Offset(size.width * 0.82f, size.height * 0.18f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
