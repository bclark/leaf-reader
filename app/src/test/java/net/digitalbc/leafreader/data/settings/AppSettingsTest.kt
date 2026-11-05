package net.digitalbc.leafreader.data.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
    @Test fun freshInstallKeepsBothOrientationsSingle() {
        val settings = AppSettings(preferences(mutableMapOf()))
        assertFalse(settings.portraitSpread)
        assertFalse(settings.landscapeSpread)
    }

    @Test fun legacyLandscapeChoiceIsRetainedWhilePortraitStaysSingle() {
        for (oldChoice in listOf(false, true)) {
            val stored = mutableMapOf<String, Any>("landscape_spread" to oldChoice)
            val settings = AppSettings(preferences(stored))
            assertEquals(oldChoice, settings.landscapeSpread)
            assertFalse(settings.portraitSpread)
            assertFalse(stored.containsKey("portrait_spread"))
            assertEquals(mapOf("landscape_spread" to oldChoice), stored)
        }
    }

    @Test fun bothChoicesPersistIndependentlyWhenSettingsAreRecreated() {
        val stored = mutableMapOf<String, Any>()
        for (portrait in listOf(false, true)) for (landscape in listOf(false, true)) {
            val settings = AppSettings(preferences(stored))
            settings.portraitSpread = portrait
            settings.landscapeSpread = landscape
            val reopened = AppSettings(preferences(stored))
            assertEquals(portrait, reopened.portraitSpread)
            assertEquals(landscape, reopened.landscapeSpread)
            assertEquals(portrait, stored["portrait_spread"])
            assertEquals(landscape, stored["landscape_spread"])
        }
    }

    @Test fun changingPortraitNeverChangesMigratedLandscapeSetting() {
        val stored = mutableMapOf<String, Any>("landscape_spread" to true)
        val settings = AppSettings(preferences(stored))
        settings.portraitSpread = true
        settings.portraitSpread = false
        assertTrue(AppSettings(preferences(stored)).landscapeSpread)
        assertFalse(AppSettings(preferences(stored)).portraitSpread)
    }

    @Test fun appearanceDefaultsFollowSystemAndPreserveExistingAmoledChoice() {
        assertEquals(ThemeMode.SYSTEM, AppSettings(preferences(mutableMapOf())).themeMode)
        val legacy = AppSettings(preferences(mutableMapOf("amoled_theme" to true)))
        assertEquals(ThemeMode.DARK, legacy.themeMode)
        assertTrue(legacy.amoledTheme)
    }

    @Test fun appearanceAndIconPersistIndependentlyAndInvalidValuesFallBack() {
        val stored = mutableMapOf<String, Any>()
        val settings = AppSettings(preferences(stored))
        settings.amoledTheme = true
        settings.themeMode = ThemeMode.LIGHT
        settings.launcherIcon = LauncherIcon.DARK
        val reopened = AppSettings(preferences(stored))
        assertEquals(ThemeMode.LIGHT, reopened.themeMode)
        assertEquals(LauncherIcon.DARK, reopened.launcherIcon)
        assertTrue(reopened.amoledTheme)
        stored["theme_mode"] = "future-theme"
        stored["launcher_icon"] = "future-icon"
        assertEquals(ThemeMode.SYSTEM, reopened.themeMode)
        assertEquals(LauncherIcon.LIGHT, reopened.launcherIcon)
    }

    @Test fun explicitModesOverrideSystemWhileSystemTracksIt() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemeMode.SYSTEM.usesDarkTheme(systemDark))
            assertFalse(ThemeMode.LIGHT.usesDarkTheme(systemDark))
            assertTrue(ThemeMode.DARK.usesDarkTheme(systemDark))
        }
    }

    // Exercise the real settings getters/writes against a shared backing store, without an Android runtime.
    private fun preferences(stored: MutableMap<String, Any>): SharedPreferences {
        val pending = mutableMapOf<String, Any>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putBoolean", "putString" -> { pending[args[0] as String] = args[1]; editor }
                "apply", "commit" -> { stored.putAll(pending); pending.clear(); if (method.name == "commit") true else null }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean", "getString" -> stored[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
