package net.digitalbc.leafreader.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val diagnostics = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            val hierarchy = ByteArrayOutputStream()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).dumpWindowHierarchy(hierarchy)
            System.err.println("LEAF_PROFILE_FAILURE_UI " + hierarchy.toString("UTF-8"))
        }
    }
    @get:Rule val profile = BaselineProfileRule()

    @Before fun prepareIsolatedLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = requireNotNull(instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE))
        instrumentation.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        LeafJourneys(UiDevice.getInstance(instrumentation)).apply {
            prepareFixtures()
            openFixture()
            dismissReaderHint()
            turnPageAndReturn()
        }
    }

    @Test fun startup() = profile.collect(TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        LeafJourneys(device).populatedLibraryReady()
    }

    @Test fun localReaderAndLibrary() = profile.collect(TARGET_PACKAGE, includeInStartupProfile = false) {
        startActivityAndWait()
        LeafJourneys(device).apply {
            populatedLibraryReady()
            openFixture()
            turnPageAndReturn()
            continueFixture()
            scrollLibrary()
        }
    }
}
