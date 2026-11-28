import XCTest
import UIKit

/// Runs only on an owned simulator containing the generated 20-page QA Scrubber comic.
final class ReaderScrubberUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        try XCTSkipUnless((ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? "").hasPrefix("Leaf Reader QA "))
    }

    private func launch(page: Int? = 0, theme: String = "light") -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-leaf.appTheme", theme,
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
                               "-leaf.portraitSpread", "NO", "-leaf.landscapeSpread", "NO"]
        app.launchEnvironment = ["LEAF_DEBUG_OPEN": "QA Scrubber", "LEAF_DEBUG_RTL": "0"]
        if let page { app.launchEnvironment["LEAF_DEBUG_PAGE"] = String(page) }
        app.launch()
        XCTAssertTrue(app.otherElements["ReaderCanvas"].waitForExistence(timeout: 15))
        return app
    }

    private func centerTap(_ app: XCUIApplication) {
        app.otherElements["ReaderCanvas"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        // Single-tap recognition and the 0.2s controls transition are asynchronous.
        Thread.sleep(forTimeInterval: 0.6)
    }

    private func assertPage(_ page: Int, app: XCUIApplication, capture name: String) {
        let slider = app.descendants(matching: .any).matching(identifier: "ComicScrubberSlider").firstMatch
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = name + "-hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = name; image.lifetime = .keepAlways; add(image)
        XCTAssertTrue(slider.waitForExistence(timeout: 5))
        XCTAssertEqual(slider.value as? String, "Page \(page)/20")
        let status = app.staticTexts["PageStatus"]
        XCTAssertTrue(status.exists)
        XCTAssertEqual(app.staticTexts["PageCoinCurrentPage"].label, String(page))
    }

    func testFullscreenTurnsSynchronizeFirstAndRepeatedControlsReveal() {
        let app = launch()
        let canvas = app.otherElements["ReaderCanvas"]
        for _ in 0..<12 {
            canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
            // Let the single-tap recognizer and 0.26s curl settle before the next gesture.
            Thread.sleep(forTimeInterval: 0.6)
        }
        centerTap(app)
        assertPage(13, app: app, capture: "first-reveal-page13")
        centerTap(app)
        XCTAssertFalse(app.staticTexts["PageStatus"].exists)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
            // Let the single-tap recognizer and 0.26s curl settle before the next gesture.
            Thread.sleep(forTimeInterval: 0.6)
        centerTap(app)
        assertPage(14, app: app, capture: "repeated-reveal-page14")
    }

    func testRestoredPageRotationAndSliderCommitStaySynchronized() {
        let app = launch(page: 6, theme: "dark")
        centerTap(app)
        assertPage(7, app: app, capture: "restored-page7-dark")
        XCUIDevice.shared.orientation = .landscapeLeft
        assertPage(7, app: app, capture: "landscape-page7")
        XCUIDevice.shared.orientation = .portrait
        let slider = app.descendants(matching: .any).matching(identifier: "ComicScrubberSlider").firstMatch
        slider.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5))
            .press(forDuration: 0.1, thenDragTo: slider.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.5)))
        assertPage(15, app: app, capture: "slider-commit-page15")
        centerTap(app); centerTap(app)
        assertPage(15, app: app, capture: "slider-target-after-reveal")
        XCUIDevice.shared.press(.home)
        app.terminate()
        let restored = launch(page: nil, theme: "dark")
        centerTap(restored)
        assertPage(15, app: restored, capture: "cold-relaunch-saved-page15")
        XCUIDevice.shared.orientation = .portrait
    }

    func testSpreadAndRotationRevealMatchesPageCoin() {
        let app = launch(page: 12)
        centerTap(app)
        assertPage(13, app: app, capture: "single-restored-page13")
        app.buttons["SpreadChip"].tap()
        assertPage(13, app: app, capture: "spread-page13")
        centerTap(app)
        app.otherElements["ReaderCanvas"].coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
        Thread.sleep(forTimeInterval: 0.6)
        centerTap(app)
        assertPage(14, app: app, capture: "spread-hidden-turn-page14")
        XCUIDevice.shared.orientation = .landscapeLeft
        assertPage(14, app: app, capture: "spread-rotation-page14")
        XCUIDevice.shared.orientation = .portrait
    }

}
