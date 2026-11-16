package net.digitalbc.leafreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "net.digitalbc.leafreader.benchmark"
private val fixture = By.text(Pattern.compile("LEAF R8 FIXTURE [0-9]{2}", Pattern.CASE_INSENSITIVE))

/** Real UI/SAF journeys on the separate benchmark install; never seed/reset an owner's library. */
internal class LeafJourneys(private val device: UiDevice) {
    fun libraryReady() {
        assertTrue("Library toolbar did not appear", device.wait(Until.hasObject(By.desc("Menu")), 15_000))
    }

    fun prepareFixtures() {
        check(InstrumentationRegistry.getArguments().getString("leaf.qaFixtures") == "true") {
            "Use the documented isolated benchmark install and pass leaf.qaFixtures=true."
        }
        libraryReady()
        // All is a user-visible filter; no production-only automation hooks are introduced.
        device.findObject(By.text(Pattern.compile("ALL", Pattern.CASE_INSENSITIVE)))?.click()
        if (device.hasObject(fixture)) return
        check(device.hasObject(By.text("YOUR COMICS, YOUR WAY"))) {
            "Refusing fixture setup in a nonempty library. Use the separate empty benchmark app."
        }
        val load = device.findObject(By.desc("Load comic"))
            ?: device.findObject(By.text(Pattern.compile(".*(ADD|LOAD).*COMIC.*", Pattern.CASE_INSENSITIVE)))
        requireNotNull(load) { "Load comic action unavailable" }.click()
        requireNotNull(device.wait(Until.findObject(By.text("FROM DEVICE (FOLDER)")), 5_000)).click()
        device.waitForIdle()
        device.findObject(By.desc("Show roots"))?.click()
        requireNotNull(device.wait(Until.findObject(By.text("Downloads")), 5_000)).click()
        requireNotNull(device.wait(Until.findObject(By.text("LeafR8Fixtures")), 5_000)).click()
        requireNotNull(device.wait(Until.findObject(By.text("USE THIS FOLDER")), 5_000)).click()
        device.wait(Until.findObject(By.text("ALLOW")), 5_000)?.click()
        assertTrue("Fixture import did not finish; push the generated folder into Downloads first",
            device.wait(Until.hasObject(fixture), 90_000))
        assertTrue("Fixture import is still running", device.wait(Until.gone(By.desc("Cancel import")), 90_000))
        device.waitForIdle()
    }

    fun continueFixture() {
        val card = requireNotNull(device.wait(Until.findObject(By.descStartsWith("Continue reading, Leaf R8 Fixture")), 10_000))
        card.click()
        assertTrue("Continue did not open Reader", device.wait(Until.gone(By.desc("Menu")), 10_000))
        device.pressBack()
        libraryReady()
    }

    fun openFixture() {
        libraryReady()
        requireNotNull(device.wait(Until.findObject(fixture), 10_000)).click()
        device.waitForIdle()
        assertTrue("Reader did not replace Library", device.wait(Until.gone(By.desc("Menu")), 10_000))
    }

    fun turnPageAndReturn() {
        device.click(device.displayWidth / 10, device.displayHeight / 2)
        device.waitForIdle()
        device.click(device.displayWidth * 9 / 10, device.displayHeight / 2)
        device.waitForIdle()
        device.pressBack()
        libraryReady()
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
