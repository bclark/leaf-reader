import XCTest

/// The runner resets only its named disposable Tip QA library before each method.
final class ContextualTipUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        try XCTSkipUnless((ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? "").hasPrefix("Leaf Tip QA "))
    }
    private func launch(dark: Bool = false, large: Bool = false, spanish: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-leaf.appTheme", dark ? "dark" : "light",
            "-AppleLanguages", spanish ? "(es-MX)" : "(en)", "-AppleLocale", spanish ? "es_MX" : "en_US"]
        if large { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists,
            "Existing completed history and app launch must not produce a prompt")
        return app
    }
    private func capture(_ name: String, _ app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.name = name
        attachment.lifetime = .keepAlways; add(attachment)
    }
    private func finishNext(_ number: Int, _ app: XCUIApplication, spanish: Bool = false) {
        let card = app.buttons["library.continue"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertTrue(card.label.contains(String(format: "QA Tip %02d", number)), card.label)
        card.tap()
        let canvas = app.otherElements["ReaderCanvas"]
        XCTAssertTrue(canvas.waitForExistence(timeout: 10) && canvas.isHittable)
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let back = app.buttons["BackButton"]
        if !back.waitForExistence(timeout: 2) {
            canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        XCTAssertTrue(back.waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["PageStatus"].label, spanish ? "Página 4/4" : "Page 4/4")
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists, "A last-page turn must not interrupt the reader")
        back.tap()
    }
    private func finishThree(_ app: XCUIApplication, spanish: Bool = false) {
        for index in 1...3 {
            finishNext(index, app, spanish: spanish)
            if index < 3 {
                XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 5))
                XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
            }
        }
        XCTAssertTrue(app.staticTexts["tipPrompt.title"].waitForExistence(timeout: 5))
    }
    private func reveal(_ element: XCUIElement, _ app: XCUIApplication) {
        for _ in 0..<10 {
            let promptScroll = app.scrollViews.containing(.staticText, identifier: "tipPrompt.title").firstMatch
            let scroll = promptScroll.exists ? promptScroll : app.scrollViews.firstMatch
            let viewport = scroll.exists ? scroll.frame.intersection(app.frame) : app.frame
            if element.exists && element.isHittable
                && viewport.insetBy(dx: -1, dy: -1).contains(element.frame) { return }
            if element.exists && element.frame.minY < viewport.minY { scroll.swipeDown() }
            else { scroll.swipeUp() }
        }
        XCTFail("Control must be fully visible and hittable inside its scroll viewport: \(element.identifier)")
    }
    private func openVoluntarySupport(_ app: XCUIApplication, spanish: Bool = false) {
        app.buttons[spanish ? "MENÚ" : "MENU"].tap()
        XCTAssertTrue(app.buttons[spanish ? "Ajustes y más" : "Settings and more"].waitForExistence(timeout: 5))
        app.buttons[spanish ? "Ajustes y más" : "Settings and more"].tap()
        let support = app.buttons[spanish ? "Dejar propina" : "Leave a tip"]
        reveal(support, app); support.tap()
        XCTAssertTrue(app.navigationBars[spanish ? "Apoyar a Leaf" : "Support Leaf"].waitForExistence(timeout: 5))
        capture("voluntary-support-after-opt-out", app)
        app.buttons[spanish ? "Cerrar" : "Close"].tap()
    }

    func testNeverAskAfterThreeNewCompletionsAndSettingsSupportSurvives() {
        let app = launch()
        finishThree(app)
        for id in ["tipPrompt.tip", "tipPrompt.notNow", "tipPrompt.never"] {
            let button = app.buttons[id]
            reveal(button, app)
            XCTAssertGreaterThanOrEqual(button.frame.height, 44)
        }
        capture("contextual-tip-three-choices", app)
        app.buttons["tipPrompt.never"].tap()
        XCTAssertTrue(app.staticTexts["tipPrompt.title"].waitForNonExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        finishNext(4, app)
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        openVoluntarySupport(app)
    }

    func testNotNowSurvivesRelaunchAndNextCompletion() {
        let app = launch()
        finishThree(app)
        app.buttons["tipPrompt.notNow"].tap()
        XCTAssertTrue(app.staticTexts["tipPrompt.title"].waitForNonExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        finishNext(4, app)
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        capture("not-now-persists-after-reader-relaunch", app)
    }

    func testTipOpensExistingJarAfterPromptDismissalWithoutPurchase() {
        let app = launch()
        finishThree(app)
        app.buttons["tipPrompt.tip"].tap()
        XCTAssertTrue(app.navigationBars["Support Leaf"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        capture("contextual-tip-opens-existing-jar", app)
        app.buttons["Close"].tap()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        // Product buttons are deliberately not selected; no account or purchase approval.
    }

    func testDarkLargeTextPromptKeepsAllChoicesReachable() {
        let app = launch(dark: true, large: true)
        finishThree(app)
        for id in ["tipPrompt.tip", "tipPrompt.notNow", "tipPrompt.never"] {
            let button = app.buttons[id]
            reveal(button, app)
            XCTAssertGreaterThanOrEqual(button.frame.height, 44)
            XCTAssertLessThanOrEqual(button.frame.maxX, app.frame.maxX + 1)
            capture("contextual-tip-dark-large-" + id, app)
        }
        app.buttons["tipPrompt.never"].tap()
        XCTAssertTrue(app.staticTexts["tipPrompt.title"].waitForNonExistence(timeout: 5))
    }

    func testSpanishPromptKeepsChoicesAndVoluntarySupport() {
        let app = launch(spanish: true)
        finishThree(app, spanish: true)
        for (id, title) in [("tipPrompt.tip", "Dejar propina"),
                            ("tipPrompt.notNow", "Ahora no"),
                            ("tipPrompt.never", "No volver a preguntar")] {
            let button = app.buttons[id]
            reveal(button, app)
            XCTAssertEqual(button.label, title)
            XCTAssertGreaterThanOrEqual(button.frame.height, 44)
            capture("contextual-tip-spanish-" + id, app)
        }
        app.buttons["tipPrompt.never"].tap()
        XCTAssertTrue(app.staticTexts["tipPrompt.title"].waitForNonExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["tipPrompt.title"].exists)
        openVoluntarySupport(app, spanish: true)
    }

}
