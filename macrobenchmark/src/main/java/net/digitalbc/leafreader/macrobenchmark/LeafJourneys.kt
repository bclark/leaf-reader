package net.digitalbc.leafreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.waitForStable
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "net.digitalbc.leafreader.benchmark"
private val fixture = By.text(Pattern.compile("LEAF R8 FIXTURE [0-9]{2}", Pattern.CASE_INSENSITIVE))

/** Real UI/SAF journeys on the separate benchmark install; never seed/reset an owner's library. */
internal class LeafJourneys(private val device: UiDevice) {
    private fun expect(message: String, condition: Boolean) {
        if (!condition) {
            val hierarchy = ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            System.err.println("LEAF_JOURNEY_FAILURE " + message + " " + hierarchy.toString("UTF-8"))
        }
        assertTrue(message, condition)
    }

    fun libraryReady() {
        expect("Library toolbar did not appear", device.wait(Until.hasObject(By.desc("Menu")), 15_000))
    }

    fun populatedLibraryReady() {
        libraryReady()
        expect("Synthetic populated library is missing", device.wait(Until.hasObject(fixture), 10_000)
            || device.hasObject(By.descStartsWith("Continue reading, Leaf R8 Fixture")))
    }

    fun prepareFixtures() {
        check(InstrumentationRegistry.getArguments().getString("leaf.qaFixtures") == "true") {
            "Use the documented isolated benchmark install and pass leaf.qaFixtures=true."
        }
        libraryReady()
        // All is a user-visible filter; no production-only automation hooks are introduced.
        device.findObject(By.text(Pattern.compile("ALL", Pattern.CASE_INSENSITIVE)))?.click()
        if (device.hasObject(fixture)) {
            verifyFixtureCount()
            return
        }
        check(device.hasObject(By.text("YOUR COMICS, YOUR WAY"))) {
            "Refusing fixture setup in a nonempty library. Use the separate empty benchmark app."
        }
        val load = device.findObject(By.desc("Load comic"))
            ?: device.findObject(By.text(Pattern.compile("(?:ADD COMICS|LOAD COMIC)", Pattern.CASE_INSENSITIVE)))
        requireNotNull(load) { "Load comic action unavailable" }.click()
        requireNotNull(device.wait(Until.findObject(By.text("FROM DEVICE (FOLDER)")), 5_000)).click()
        device.waitForIdle()
        device.findObject(By.desc("Show roots"))?.click()
        requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("Downloads?"))), 5_000)).click()
        requireNotNull(device.wait(Until.findObject(By.text("LeafR8Fixtures")), 5_000)).click()
        requireNotNull(device.wait(Until.findObject(By.text("USE THIS FOLDER")), 5_000)).click()
        device.wait(Until.findObject(By.text("ALLOW")), 5_000)?.click()
        expect("Fixture import did not finish; push the generated folder into Downloads first",
            device.wait(Until.hasObject(fixture), 90_000))
        expect("Fixture import is still running", device.wait(Until.gone(By.desc("Cancel import")), 90_000))
        device.wait(Until.findObject(By.text(Pattern.compile("ALL", Pattern.CASE_INSENSITIVE))), 5_000)?.click()
        device.waitForIdle()
        verifyFixtureCount()
    }

    private fun verifyFixtureCount() {
        requireNotNull(device.findObject(By.desc("Menu"))).click()
        requireNotNull(device.wait(Until.findObject(By.text("Select comics")), 5_000)).click()
        requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("SELECT ALL", Pattern.CASE_INSENSITIVE))), 5_000)).click()
        expect("Expected exactly 24 synthetic comics", device.wait(Until.hasObject(By.text("24 SELECTED")), 5_000))
        requireNotNull(device.findObject(By.text(Pattern.compile("DONE", Pattern.CASE_INSENSITIVE)))).click()
        libraryReady()
    }

    fun continueFixture() {
        expect("Expected saved page 2 before Continue", device.wait(Until.hasObject(
            By.desc(Pattern.compile("Continue reading, Leaf R8 Fixture [0-9]{2}, Page 2 of 8"))), 5_000))
        val card = requireNotNull(device.wait(Until.findObject(By.descStartsWith("Continue reading, Leaf R8 Fixture")), 10_000))
        val action = if (card.isClickable) card else requireNotNull(card.parent)
        check(action.isClickable) { "Continue semantics have no clickable card parent" }
        expect("Continue card did not settle", !action.waitForStable().isTimeout)
        System.err.println("LEAF_CONTINUE_TARGET " + action.visibleBounds)
        action.click()
        expect("Continue did not open Reader", device.wait(Until.gone(By.desc("Menu")), 10_000))
        readerControls()
        returnToLibrary()
    }

    fun openFixture() {
        libraryReady()
        // A process restart restores Series; matching titles there are not Reader entries.
        requireNotNull(device.findObject(By.text(Pattern.compile("ALL", Pattern.CASE_INSENSITIVE)))).click()
        requireNotNull(device.wait(Until.findObject(fixture), 10_000)).click()
        device.waitForIdle()
        expect("Reader did not replace Library", device.wait(Until.gone(By.desc("Menu")), 10_000))
    }

    // Handle only Android's first-run immersive hint, outside collection/measurement.
    fun dismissReaderHint() {
        device.wait(Until.findObject(By.res("com.android.systemui", "ok").text("Got it")), 3_000)?.click()
        device.waitForIdle()
    }

    private fun readerControls() {
        if (!device.hasObject(By.desc("Next page"))) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        expect("Expected the eight-page fixture Reader, not Series detail",
            device.wait(Until.hasObject(By.desc("Next page")), 5_000))
    }

    private fun clickControl(description: String) {
        var action = requireNotNull(device.wait(Until.findObject(By.desc(description)), 5_000))
        while (!action.isClickable) action = requireNotNull(action.parent)
        check(action.isEnabled) { "$description action disabled" }
        action.click()
    }

    fun turnPageAndReturn() {
        readerControls()
        if (device.hasObject(By.text("Page 2/8"))) {
            clickControl("Previous page")
        }
        expect("Expected fixture page 1", device.wait(Until.hasObject(By.text("Page 1/8")), 5_000))
        clickControl("Next page")
        expect("Next did not reach fixture page 2", device.wait(Until.hasObject(By.text("Page 2/8")), 5_000))
        returnToLibrary()
    }

    private fun returnToLibrary() {
        // Exercise Leaf's explicit Reader Back action; platform Back is separate acceptance.
        if (!device.hasObject(By.desc("Back"))) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        requireNotNull(device.wait(Until.findObject(By.desc("Back")), 5_000)) {
            "Reader Back action unavailable"
        }
        clickControl("Back")
        libraryReady()
        val root = requireNotNull(device.findObject(By.pkg(TARGET_PACKAGE)))
        expect("Library did not settle after Reader", !root.waitForStable().isTimeout)
    }

    fun scrollLibrary() {
        repeat(3) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                device.displayWidth / 2, device.displayHeight / 3, 25)
        }
        repeat(3) {
            device.swipe(device.displayWidth / 2, device.displayHeight / 3,
                device.displayWidth / 2, device.displayHeight * 4 / 5, 25)
        }
        device.waitForIdle()
    }
}
