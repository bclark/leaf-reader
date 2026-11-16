import XCTest

/// Each method is launched separately by the task runner with a fresh, owned simulator app container.
final class FirstLaunchOnboardingUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        guard ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.hasPrefix("Leaf Onboarding QA ") == true else {
            throw XCTSkip("Onboarding QA runs only on its named, isolated simulators")
        }
    }

    private func launch(theme: String? = nil, largeText: Bool = false, spanish: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        // The first fresh launch must not inject legacy Leaf preferences: that would bypass migration.
        app.launchArguments = ["-AppleLanguages", spanish ? "(es-MX)" : "(en)", "-AppleLocale", spanish ? "es_MX" : "en_US"]
        if let theme { app.launchArguments += ["-leaf.appTheme", theme] }
        if largeText { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        return app
    }

    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }

    private func reveal(_ button: XCUIElement, app: XCUIApplication) {
        for _ in 0..<12 {
            if button.exists && button.isHittable { return }
            app.scrollViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(button.exists && button.isHittable)
    }

    func testFreshWelcomePickerCancelOptionalBackSetupCancelAndSkipPersist() {
        let app = launch()
        let welcome = app.staticTexts["onboarding.welcomeTitle"]
        XCTAssertTrue(welcome.waitForExistence(timeout: 15))
        let choose = app.buttons["onboarding.chooseFile"]
        XCTAssertGreaterThanOrEqual(choose.frame.height, 44)
        XCTAssertGreaterThanOrEqual(app.buttons["onboarding.useJellyfin"].frame.height, 44)
        XCTAssertGreaterThanOrEqual(app.buttons["onboarding.later"].frame.height, 44)
        capture("onboarding-fresh-welcome", app: app)
        choose.tap()
        let cancel = app.buttons["Cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5))
        capture("onboarding-native-file-picker", app: app)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "onboarding-owned-picker-hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        cancel.tap()
        XCTAssertTrue(welcome.waitForExistence(timeout: 5) && choose.isHittable)
        XCTAssertFalse(app.alerts["Import failed"].exists)

        app.buttons["onboarding.useJellyfin"].tap()
        let jellyfin = app.staticTexts["onboarding.jellyfinTitle"]
        XCTAssertTrue(jellyfin.waitForExistence(timeout: 5))
        capture("onboarding-optional-jellyfin", app: app)
        let back = app.navigationBars.buttons["Back"]
        XCTAssertTrue(back.exists && back.isHittable)
        back.tap()
        XCTAssertTrue(welcome.waitForExistence(timeout: 5))
        app.buttons["onboarding.useJellyfin"].tap()
        app.buttons["onboarding.connect"].tap()
        XCTAssertTrue(app.textFields["Server URL"].waitForExistence(timeout: 5))
        app.navigationBars.buttons["Done"].tap()
        XCTAssertTrue(jellyfin.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["onboarding.connect"].isHittable)
        app.terminate(); app.launch()
        XCTAssertTrue(jellyfin.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["onboarding.connect"].isHittable)
        app.buttons["onboarding.withoutJellyfin"].tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 10))
        XCTAssertFalse(welcome.exists)
    }

    func testMaybeLaterPersistsAndLibrarySourceActionsRemainAvailable() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        app.buttons["onboarding.later"].tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 10))
        let add = app.buttons["library.empty.add"]
        XCTAssertTrue(app.buttons["library.empty.connect"].exists)
        XCTAssertFalse(app.buttons["Not now"].exists)
        XCTAssertTrue(add.exists && add.isHittable)
        add.tap()
        for title in ["From Files", "From SMB Drive", "From Jellyfin"] { XCTAssertTrue(app.buttons[title].exists) }
        capture("onboarding-skipped-library-source-actions", app: app)
        // iOS 27 presents this confirmation dialog as a popover, dismissed by tapping outside.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        XCTAssertFalse(app.buttons["From Files"].exists)
        XCTAssertTrue(app.segmentedControls["library.views"].exists)
    }

    func testDarkLargeTextOnboardingKeepsSetupAndSkipReachable() {
        var app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        app.terminate()
        app = launch(theme: "dark", largeText: true)
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        capture("onboarding-welcome-dark-ax3", app: app)
        let choose = app.buttons["onboarding.chooseFile"]
        reveal(choose, app: app)
        XCTAssertGreaterThanOrEqual(choose.frame.height, 44)
        let optional = app.buttons["onboarding.useJellyfin"]
        reveal(optional, app: app); optional.tap()
        XCTAssertTrue(app.staticTexts["onboarding.jellyfinTitle"].waitForExistence(timeout: 5))
        let connect = app.buttons["onboarding.connect"]
        reveal(connect, app: app)
        XCTAssertGreaterThanOrEqual(connect.frame.height, 44)
        capture("onboarding-jellyfin-dark-ax3", app: app)
        connect.tap()
        let server = app.textFields["Server URL"]
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        server.tap()
        let done = app.navigationBars.buttons["Done"]
        XCTAssertTrue(done.exists && done.isHittable)
        capture("onboarding-setup-keyboard-dark-ax3", app: app)
        done.tap()
        let skip = app.buttons["onboarding.withoutJellyfin"]
        reveal(skip, app: app); skip.tap()
        XCTAssertTrue(app.buttons["library.views"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["onboarding.welcomeTitle"].exists)
    }

    func testSpanishWelcomeAndOptionalCardUseLocalizedCopy() {
        let app = launch(spanish: true)
        let title = app.staticTexts["onboarding.welcomeTitle"]
        XCTAssertTrue(title.waitForExistence(timeout: 15))
        XCTAssertEqual(title.label, "Abre tu primer cómic")
        XCTAssertGreaterThanOrEqual(app.buttons["onboarding.chooseFile"].frame.height, 44)
        capture("onboarding-welcome-es-MX", app: app)
        app.buttons["onboarding.useJellyfin"].tap()
        let jellyfin = app.staticTexts["onboarding.jellyfinTitle"]
        XCTAssertTrue(jellyfin.waitForExistence(timeout: 5))
        XCTAssertEqual(jellyfin.label, "Conecta tu biblioteca de Jellyfin")
        capture("onboarding-jellyfin-es-MX", app: app)
        app.buttons["onboarding.withoutJellyfin"].tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
    }

    func testFileImportFailureCanRetryAndPublishedComicReadsLocally() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        func pick(_ name: String) {
            app.buttons["onboarding.chooseFile"].tap()
            XCTAssertTrue(app.buttons["Cancel"].waitForExistence(timeout: 5))
            let file = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
            if !file.exists {
                let browse = app.tabBars["DOC.browsingModeTabBar"].buttons["Browse"]
                XCTAssertTrue(browse.exists && browse.isHittable)
                browse.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).tap()
                XCTAssertTrue(browse.isSelected)
                let local = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "On My iPhone")).firstMatch
                XCTAssertTrue(local.waitForExistence(timeout: 5)); local.tap()
                let folder = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "Leaf Onboarding Fixtures")).firstMatch
                XCTAssertTrue(folder.waitForExistence(timeout: 5)); folder.tap()
            }
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "onboarding-picker-fixture-hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
            XCTAssertTrue(file.waitForExistence(timeout: 5)); file.tap()
            let open = app.buttons["Open"]
            if open.exists { XCTAssertTrue(open.isEnabled); open.tap() }
        }
        pick("Onboarding Invalid")
        let error = app.alerts["Import failed"]
        XCTAssertTrue(error.waitForExistence(timeout: 10))
        XCTAssertFalse(app.segmentedControls["library.views"].exists)
        capture("onboarding-import-failure", app: app)
        error.buttons["OK"].tap()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 5))
        pick("Onboarding Good")
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["onboarding.welcomeTitle"].exists)
        app.terminate(); app.launch()
        XCTAssertTrue(views.waitForExistence(timeout: 10))
        views.buttons["Offline"].tap()
        let comic = app.buttons.matching(NSPredicate(format: "identifier != %@", "library.continue"))
            .containing(.staticText, identifier: "Onboarding Good").firstMatch
        XCTAssertTrue(comic.waitForExistence(timeout: 10)); comic.tap()
        let canvas = app.otherElements["ReaderCanvas"]
        XCTAssertTrue(canvas.waitForExistence(timeout: 10) && canvas.isHittable)
        capture("onboarding-imported-local-reader", app: app)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let back = app.buttons["BackButton"]
        if !back.waitForExistence(timeout: 2) { canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap() }
        XCTAssertTrue(back.waitForExistence(timeout: 3)); back.tap()
        XCTAssertTrue(views.waitForExistence(timeout: 5))
        // Remove only the known imported fixture through the normal app path.
        comic.press(forDuration: 1); app.buttons["Remove"].tap()
        let removal = app.alerts["Remove comic?"]
        XCTAssertTrue(removal.waitForExistence(timeout: 5)); removal.buttons["Delete"].tap()
        XCTAssertTrue(comic.waitForNonExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(views.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["onboarding.welcomeTitle"].exists)
    }

    func testNativePickerStartupDiagnostic() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        let start = Date()
        app.buttons["onboarding.chooseFile"].tap()
        let cancel = app.buttons["Cancel"]
        let ready = cancel.waitForExistence(timeout: 15)
        let timing = XCTAttachment(string: "Native picker ready=\(ready); seconds since tap=\(Date().timeIntervalSince(start))")
        timing.name = "native-picker-startup-measurement"; timing.lifetime = .keepAlways; add(timing)
        capture("native-picker-ready-diagnostic", app: app)
        XCTAssertTrue(ready)
        let browse = app.tabBars["DOC.browsingModeTabBar"].buttons["Browse"]
        XCTAssertTrue(browse.exists && browse.isHittable)
        browse.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).tap()
        let selected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isSelected == true"), object: browse)
        let outcome = XCTWaiter.wait(for: [selected], timeout: 5)
        capture("native-picker-browse-diagnostic", app: app)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "native-picker-browse-hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        XCTAssertEqual(outcome, .completed)
        cancel.tap()
        XCTAssertTrue(app.buttons["onboarding.chooseFile"].isHittable)
    }

    func testJellyfinConsentFailureRetriesVerifiedConnectionAndDoesNotReopenAfterDisconnect() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        app.buttons["onboarding.useJellyfin"].tap(); app.buttons["onboarding.connect"].tap()
        let server = app.textFields["Server URL"]
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        server.tap(); server.typeText("http://127.0.0.1:56823")
        let username = app.textFields["Username"]
        username.tap(); username.typeText("fixture")
        let password = app.secureTextFields["Password"]
        password.tap(); password.typeText("fixture")
        let signIn = app.buttons["Sign in"]
        for _ in 0..<6 { if signIn.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(signIn.isHittable && signIn.isEnabled); signIn.tap()
        let consentError = app.staticTexts["HTTP requires your permission and a local-network address. Use HTTPS for remote servers."]
        XCTAssertTrue(consentError.waitForExistence(timeout: 10))
        XCTAssertFalse(app.segmentedControls["library.views"].exists)
        capture("onboarding-connection-consent-failure", app: app)
        let consent = app.switches["Allow unencrypted local HTTP"]
        XCTAssertEqual(consent.value as? String, "0")
        consent.switches.firstMatch.tap()
        XCTAssertEqual(consent.value as? String, "1")
        password.tap(); password.typeText("fixture")
        for _ in 0..<6 { if signIn.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(signIn.isEnabled && signIn.isHittable); signIn.tap()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["onboarding.jellyfinTitle"].exists)
        capture("onboarding-verified-connection-library", app: app)
        let serverContinue = app.buttons["library.continue"].firstMatch
        XCTAssertTrue(serverContinue.waitForExistence(timeout: 10))
        XCTAssertTrue(serverContinue.label.contains("QA Server Continue"))
        serverContinue.tap()
        XCTAssertTrue(app.navigationBars["QA Server Continue"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Server-only continuation details."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Read"].exists && app.buttons["Read"].isHittable)
        XCTAssertFalse(app.otherElements["ReaderCanvas"].exists,
            "Server-only Continue must open details without starting retrieval or the reader")
        capture("continue-server-only-opens-details", app: app)
        let detailsBack = app.navigationBars.buttons["Back"]
        XCTAssertTrue(detailsBack.exists && detailsBack.isHittable)
        detailsBack.tap()
        XCTAssertTrue(serverContinue.waitForExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(views.waitForExistence(timeout: 10))
        disconnectFixture(app)
        app.terminate(); app.launch()
        XCTAssertTrue(views.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["onboarding.welcomeTitle"].exists)
    }

    /// Recovery for a failed fixture run; the runner verifies the persisted loopback identity first.
    func testDisconnectOwnedLoopbackFixture() {
        let app = launch()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 15))
        disconnectFixture(app)
    }

    private func disconnectFixture(_ app: XCUIApplication) {
        let add = app.buttons["Add comics"]
        XCTAssertTrue(add.waitForExistence(timeout: 5) && add.isHittable); add.tap()
        app.buttons["From Jellyfin"].tap()
        let disconnect = app.buttons["Disconnect"]
        XCTAssertTrue(disconnect.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["http://127.0.0.1:56823"].exists)
        XCTAssertTrue(app.staticTexts["fixture"].exists)
        disconnect.tap()
        XCTAssertTrue(app.textFields["Server URL"].waitForExistence(timeout: 10))
        app.navigationBars.buttons["Done"].tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
    }
}
