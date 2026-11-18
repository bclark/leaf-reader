package net.digitalbc.leafreader

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.digitalbc.leafreader.ui.nav.AppNavHost
import net.digitalbc.leafreader.data.settings.LauncherIcons
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        finishStartup(application as ComicReaderApp)
    }

    private fun finishStartup(app: ComicReaderApp) {
        lifecycleScope.launch {
            try {
                // Decide eligibility before recovery opens Room and before legacy settings migrate.
                withContext(Dispatchers.IO) { app.firstLaunchState.initialize() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("LeafStartup", "First-launch state unavailable; continuing Library startup", error)
            }
            val recovery = app.libraryRepository.recoverPendingBackupPreferences()
            if (recovery.isFailure) {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Finish restoring Leaf")
                    .setMessage("Leaf couldn't finish loading your saved preferences. Retry before opening your library.")
                    .setPositiveButton("Retry") { _, _ -> finishStartup(app) }
                    .setNegativeButton("Close") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
                return@launch
            }
            app.refreshRestoredPreferences()
            showLibrary(app)
        }
    }

    private fun showLibrary(app: ComicReaderApp) {
        LauncherIcons.apply(this, app.settings.launcherIcon)
        setContent {
            val amoled by app.amoledTheme.collectAsState()
            val mode by app.themeMode.collectAsState()
            ComicReaderTheme(amoled = amoled, mode = mode) {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                SideEffect {
                    enableEdgeToEdge(
                        // Every top app bar retains Leaf's dark forest background.
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
