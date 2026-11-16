package net.digitalbc.leafreader.data.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.IOException

enum class FirstLaunchState { WELCOME, OPTIONAL_JELLYFIN, COMPLETE }

/** Installation-local state. Call off the main thread, before opening Room or migrating settings. */
class FirstLaunchStateStore internal constructor(
    private val preferences: SharedPreferences,
    private val hasExistingInstall: () -> Boolean,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
        { FirstLaunchStateStore.hasExistingInstall(context) },
    )

    private var current: FirstLaunchState? = null

    @Synchronized
    fun initialize(): FirstLaunchState {
        current?.let { return it }
        val saved = preferences.getString(STATE, null)
        val initial = if (saved == null) {
            if (hasExistingInstall()) FirstLaunchState.COMPLETE else FirstLaunchState.WELCOME
        } else {
            try {
                FirstLaunchState.valueOf(saved)
            } catch (error: IllegalArgumentException) {
                // Preserve an unknown/newer marker; startup can retain ordinary Library access.
                throw IOException("Unrecognized first-launch state", error)
            }
        }
        // Recommit even a saved value: a previous failed commit may have changed only memory.
        return persist(initial)
    }

    /** Complete is terminal; stale Back/setup callbacks cannot reopen onboarding. */
    @Synchronized
    fun transitionTo(next: FirstLaunchState): FirstLaunchState {
        if (initialize() == FirstLaunchState.COMPLETE) return FirstLaunchState.COMPLETE
        return persist(next)
    }

    private fun persist(state: FirstLaunchState): FirstLaunchState {
        if (!preferences.edit().putString(STATE, state.name).commit()) {
            throw IOException("First-launch state could not be saved")
        }
        current = state
        return state
    }

    internal companion object {
        // Separate from leaf.settings so this marker cannot suppress legacy settings migration.
        const val PREFERENCES = "leaf.onboarding"
        const val STATE = "state"

        @Suppress("DEPRECATION")
        fun hasExistingInstall(context: Context): Boolean = try {
            val database = context.getDatabasePath("comics.db")
            val databaseExists = listOf("", "-wal", "-shm", "-journal")
                .any { File(database.path + it).exists() }
            val preferencesExist = listOf("leaf.settings", "chika.settings").any { name ->
                File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists() ||
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()
            }
            val install = context.packageManager.getPackageInfo(context.packageName, 0)
            databaseExists || preferencesExist || install.firstInstallTime <= 0 ||
                install.lastUpdateTime != install.firstInstallTime
        } catch (_: Exception) {
            // Ambiguous install evidence must never force an existing reader into Welcome.
            true
        }
    }
}
