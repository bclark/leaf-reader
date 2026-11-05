package net.digitalbc.leafreader.data.settings

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun usesDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

enum class LauncherIcon {
    LIGHT, DARK
}
