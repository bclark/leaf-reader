import XCTest

/// Run only with the task-owned empty-Lists fixture, never a personal library.
final class EmptyListsUITests: XCTestCase {
    override func setUpWithError() throws {
        guard ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.hasPrefix("Leaf Post12 QA ") == true else {
            throw XCTSkip("Requires the named task-owned empty-Lists simulator fixture")
        }
    }

    func testSingleCompactActionSurvivesRotationAndKeyboardCancel() {
        continueAfterFailure = false
        let app = XCUIApplication()
        defer { app.terminate(); XCUIDevice.shared.orientation = .portrait }
        var regularHeight: CGFloat = 0
        for large in [false, true] {
            app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-AppleLanguages", "(en)",
                "-AppleLocale", "en_US", "-leaf.appTheme", large ? "dark" : "light"]
            if large { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
            app.launch()
            let selector = app.segmentedControls["library.views"]
            if selector.waitForExistence(timeout: 10) { selector.buttons["Lists"].tap() }
            else { app.buttons["library.views"].tap(); app.buttons["Lists"].tap() }
            for orientation in [UIDeviceOrientation.portrait, .landscapeLeft] {
                XCUIDevice.shared.orientation = orientation
                let rotated = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                    let frame = app.frame
                    return orientation.isLandscape ? frame.width > frame.height : frame.height > frame.width
                }, object: app)
                XCTAssertEqual(XCTWaiter.wait(for: [rotated], timeout: 8), .completed,
                               "Accept rotation only after the app viewport changes orientation")
                let actions = app.buttons.matching(identifier: "Create Reading List")
                XCTAssertTrue(actions.firstMatch.waitForExistence(timeout: 5))
                XCTAssertEqual(actions.count, 1)
                let action = actions.firstMatch
                XCTAssertTrue(action.isHittable)
                XCTAssertLessThanOrEqual(action.frame.maxX, app.frame.maxX + 1)
                XCTAssertLessThanOrEqual(action.frame.maxY, app.frame.maxY + 1)
                if !large { regularHeight = action.frame.height }
                else { XCTAssertGreaterThan(action.frame.height, regularHeight, "The action must respond to Dynamic Type") }
                XCTAssertGreaterThanOrEqual(action.frame.height, 44)
                XCTAssertLessThan(action.frame.height, app.frame.height / 3, "The action must not occupy a cover-sized tile")
                XCTAssertFalse(app.buttons["CREATE LIST"].exists)
                let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                screenshot.name = "empty-lists-\(large)-\(orientation.rawValue)"
                screenshot.lifetime = .keepAlways; add(screenshot)
                action.tap()
                let name = app.textFields["library.newListName"]
                XCTAssertTrue(name.waitForExistence(timeout: 5))
                name.tap(); name.typeText("Discarded QA List")
                let cancel = app.navigationBars.buttons["Cancel"]
                XCTAssertTrue(cancel.isHittable); cancel.tap()
                XCTAssertTrue(action.waitForExistence(timeout: 5) && action.isHittable)
                XCTAssertFalse(app.buttons["Discarded QA List"].exists)
            }
            app.terminate()
        }
    }
}
