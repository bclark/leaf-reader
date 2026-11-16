package net.digitalbc.leafreader.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Before fun prepareIsolatedLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = requireNotNull(instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE))
        instrumentation.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        LeafJourneys(UiDevice.getInstance(instrumentation)).apply {
            prepareFixtures()
            openFixture()
            turnPageAndReturn()
        }
    }

    @Test fun coldStartupWithoutCompilation() = startup(CompilationMode.None())
    @Test fun coldStartupWithRequiredProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilation: CompilationMode) = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        LeafJourneys(device).populatedLibraryReady()
    }

    @Test fun localReaderAndLibraryFrames() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            LeafJourneys(device).prepareFixtures()
        },
    ) {
        LeafJourneys(device).apply {
            openFixture()
            turnPageAndReturn()
            continueFixture()
            scrollLibrary()
        }
    }
}
