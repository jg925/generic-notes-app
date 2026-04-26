package com.genericnotes.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val PenIcon: ImageVector =
    ImageVector.Builder(
        name = "Pen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14.06f, 4.94f)
            lineTo(19.06f, 9.94f)
            lineTo(7.25f, 21.75f)
            lineTo(2.25f, 21.75f)
            lineTo(2.25f, 16.75f)
            close()
            moveTo(15.47f, 3.53f)
            lineTo(16.88f, 2.12f)
            curveTo(17.66f, 1.34f, 18.93f, 1.34f, 19.71f, 2.12f)
            lineTo(21.88f, 4.29f)
            curveTo(22.66f, 5.07f, 22.66f, 6.34f, 21.88f, 7.12f)
            lineTo(20.47f, 8.53f)
            close()
        }
    }.build()

internal val EraserIcon: ImageVector =
    ImageVector.Builder(
        name = "Eraser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.24f, 3.56f)
            curveTo(17.02f, 2.78f, 18.29f, 2.78f, 19.07f, 3.56f)
            lineTo(21.44f, 5.93f)
            curveTo(22.22f, 6.71f, 22.22f, 7.98f, 21.44f, 8.76f)
            lineTo(10.7f, 19.5f)
            lineTo(5.94f, 19.5f)
            lineTo(2.56f, 16.12f)
            curveTo(1.78f, 15.34f, 1.78f, 14.07f, 2.56f, 13.29f)
            close()
            moveTo(4.0f, 14.7f)
            lineTo(7.3f, 18.0f)
            lineTo(10.08f, 18.0f)
            lineTo(14.0f, 14.08f)
            lineTo(9.92f, 10.0f)
            close()
            moveTo(12.0f, 21.5f)
            lineTo(22.0f, 21.5f)
            lineTo(22.0f, 19.5f)
            lineTo(12.0f, 19.5f)
            close()
        }
    }.build()

internal val SaveIcon: ImageVector =
    ImageVector.Builder(
        name = "Save",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.0f, 3.0f)
            lineTo(17.0f, 3.0f)
            lineTo(21.0f, 7.0f)
            lineTo(21.0f, 21.0f)
            lineTo(3.0f, 21.0f)
            lineTo(3.0f, 4.0f)
            curveTo(3.0f, 3.45f, 3.45f, 3.0f, 4.0f, 3.0f)
            close()
            moveTo(7.0f, 5.0f)
            lineTo(7.0f, 10.0f)
            lineTo(16.0f, 10.0f)
            lineTo(16.0f, 5.0f)
            close()
            moveTo(7.0f, 14.0f)
            lineTo(7.0f, 19.0f)
            lineTo(17.0f, 19.0f)
            lineTo(17.0f, 14.0f)
            close()
        }
    }.build()
