package com.genericnotes.app.canvas

internal enum class PageScrollDirection(val serializedName: String) {
    Vertical("vertical"),
    Horizontal("horizontal"),
}

internal enum class PageDisplayMode(val serializedName: String) {
    Seamless("seamless"),
    Split("split"),
}

internal data class NotePageLayout(
    val pageCount: Int = 1,
    val scrollDirection: PageScrollDirection? = null,
    val displayMode: PageDisplayMode = PageDisplayMode.Seamless,
    val pageWidthPx: Int? = null,
    val pageHeightPx: Int? = null,
) {
    val normalizedPageCount: Int
        get() = pageCount.coerceAtLeast(1)
}

internal fun pageScrollDirectionFromSerializedName(name: String?): PageScrollDirection? =
    PageScrollDirection.entries.firstOrNull { it.serializedName == name }

internal fun pageDisplayModeFromSerializedName(name: String?): PageDisplayMode =
    PageDisplayMode.entries.firstOrNull { it.serializedName == name } ?: PageDisplayMode.Seamless
