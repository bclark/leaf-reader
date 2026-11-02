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
            val stored = mutableMapOf("landscape_spread" to oldChoice)
            val settings = AppSettings(preferences(stored))
            assertEquals(oldChoice, settings.landscapeSpread)
            assertFalse(settings.portraitSpread)
            assertFalse(stored.containsKey("portrait_spread"))
            assertEquals(mapOf("landscape_spread" to oldChoice), stored)
        }
    }

    @Test fun bothChoicesPersistIndependentlyWhenSettingsAreRecreated() {
        val stored = mutableMapOf<String, Boolean>()
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
        val stored = mutableMapOf("landscape_spread" to true)
        val settings = AppSettings(preferences(stored))
        settings.portraitSpread = true
        settings.portraitSpread = false
        assertTrue(AppSettings(preferences(stored)).landscapeSpread)
        assertFalse(AppSettings(preferences(stored)).portraitSpread)
    }

    // Exercise the real settings getters/writes against a shared backing store, without an Android runtime.
    private fun preferences(stored: MutableMap<String, Boolean>): SharedPreferences {
        val pending = mutableMapOf<String, Boolean>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putBoolean" -> { pending[args[0] as String] = args[1] as Boolean; editor }
                "apply", "commit" -> { stored.putAll(pending); pending.clear(); if (method.name == "commit") true else null }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean" -> stored[args[0] as String] ?: args[1] as Boolean
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
