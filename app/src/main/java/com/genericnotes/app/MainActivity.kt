package com.genericnotes.app

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen(window)

        setContent {
            MaterialTheme {
                Surface(color = Color.White) {
                    NotesCanvasScreen()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun NotesCanvasScreen() {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var activeStroke by remember { mutableStateOf<InkStroke?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    handleInkInput(
                        event = event,
                        isLocked = isLocked,
                        view = view,
                        strokes = strokes,
                        activeStroke = activeStroke,
                        setActiveStroke = { activeStroke = it },
                    )
                }
        ) {
            strokes.forEach { stroke -> drawStroke(stroke) }
        }

        Button(
            onClick = { isLocked = !isLocked },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLocked) Color(0xFF111111) else Color(0xFFECECEC),
                contentColor = if (isLocked) Color.White else Color(0xFF111111),
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isLocked) "Unlock edits" else "Lock edits")
        }
    }
}

private fun handleInkInput(
    event: MotionEvent,
    isLocked: Boolean,
    view: View,
    strokes: MutableList<InkStroke>,
    activeStroke: InkStroke?,
    setActiveStroke: (InkStroke?) -> Unit,
): Boolean {
    if (isLocked) return true

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            view.parent?.requestDisallowInterceptTouchEvent(true)
            val stroke = InkStroke()
            stroke.addHistoricalPoints(event)
            stroke.addPoint(event.x, event.y, event.pressure)
            strokes.add(stroke)
            setActiveStroke(stroke)
        }

        MotionEvent.ACTION_MOVE -> {
            val stroke = activeStroke ?: return true
            stroke.addHistoricalPoints(event)
            stroke.addPoint(event.x, event.y, event.pressure)
            strokes.replaceActiveStroke(stroke)
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            activeStroke?.let { stroke ->
                stroke.addPoint(event.x, event.y, event.pressure)
                strokes.replaceActiveStroke(stroke)
            }
            setActiveStroke(null)
            view.parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    return true
}

private fun MutableList<InkStroke>.replaceActiveStroke(stroke: InkStroke) {
    if (isNotEmpty()) {
        this[lastIndex] = stroke.copy(points = stroke.points.toMutableList())
    }
}

private fun InkStroke.addHistoricalPoints(event: MotionEvent) {
    for (index in 0 until event.historySize) {
        addPoint(
            x = event.getHistoricalX(index),
            y = event.getHistoricalY(index),
            pressure = event.getHistoricalPressure(index),
        )
    }
}

private fun InkStroke.addPoint(x: Float, y: Float, pressure: Float) {
    points.add(InkPoint(Offset(x, y), pressure.coerceIn(0.05f, 1.5f)))
}

private fun DrawScope.drawStroke(stroke: InkStroke) {
    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = Color.Black,
            radius = point.strokeWidth() / 2f,
            center = point.offset,
        )
        return
    }

    stroke.points.zipWithNext { start, end ->
        drawLine(
            color = Color.Black,
            start = start.offset,
            end = end.offset,
            strokeWidth = max(start.strokeWidth(), end.strokeWidth()),
            cap = StrokeCap.Round,
        )
    }
}

private fun InkPoint.strokeWidth(): Float = 2f + (pressure * 8f)

private fun enterFullscreen(window: Window) {
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private data class InkStroke(
    val points: MutableList<InkPoint> = mutableListOf(),
)

private data class InkPoint(
    val offset: Offset,
    val pressure: Float,
)
