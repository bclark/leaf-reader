import XCTest

/// Requires the task-owned synthetic Header fixture; never runs against a personal library.
final class LibraryAppearanceUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    private func launch(darkLarge: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-leaf.appTheme", darkLarge ? "dark" : "light",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if darkLarge { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        XCTAssertTrue(app.buttons["MENU"].waitForExistence(timeout: 20))
        return app
    }
    private func appearance(_ app: XCUIApplication) {
        app.buttons["MENU"].tap(); app.buttons["Library appearance"].tap()
        XCTAssertTrue(app.switches["library.appearance.titles"].waitForExistence(timeout: 5))
    }
    private func set(_ app: XCUIApplication, _ key: String, _ on: Bool) {
        let control = app.switches["library.appearance." + key]
        if (control.value as? String == "1") != on { control.tap() }
        let value = NSPredicate(format: "value == %@", on ? "1" : "0")
        XCTAssertTrue(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: value, object: control)], timeout: 3) == .completed)
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = name; image.lifetime = .keepAlways; add(image)
    }
    private func choose(_ app: XCUIApplication, _ view: String) {
        let selector = app.segmentedControls["library.views"]
        if selector.exists { selector.buttons[view].tap() }
        else { app.buttons["library.views"].tap(); app.buttons[view].tap() }
    }
    func testEightCombinationsAndRelaunch() {
        let app = launch()
        for bits in 0..<8 {
            appearance(app)
            set(app, "titles", bits & 1 != 0); set(app, "details", bits & 2 != 0); set(app, "reading", bits & 4 != 0)
            capture(app, "appearance-controls-\(bits)")
            app.buttons["Done"].tap()
            XCTAssertTrue(app.buttons["QA Series"].waitForExistence(timeout: 5))
            capture(app, "appearance-library-\(bits)")
        }
        app.terminate(); app.launch()
        appearance(app)
        for key in ["titles", "details", "reading"] { XCTAssertEqual(app.switches["library.appearance." + key].value as? String, "1") }
        app.buttons["Done"].tap()
        app.terminate()
    }
    func testQuickDragsScrollHeldDragPeelsAndTapOpens() {
        let app = launch()
        appearance(app)
        for key in ["titles", "details", "reading"] { set(app, key, false) }
        app.buttons["Done"].tap()
        for (view, title) in [("Series", "QA Series"), ("Lists", "QA Reading Order")] {
            choose(app, view)
            let card = app.buttons[title]
            XCTAssertTrue(card.waitForExistence(timeout: 5) && card.isHittable)
            let initial = card.value as? String
            let frame = card.frame
            let origin = app.coordinate(withNormalizedOffset: .zero)
            let mid = origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.65))
            let upper = origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.2))
            mid.press(forDuration: 0.05, thenDragTo: upper)
            XCTAssertEqual(card.value as? String, initial, "Quick upward cover swipes must not peel")
            capture(app, view + "-quick-up")
            // Return to the beginning without targeting another session's scroll view.
            app.scrollViews.firstMatch.swipeDown()
            let f = card.frame
            let start = origin.withOffset(CGVector(dx: f.midX, dy: f.minY + f.height * 0.15))
            let end = origin.withOffset(CGVector(dx: f.midX, dy: f.minY + f.height * 0.85))
            start.press(forDuration: 0.05, thenDragTo: end)
            XCTAssertEqual(card.value as? String, initial, "Quick downward cover swipes must not peel")
            start.press(forDuration: 0.65, thenDragTo: end)
            let changed = NSPredicate(format: "value != %@", initial ?? "")
            expectation(for: changed, evaluatedWith: card)
            waitForExpectations(timeout: 5)
            capture(app, view + "-held-peel")
            card.tap()
            XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
            app.buttons["Back"].tap()
        }
        app.terminate()
    }
    func testLargeDarkControlsReachDoneAndSettingsEntry() {
        let app = launch(darkLarge: true)
        appearance(app)
        for key in ["titles", "details", "reading"] {
            let control = app.switches["library.appearance." + key]
            for _ in 0..<3 where !control.isHittable { app.swipeUp() }
            XCTAssertTrue(control.isHittable)
            set(app, key, false)
        }
        XCTAssertTrue(app.buttons["Done"].isHittable)
        capture(app, "appearance-dark-large")
        app.buttons["Done"].tap()
        app.buttons["MENU"].tap(); app.buttons["Settings and more"].tap()
        let titles = app.switches["library.appearance.titles"]
        for _ in 0..<8 where !titles.isHittable { app.swipeUp() }
        XCTAssertTrue(titles.exists && titles.isHittable)
        XCTAssertEqual(titles.value as? String, "0")
        capture(app, "appearance-settings-dark-large")
        app.terminate()
    }
    func testHiddenCaptionSelectionKeepsIdentityAndSelection() {
        let app = launch()
        appearance(app)
        for key in ["titles", "details", "reading"] { set(app, key, false) }
        app.buttons["Done"].tap()
        choose(app, "All")
        let item = app.buttons["QA Completed"]
        XCTAssertTrue(item.waitForExistence(timeout: 5) && item.isHittable)
        app.buttons["MENU"].tap(); app.buttons["Select comics"].tap()
        item.tap()
        XCTAssertTrue(item.isSelected, "Hidden captions must retain the selected trait on the named comic")
        item.tap()
        XCTAssertFalse(item.isSelected)
        app.terminate()
    }

    func testIncompleteHeldPeelReturnsWithoutAdvancing() {
        let app = launch()
        for (view, title) in [("Series", "QA Series"), ("Lists", "QA Reading Order")] {
            choose(app, view)
            let card = app.buttons[title]
            XCTAssertTrue(card.waitForExistence(timeout: 5) && card.isHittable)
            let initial = card.value as? String
            let frame = card.frame
            let origin = app.coordinate(withNormalizedOffset: .zero)
            let start = origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.2))
            let end = origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.28))
            start.press(forDuration: 0.65, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.3)
            XCTAssertEqual(card.value as? String, initial)
            capture(app, view + "-incomplete-peel")
            card.tap()
            XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
            app.buttons["Back"].tap()
        }
        app.terminate()
    }

    func testQuickDownwardCoverSwipeScrollsSameShelf() {
        let app = launch()
        choose(app, "All")
        let card = app.buttons["QA Series"]
        XCTAssertTrue(card.waitForExistence(timeout: 5) && card.isHittable)
        let initial = card.value as? String
        let origin = app.coordinate(withNormalizedOffset: .zero)
        let frame = card.frame
        origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.7))
            .press(forDuration: 0.05,
                   thenDragTo: origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.45)),
                   withVelocity: .slow, thenHoldForDuration: 0.4)
        let moved = card.frame.intersection(app.scrollViews.firstMatch.frame)
        XCTAssertGreaterThan(moved.height, 100)
        let before = card.frame.minY
        origin.withOffset(CGVector(dx: moved.midX, dy: moved.minY + moved.height * 0.2))
            .press(forDuration: 0.05, thenDragTo: origin.withOffset(CGVector(dx: moved.midX, dy: moved.minY + moved.height * 0.65)))
        XCTAssertEqual(card.value as? String, initial)
        XCTAssertGreaterThan(card.frame.minY, before + 20, "A quick downward drag must move the shelf, not the cover index")
        capture(app, "quick-downward-cover-scroll")
        app.terminate()
    }

}
