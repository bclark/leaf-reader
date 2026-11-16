package net.digitalbc.leafreader.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class FirstLaunchStateStoreTest {
    private lateinit var context: Context

    @Before
    fun freshInstallation() {
        context = RuntimeEnvironment.getApplication()
        listOf(FirstLaunchStateStore.PREFERENCES, "leaf.settings", "chika.settings").forEach {
            context.deleteSharedPreferences(it)
        }
        context.deleteDatabase("comics.db")
        listOf("-wal", "-shm", "-journal").forEach {
            File(context.getDatabasePath("comics.db").path + it).delete()
        }
        installTimes(1_000, 1_000)
    }

    private fun installTimes(first: Long, updated: Long) {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).apply {
            firstInstallTime = first
            lastUpdateTime = updated
        }
    }

    private fun preferences() = context.getSharedPreferences(FirstLaunchStateStore.PREFERENCES, Context.MODE_PRIVATE)

    @Test
    fun freshDecisionIsSavedBeforeAnyDatabaseOrSettingsMigration() {
        assertEquals(FirstLaunchState.WELCOME, FirstLaunchStateStore(context).initialize())
        assertEquals("WELCOME", preferences().getString(FirstLaunchStateStore.STATE, null))
        assertFalse(context.getDatabasePath("comics.db").exists())
        for (name in listOf("leaf.settings", "chika.settings")) {
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
            assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
        }
    }

    @Test
    fun savedWelcomeSurvivesDatabaseCreationAndStoreRecreation() {
        assertEquals(FirstLaunchState.WELCOME, FirstLaunchStateStore(context).initialize())
        context.openOrCreateDatabase("comics.db", Context.MODE_PRIVATE, null).close()
        assertEquals(FirstLaunchState.WELCOME, FirstLaunchStateStore(context).initialize())
    }

    @Test
    fun existingEmptyAndPopulatedDatabasesBothSkipOnboardingWithoutChangingRows() {
        for (count in listOf(0, 1)) {
            context.deleteSharedPreferences(FirstLaunchStateStore.PREFERENCES)
            context.deleteDatabase("comics.db")
            context.openOrCreateDatabase("comics.db", Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TABLE comics (title TEXT, lastPage INTEGER)")
                if (count == 1) db.execSQL("INSERT INTO comics VALUES ('Fixture', 6)")
                assertEquals(FirstLaunchState.COMPLETE, FirstLaunchStateStore(context).initialize())
                db.rawQuery("SELECT COUNT(*) FROM comics", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(count, it.getInt(0))
                }
                if (count == 1) db.rawQuery("SELECT lastPage FROM comics", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(6, it.getInt(0))
                }
            }
        }
    }

    @Test
    fun databaseRecoverySidecarsAreExistingInstallEvidence() {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val file = File(context.getDatabasePath("comics.db").path + suffix)
            file.parentFile!!.mkdirs()
            file.writeText("retained synthetic recovery evidence")
            assertTrue(FirstLaunchStateStore.hasExistingInstall(context))
            assertEquals("retained synthetic recovery evidence", file.readText())
            file.delete()
        }
    }

    @Test
    fun existingLeafPreferencesAndConfiguredServerArePreserved() {
        val prefs = context.getSharedPreferences("leaf.settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("library_columns", 5)
            .putString("jellyfin_url", "https://fixture.invalid")
            .putString("jellyfin_user_id", "fixture-user").commit()
        val before = prefs.all.toMap()
        assertEquals(FirstLaunchState.COMPLETE, FirstLaunchStateStore(context).initialize())
        assertEquals(before, prefs.all)
        assertEquals(5, AppSettings(context).libraryColumns)
        assertTrue(AppSettings(context).hasJellyfinConfig)
    }

    @Test
    fun separateMarkerDoesNotPreventLegacySettingsMigration() {
        val legacy = context.getSharedPreferences("chika.settings", Context.MODE_PRIVATE)
        legacy.edit().putInt("library_columns", 4).putBoolean("default_rtl", true).commit()
        val before = legacy.all.toMap()
        assertEquals(FirstLaunchState.COMPLETE, FirstLaunchStateStore(context).initialize())
        assertTrue(context.getSharedPreferences("leaf.settings", Context.MODE_PRIVATE).all.isEmpty())
        val migrated = AppSettings(context)
        assertEquals(4, migrated.libraryColumns)
        assertTrue(migrated.defaultRightToLeft)
        assertEquals(before, legacy.all)
    }

    @Test
    fun packageUpgradeWithoutOtherDataStillPreservesOrdinaryStartup() {
        installTimes(1_000, 2_000)
        assertEquals(FirstLaunchState.COMPLETE, FirstLaunchStateStore(context).initialize())
    }

    @Test
    fun ambiguousInstallTimesAreNeverClassifiedAsFresh() {
        for ((first, updated) in listOf(0L to 0L, -1L to -1L, 1_000L to 0L)) {
            installTimes(first, updated)
            assertTrue(FirstLaunchStateStore.hasExistingInstall(context))
        }
    }

    @Test
    fun unavailablePackageMetadataPreservesExistingReaderAccess() {
        val unknownPackage = object : ContextWrapper(context) {
            override fun getPackageName() = "fixture.not.installed"
        }
        assertEquals(FirstLaunchState.COMPLETE, FirstLaunchStateStore(unknownPackage).initialize())
    }

    @Test
    fun eachSavedDestinationTakesPrecedenceOverLaterInstallEvidence() {
        installTimes(1_000, 2_000)
        for (state in FirstLaunchState.entries) {
            preferences().edit().putString(FirstLaunchStateStore.STATE, state.name).commit()
            assertEquals(state, FirstLaunchStateStore(context).initialize())
        }
    }

    @Test
    fun optionalJellyfinAndBackPersistAcrossStoreRecreation() {
        val initial = FirstLaunchStateStore(context)
        assertEquals(FirstLaunchState.OPTIONAL_JELLYFIN, initial.transitionTo(FirstLaunchState.OPTIONAL_JELLYFIN))
        val reopened = FirstLaunchStateStore(context)
        assertEquals(FirstLaunchState.OPTIONAL_JELLYFIN, reopened.initialize())
        assertEquals(FirstLaunchState.WELCOME, reopened.transitionTo(FirstLaunchState.WELCOME))
        assertEquals(FirstLaunchState.WELCOME, FirstLaunchStateStore(context).initialize())
    }

    @Test
    fun completedOnboardingSurvivesRecreationAndRejectsStaleBackCallbacks() {
        assertEquals(FirstLaunchState.COMPLETE, FirstLaunchStateStore(context).transitionTo(FirstLaunchState.COMPLETE))
        val reopened = FirstLaunchStateStore(context)
        for (target in FirstLaunchState.entries) {
            assertEquals(FirstLaunchState.COMPLETE, reopened.transitionTo(target))
        }
        assertEquals("COMPLETE", preferences().getString(FirstLaunchStateStore.STATE, null))
    }

    @Test
    fun unknownMarkerIsReportedWithoutOverwritingItOrCreatingDatabase() {
        preferences().edit().putString(FirstLaunchStateStore.STATE, "NEWER_DESTINATION").commit()
        assertThrows(IOException::class.java) { FirstLaunchStateStore(context).initialize() }
        assertEquals("NEWER_DESTINATION", preferences().getString(FirstLaunchStateStore.STATE, null))
        assertFalse(context.getDatabasePath("comics.db").exists())
    }

    @Test
    fun failedInitialWritesCannotBeMistakenForPersistenceThroughInMemoryPreferences() {
        var fail = true
        val failing = failingPreferences { fail }
        repeat(2) {
            // Android preferences may change in memory even when commit reports failure.
            assertThrows(IOException::class.java) { FirstLaunchStateStore(failing) { false }.initialize() }
        }
        fail = false
        assertEquals(FirstLaunchState.WELCOME, FirstLaunchStateStore(failing) { false }.initialize())
        assertEquals(FirstLaunchState.WELCOME, FirstLaunchStateStore(context).initialize())
    }

    @Test
    fun failedTransitionKeepsTheLastConfirmedStateAndCanBeRetried() {
        var fail = false
        val store = FirstLaunchStateStore(failingPreferences { fail }) { false }
        assertEquals(FirstLaunchState.WELCOME, store.initialize())
        fail = true
        assertThrows(IOException::class.java) { store.transitionTo(FirstLaunchState.OPTIONAL_JELLYFIN) }
        assertEquals(FirstLaunchState.WELCOME, store.initialize())
        fail = false
        assertEquals(FirstLaunchState.OPTIONAL_JELLYFIN, store.transitionTo(FirstLaunchState.OPTIONAL_JELLYFIN))
        assertEquals(FirstLaunchState.OPTIONAL_JELLYFIN, FirstLaunchStateStore(context).initialize())
    }

    private fun failingPreferences(fail: () -> Boolean): SharedPreferences {
        val real = preferences()
        return object : SharedPreferences by real {
            override fun edit(): SharedPreferences.Editor {
                val editor = real.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
                    override fun commit(): Boolean = if (fail()) {
                        editor.apply()
                        false
                    } else editor.commit()
                }
            }
        }
    }
}
