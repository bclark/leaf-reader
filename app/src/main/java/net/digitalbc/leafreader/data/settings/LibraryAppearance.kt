package net.digitalbc.leafreader.data.settings

/** Presentation only: never changes reading state, source identity or filtering. */
data class LibraryAppearance(
    val showTitles: Boolean = true,
    val showDetails: Boolean = false,
    val showReadingIndicators: Boolean = true,
)
