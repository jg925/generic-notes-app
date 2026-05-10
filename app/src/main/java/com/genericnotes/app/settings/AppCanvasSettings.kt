package com.genericnotes.app.settings

import android.content.Context
import com.genericnotes.app.canvas.DrawingTool
import com.genericnotes.app.canvas.drawingToolFromSerializedName
import com.genericnotes.app.canvas.serializedName

internal data class AppCanvasSettings(
    val isLocked: Boolean = false,
    val selectedTool: DrawingTool = DrawingTool.Pen,
    val ignoreTouchInput: Boolean = false,
)

private const val AppSettingsPreferencesName = "generic_notes_app_settings"
private const val IsLockedKey = "isLocked"
private const val SelectedToolKey = "selectedTool"
private const val IgnoreTouchInputKey = "ignoreTouchInput"

internal fun Context.loadAppCanvasSettings(): AppCanvasSettings {
    val preferences = appSettingsPreferences()
    val selectedTool = drawingToolFromSerializedName(
        preferences.getString(SelectedToolKey, DrawingTool.Pen.serializedName).orEmpty(),
    ) ?: DrawingTool.Pen

    return AppCanvasSettings(
        isLocked = preferences.getBoolean(IsLockedKey, false),
        selectedTool = selectedTool,
        ignoreTouchInput = preferences.getBoolean(IgnoreTouchInputKey, false),
    )
}

internal fun Context.saveAppCanvasSettings(settings: AppCanvasSettings) {
    appSettingsPreferences()
        .edit()
        .putBoolean(IsLockedKey, settings.isLocked)
        .putString(SelectedToolKey, settings.selectedTool.serializedName)
        .putBoolean(IgnoreTouchInputKey, settings.ignoreTouchInput)
        .apply()
}

private fun Context.appSettingsPreferences() =
    getSharedPreferences(AppSettingsPreferencesName, Context.MODE_PRIVATE)
