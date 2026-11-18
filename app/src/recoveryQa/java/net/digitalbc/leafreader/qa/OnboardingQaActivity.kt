package net.digitalbc.leafreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import net.digitalbc.leafreader.data.settings.FirstLaunchState
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.ui.onboarding.FirstLaunchScreen
import net.digitalbc.leafreader.ui.onboarding.FirstLaunchUi
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme

/** Synthetic visual fixture only. Never compiled into debug, release or performance variants. */
class OnboardingQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val optional = intent.getBooleanExtra("optional", false)
        val dark = intent.getBooleanExtra("dark", true)
        val fontScale = intent.getFloatExtra("fontScale", 1f).coerceIn(1f, 2f)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ComicReaderTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    amoled = intent.getBooleanExtra("amoled", false)) {
                    FirstLaunchScreen(FirstLaunchUi(if (optional) FirstLaunchState.OPTIONAL_JELLYFIN else FirstLaunchState.WELCOME,
                        busy = false), 0f, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }
}
