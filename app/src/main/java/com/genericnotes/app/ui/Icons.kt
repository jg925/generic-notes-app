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

internal val UndoIcon: ImageVector =
    ImageVector.Builder(
        name = "Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 5.0f)
            lineTo(12.0f, 1.75f)
            lineTo(6.25f, 7.5f)
            lineTo(12.0f, 13.25f)
            lineTo(12.0f, 9.0f)
            curveTo(14.76f, 9.0f, 17.0f, 11.24f, 17.0f, 14.0f)
            curveTo(17.0f, 16.76f, 14.76f, 19.0f, 12.0f, 19.0f)
            curveTo(9.24f, 19.0f, 7.0f, 16.76f, 7.0f, 14.0f)
            curveTo(7.0f, 13.02f, 7.28f, 12.11f, 7.77f, 11.34f)
            lineTo(5.6f, 10.08f)
            curveTo(4.9f, 11.23f, 4.5f, 12.58f, 4.5f, 14.0f)
            curveTo(4.5f, 18.14f, 7.86f, 21.5f, 12.0f, 21.5f)
            curveTo(16.14f, 21.5f, 19.5f, 18.14f, 19.5f, 14.0f)
            curveTo(19.5f, 9.86f, 16.14f, 6.5f, 12.0f, 6.5f)
            close()
        }
    }.build()

internal val RedoIcon: ImageVector =
    ImageVector.Builder(
        name = "Redo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 5.0f)
            lineTo(12.0f, 1.75f)
            lineTo(17.75f, 7.5f)
            lineTo(12.0f, 13.25f)
            lineTo(12.0f, 9.0f)
            curveTo(9.24f, 9.0f, 7.0f, 11.24f, 7.0f, 14.0f)
            curveTo(7.0f, 16.76f, 9.24f, 19.0f, 12.0f, 19.0f)
            curveTo(14.76f, 19.0f, 17.0f, 16.76f, 17.0f, 14.0f)
            curveTo(17.0f, 13.02f, 16.72f, 12.11f, 16.23f, 11.34f)
            lineTo(18.4f, 10.08f)
            curveTo(19.1f, 11.23f, 19.5f, 12.58f, 19.5f, 14.0f)
            curveTo(19.5f, 18.14f, 16.14f, 21.5f, 12.0f, 21.5f)
            curveTo(7.86f, 21.5f, 4.5f, 18.14f, 4.5f, 14.0f)
            curveTo(4.5f, 9.86f, 7.86f, 6.5f, 12.0f, 6.5f)
            close()
        }
    }.build()

internal val HandIcon: ImageVector =
    ImageVector.Builder(
        name = "Hand",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21.5f, 4.0f)
            curveTo(20.67f, 4.0f, 20.0f, 4.67f, 20.0f, 5.5f)
            lineTo(20.0f, 11.0f)
            lineTo(19.0f, 11.0f)
            lineTo(19.0f, 2.5f)
            curveTo(19.0f, 1.67f, 18.33f, 1.0f, 17.5f, 1.0f)
            curveTo(16.67f, 1.0f, 16.0f, 1.67f, 16.0f, 2.5f)
            lineTo(16.0f, 11.0f)
            lineTo(15.0f, 11.0f)
            lineTo(15.0f, 1.5f)
            curveTo(15.0f, 0.67f, 14.33f, 0.0f, 13.5f, 0.0f)
            curveTo(12.67f, 0.0f, 12.0f, 0.67f, 12.0f, 1.5f)
            lineTo(12.0f, 11.0f)
            lineTo(11.0f, 11.0f)
            lineTo(11.0f, 4.0f)
            curveTo(11.0f, 3.17f, 10.33f, 2.5f, 9.5f, 2.5f)
            curveTo(8.67f, 2.5f, 8.0f, 3.17f, 8.0f, 4.0f)
            lineTo(8.0f, 15.9f)
            lineTo(3.69f, 13.44f)
            curveTo(3.51f, 13.34f, 3.3f, 13.28f, 3.08f, 13.28f)
            curveTo(2.78f, 13.28f, 2.51f, 13.38f, 2.29f, 13.57f)
            curveTo(2.25f, 13.59f, 1.0f, 14.82f, 1.0f, 14.82f)
            lineTo(8.85f, 22.8f)
            curveTo(9.6f, 23.56f, 10.62f, 24.0f, 11.7f, 24.0f)
            lineTo(19.0f, 24.0f)
            curveTo(21.2f, 24.0f, 23.0f, 22.2f, 23.0f, 20.0f)
            lineTo(23.0f, 5.5f)
            curveTo(23.0f, 4.67f, 22.33f, 4.0f, 21.5f, 4.0f)
            close()
        }
    }.build()

