package com.genericnotes.app.hwdn

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject

internal const val MaxRecentHwdnFiles = 5

internal data class RecentHwdnFile(
    val uri: Uri,
    val displayName: String,
    val locationHint: String,
    val openedAtMillis: Long,
)

private const val RecentFilesPreferencesName = "recent_hwdn_files"
private const val RecentFilesKey = "files"

internal fun Context.loadRecentHwdnFiles(): List<RecentHwdnFile> {
    val rawRecentFiles = recentFilesPreferences().getString(RecentFilesKey, null) ?: return emptyList()

    return runCatching {
        val recentFiles = JSONArray(rawRecentFiles)
        buildList {
            for (index in 0 until recentFiles.length()) {
                recentFiles.optJSONObject(index)?.toRecentHwdnFile()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
        .distinctBy { it.uri }
        .sortedByDescending { it.openedAtMillis }
        .take(MaxRecentHwdnFiles)
}

internal fun Context.rememberRecentHwdnFile(uri: Uri, fallbackDisplayName: String) {
    val displayName = displayNameFor(uri)
        ?: fallbackDisplayName.toHwdnDisplayName()
    val recentFile = RecentHwdnFile(
        uri = uri,
        displayName = displayName,
        locationHint = storageLocationHintFor(uri),
        openedAtMillis = System.currentTimeMillis(),
    )
    val recentFiles = listOf(recentFile) + loadRecentHwdnFiles().filterNot { it.uri == uri }

    saveRecentHwdnFiles(recentFiles.take(MaxRecentHwdnFiles))
}

internal fun Context.forgetRecentHwdnFile(uri: Uri) {
    saveRecentHwdnFiles(loadRecentHwdnFiles().filterNot { it.uri == uri })
}

private fun Context.saveRecentHwdnFiles(recentFiles: List<RecentHwdnFile>) {
    val recentFilesJson = JSONArray().apply {
        recentFiles.take(MaxRecentHwdnFiles).forEach { recentFile ->
            put(recentFile.toJson())
        }
    }

    recentFilesPreferences()
        .edit()
        .putString(RecentFilesKey, recentFilesJson.toString())
        .apply()
}

private fun Context.recentFilesPreferences() =
    getSharedPreferences(RecentFilesPreferencesName, Context.MODE_PRIVATE)

private fun RecentHwdnFile.toJson(): JSONObject =
    JSONObject()
        .put("uri", uri.toString())
        .put("displayName", displayName)
        .put("locationHint", locationHint)
        .put("openedAtMillis", openedAtMillis)

private fun JSONObject.toRecentHwdnFile(): RecentHwdnFile? {
    val uri = optString("uri").trim().takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
    val displayName = optString("displayName").trim().takeIf { it.isNotBlank() } ?: return null
    val locationHint = optString("locationHint").trim().takeIf { it.isNotBlank() } ?: return null

    return RecentHwdnFile(
        uri = uri,
        displayName = displayName,
        locationHint = locationHint,
        openedAtMillis = optLong("openedAtMillis", 0L),
    )
}

private fun String.toHwdnDisplayName(): String {
    val trimmedName = trim().ifBlank { "Untitled Note" }
    return if (trimmedName.endsWith(HwdnExtension, ignoreCase = true)) {
        trimmedName
    } else {
        "$trimmedName$HwdnExtension"
    }
}

private fun Context.storageLocationHintFor(uri: Uri): String =
    documentLocationHintFor(uri)
        ?: uri.authority?.knownProviderHint()
        ?: uri.authority?.readableAuthorityHint()
        ?: "Unknown location"

private fun Context.documentLocationHintFor(uri: Uri): String? {
    val authority = uri.authority ?: return null
    val documentId = runCatching {
        if (DocumentsContract.isDocumentUri(this, uri)) {
            DocumentsContract.getDocumentId(uri)
        } else {
            null
        }
    }.getOrNull() ?: return null

    return when (authority) {
        "com.android.externalstorage.documents" -> externalStorageLocationHint(documentId)
        "com.android.providers.downloads.documents" -> downloadsLocationHint(documentId)
        "com.android.providers.media.documents" -> mediaLocationHint(documentId)
        else -> null
    }
}

private fun externalStorageLocationHint(documentId: String): String {
    val volume = documentId.substringBefore(":").ifBlank { "Storage" }
    val path = documentId.substringAfter(":", missingDelimiterValue = "")
    val volumeHint = when (volume) {
        "home" -> "Documents"
        "primary" -> "Internal storage"
        else -> volume
    }
    val folders = parentFoldersFor(path)

    return (listOf(volumeHint) + folders).joinToString(" / ")
}

private fun downloadsLocationHint(documentId: String): String =
    if (documentId.startsWith("raw:")) {
        pathLocationHint(documentId.removePrefix("raw:")) ?: "Downloads"
    } else {
        "Downloads"
    }

private fun mediaLocationHint(documentId: String): String {
    val mediaType = documentId.substringBefore(":").ifBlank { "Media" }
    return when (mediaType) {
        "audio" -> "Audio"
        "image" -> "Images"
        "video" -> "Videos"
        else -> "Media"
    }
}

private fun pathLocationHint(path: String): String? {
    val folders = parentFolderSegmentsFor(path.trim('/'))
    if (folders.isEmpty()) return null

    val storagePrefix = listOf("storage", "emulated", "0")
    return if (folders.take(storagePrefix.size) == storagePrefix) {
        (listOf("Internal storage") + folders.drop(storagePrefix.size).takeLast(2)).joinToString(" / ")
    } else {
        folders.takeLast(3).joinToString(" / ")
    }
}

private fun parentFoldersFor(path: String): List<String> {
    return parentFolderSegmentsFor(path).takeLast(2)
}

private fun parentFolderSegmentsFor(path: String): List<String> {
    val parentPath = path.substringBeforeLast("/", missingDelimiterValue = "")
    return parentPath
        .split("/")
        .map(String::trim)
        .filter(String::isNotBlank)
}

private fun String.knownProviderHint(): String? =
    when (this) {
        "com.android.externalstorage.documents" -> "Device storage"
        "com.android.providers.downloads.documents" -> "Downloads"
        "com.android.providers.media.documents" -> "Media"
        "com.google.android.apps.docs.storage",
        "com.google.android.apps.docs.storage.legacy",
        -> "Google Drive"
        "com.microsoft.skydrive.content.StorageAccessProvider" -> "OneDrive"
        "com.box.android.documents" -> "Box"
        "com.dropbox.android.FileCache" -> "Dropbox"
        else -> null
    }

private fun String.readableAuthorityHint(): String {
    val ignoredSegments = setOf("android", "content", "documents", "provider", "providers", "storage")
    val readableSegments = split(".")
        .map(String::trim)
        .filter { it.isNotBlank() && it !in ignoredSegments && it != "com" }

    return readableSegments
        .takeLast(2)
        .joinToString(" ") { segment -> segment.replaceFirstChar { char -> char.uppercase() } }
        .ifBlank { this }
}
