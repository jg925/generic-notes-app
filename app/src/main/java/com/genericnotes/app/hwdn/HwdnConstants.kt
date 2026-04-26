package com.genericnotes.app.hwdn

internal const val HwdnExtension = ".hwdn"
internal const val HwdnMimeType = "application/zip"
internal const val HwdnFormatVersion = "0.1.0"
internal const val MaxFileNameLength = 80
internal const val SourceApplicationName = "generic-notes-app"
internal const val SourceApplicationVersion = "0.1.0"

internal val HwdnOpenMimeTypes = arrayOf(
    HwdnMimeType,
    "application/x-zip-compressed",
    "application/octet-stream",
    "*/*",
)

private val InvalidFileNameCharactersRegex = Regex("""[\\/:*?"<>|]+""")
private val WhitespaceRegex = Regex("""\s+""")

internal fun String.withoutHwdnExtension(): String =
    if (endsWith(HwdnExtension, ignoreCase = true)) {
        dropLast(HwdnExtension.length)
    } else {
        this
    }

internal fun String.toHwdnFileName(): String {
    val baseName = withoutHwdnExtension()
        .trim()
        .replace(InvalidFileNameCharactersRegex, "-")
        .replace(WhitespaceRegex, " ")
        .ifBlank { "Untitled Note" }
        .take(MaxFileNameLength)
    return "$baseName$HwdnExtension"
}