internal val FitScreenIcon: ImageVector =
    ImageVector.Builder(
        name = "FitScreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.0f, 4.0f)
            lineTo(10.0f, 4.0f)
            lineTo(10.0f, 6.2f)
            lineTo(6.2f, 6.2f)
            lineTo(6.2f, 10.0f)
            lineTo(4.0f, 10.0f)
            close()
            moveTo(14.0f, 4.0f)
            lineTo(20.0f, 4.0f)
            lineTo(20.0f, 10.0f)
            lineTo(17.8f, 10.0f)
            lineTo(17.8f, 6.2f)
            lineTo(14.0f, 6.2f)
            close()
            moveTo(4.0f, 14.0f)
            lineTo(6.2f, 14.0f)
            lineTo(6.2f, 17.8f)
            lineTo(10.0f, 17.8f)
            lineTo(10.0f, 20.0f)
            lineTo(4.0f, 20.0f)
            close()
            moveTo(17.8f, 14.0f)
            lineTo(20.0f, 14.0f)
            lineTo(20.0f, 20.0f)
            lineTo(14.0f, 20.0f)
            lineTo(14.0f, 17.8f)
            lineTo(17.8f, 17.8f)
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

internal val SettingsIcon: ImageVector =
    ImageVector.Builder(
        name = "Settings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.0f, 5.0f)
            lineTo(7.0f, 5.0f)
            curveTo(7.42f, 3.84f, 8.52f, 3.0f, 9.82f, 3.0f)
            curveTo(11.12f, 3.0f, 12.22f, 3.84f, 12.64f, 5.0f)
            lineTo(20.0f, 5.0f)
            lineTo(20.0f, 7.0f)
            lineTo(12.64f, 7.0f)
            curveTo(12.22f, 8.16f, 11.12f, 9.0f, 9.82f, 9.0f)
            curveTo(8.52f, 9.0f, 7.42f, 8.16f, 7.0f, 7.0f)
            lineTo(4.0f, 7.0f)
            close()
            moveTo(9.82f, 5.0f)
            curveTo(9.27f, 5.0f, 8.82f, 5.45f, 8.82f, 6.0f)
            curveTo(8.82f, 6.55f, 9.27f, 7.0f, 9.82f, 7.0f)
            curveTo(10.37f, 7.0f, 10.82f, 6.55f, 10.82f, 6.0f)
            curveTo(10.82f, 5.45f, 10.37f, 5.0f, 9.82f, 5.0f)
            close()
            moveTo(4.0f, 11.0f)
            lineTo(13.36f, 11.0f)
            curveTo(13.78f, 9.84f, 14.88f, 9.0f, 16.18f, 9.0f)
            curveTo(17.48f, 9.0f, 18.58f, 9.84f, 19.0f, 11.0f)
            lineTo(20.0f, 11.0f)
            lineTo(20.0f, 13.0f)
            lineTo(19.0f, 13.0f)
            curveTo(18.58f, 14.16f, 17.48f, 15.0f, 16.18f, 15.0f)
            curveTo(14.88f, 15.0f, 13.78f, 14.16f, 13.36f, 13.0f)
            lineTo(4.0f, 13.0f)
            close()
            moveTo(16.18f, 11.0f)
            curveTo(15.63f, 11.0f, 15.18f, 11.45f, 15.18f, 12.0f)
            curveTo(15.18f, 12.55f, 15.63f, 13.0f, 16.18f, 13.0f)
            curveTo(16.73f, 13.0f, 17.18f, 12.55f, 17.18f, 12.0f)
            curveTo(17.18f, 11.45f, 16.73f, 11.0f, 16.18f, 11.0f)
            close()
            moveTo(4.0f, 17.0f)
            lineTo(8.36f, 17.0f)
            curveTo(8.78f, 15.84f, 9.88f, 15.0f, 11.18f, 15.0f)
            curveTo(12.48f, 15.0f, 13.58f, 15.84f, 14.0f, 17.0f)
            lineTo(20.0f, 17.0f)
            lineTo(20.0f, 19.0f)
            lineTo(14.0f, 19.0f)
            curveTo(13.58f, 20.16f, 12.48f, 21.0f, 11.18f, 21.0f)
            curveTo(9.88f, 21.0f, 8.78f, 20.16f, 8.36f, 19.0f)
            lineTo(4.0f, 19.0f)
            close()
            moveTo(11.18f, 17.0f)
            curveTo(10.63f, 17.0f, 10.18f, 17.45f, 10.18f, 18.0f)
            curveTo(10.18f, 18.55f, 10.63f, 19.0f, 11.18f, 19.0f)
            curveTo(11.73f, 19.0f, 12.18f, 18.55f, 12.18f, 18.0f)
            curveTo(12.18f, 17.45f, 11.73f, 17.0f, 11.18f, 17.0f)
            close()
        }
    }.build()

internal val BackIcon: ImageVector =
    ImageVector.Builder(
        name = "Back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10.2f, 4.4f)
            lineTo(11.6f, 5.8f)
            lineTo(7.4f, 10.0f)
            lineTo(20.0f, 10.0f)
            lineTo(20.0f, 12.0f)
            lineTo(7.4f, 12.0f)
            lineTo(11.6f, 16.2f)
            lineTo(10.2f, 17.6f)
            lineTo(3.6f, 11.0f)
            close()
        }
    }.build()
