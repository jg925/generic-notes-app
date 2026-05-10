package com.genericnotes.app.ui

import android.content.Context
import androidx.compose.ui.graphics.Color

internal enum class AppAestheticColor(
    val storageKey: String,
    val label: String,
    val color: Color,
) {
    // TODO: decide if I want to keep the label text. Pick the colors I actually want on the palette.
    Ink("black", "Black", Color(0xFF111111)),
    Red("red", "Red", Color(0xFFB42318)),
    Blue("blue", "Blue", Color(0xFF1D4ED8)),
    Green("green", "Green", Color(0xFF166534)),
    Purple("violet", "Violet", Color(0xFF6D28D9)),
}

internal fun Context.loadAppAestheticColor(): AppAestheticColor {
    val savedKey = appSettingsPrefs().getString(AppAestheticColorKey, AppAestheticColor.Ink.storageKey)
    return AppAestheticColor.values().firstOrNull { it.storageKey == savedKey } ?: AppAestheticColor.Ink
}

internal fun Context.saveAppAestheticColor(aestheticColor: AppAestheticColor) {
    appSettingsPrefs()
        .edit()
        .putString(AppAestheticColorKey, aestheticColor.storageKey)
        .apply()
}

private fun Context.appSettingsPrefs() =
    getSharedPreferences(AppSettingsPreferencesName, Context.MODE_PRIVATE)

private const val AppSettingsPreferencesName = "generic_notes_app_settings"
private const val AppAestheticColorKey = "app_aesthetic_color"
