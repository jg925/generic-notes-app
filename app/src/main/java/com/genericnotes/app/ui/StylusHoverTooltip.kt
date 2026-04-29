package com.genericnotes.app.ui

import android.view.MotionEvent
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StylusHoverTooltipBox(
    tooltipText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var hasStylusHover by remember { mutableStateOf(false) }
    val tooltipYOffset = with(LocalDensity.current) { 52.dp.roundToPx() }

    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER,
                    MotionEvent.ACTION_HOVER_MOVE -> {
                        hasStylusHover = event.hasTrueStylusTool()
                        false
                    }

                    MotionEvent.ACTION_HOVER_EXIT,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_DOWN -> {
                        hasStylusHover = false
                        false
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()

        if (isHovered && hasStylusHover) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(x = 0, y = tooltipYOffset),
            ) {
                Surface(
                    color = Color(0xFF111111),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = tooltipText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun MotionEvent.hasTrueStylusTool(): Boolean =
    (0 until pointerCount).any { pointerIndex ->
        when (getToolType(pointerIndex)) {
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_ERASER -> true
            else -> false
        }
    }
