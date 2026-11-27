import XCTest
import UIKit

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
        for title in ["Copy from Files", "Link from Files", "From SMB Drive", "From Jellyfin"] { XCTAssertTrue(app.buttons[title].exists) }
        capture("onboarding-skipped-library-source-actions", app: app)
        // iOS 27 presents this confirmation dialog as a popover, dismissed by tapping outside.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        XCTAssertFalse(app.buttons["Copy from Files"].exists)
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
        XCTAssertTrue(signIn.isEnabled, "A recoverable sign-in error must retain the password for retry")
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

    /// Requires LEAF_SIGNIN_FAILURES=1 on the owned loopback fixture; fresh app container.
    func testSignInRetriesServerFailureWithoutRetypingPassword() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        app.buttons["onboarding.useJellyfin"].tap(); app.buttons["onboarding.connect"].tap()
        let server = app.textFields["Server URL"]
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        server.tap(); server.typeText("http://127.0.0.1:56823")
        app.textFields["Username"].tap(); app.textFields["Username"].typeText("fixture")
        let password = app.secureTextFields["Password"]
        password.tap(); password.typeText("fixture")
        let consent = app.switches["Allow unencrypted local HTTP"]
        let signIn = app.buttons["Sign in"]
        for _ in 0..<6 { if consent.isHittable { break }; app.swipeUp() }
        consent.switches.firstMatch.tap()
        for _ in 0..<6 { if signIn.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(signIn.isEnabled && signIn.isHittable); signIn.tap()
        XCTAssertTrue(app.staticTexts["Jellyfin could not complete the request. Try again later."].waitForExistence(timeout: 10))
        XCTAssertTrue(signIn.isEnabled, "A failed network authentication must keep the draft password")
        XCTAssertEqual(app.textFields["Server URL"].value as? String, "http://127.0.0.1:56823")
        XCTAssertEqual(app.textFields["Username"].value as? String, "fixture")
        signIn.tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["onboarding.jellyfinTitle"].exists)
    }

    func testSignInDraftClearsOnBackgroundAndExplicitDismissal() {
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
        XCTAssertTrue(signIn.isEnabled)
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(password.waitForExistence(timeout: 5))
        XCTAssertFalse(signIn.isEnabled, "Genuine backgrounding must clear the draft password")
        password.tap(); password.typeText("fixture")
        XCTAssertTrue(signIn.isEnabled)
        app.navigationBars.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["onboarding.connect"].waitForExistence(timeout: 5))
        app.buttons["onboarding.connect"].tap()
        XCTAssertTrue(password.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Sign in"].isEnabled, "Dismissal must not retain draft credentials")
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

    func testSeriesPeelPromotesLoadedArtworkWithoutOutgoingFlash() {
        let app = connectCinematicFixture()
        let card = app.buttons["Unsorted"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        for _ in 0..<5 {
            if card.isHittable && card.frame.maxY < app.frame.maxY - 24 { break }
            app.swipeUp()
        }
        XCTAssertTrue(card.isHittable)
        Thread.sleep(forTimeInterval: 0.5) // Let three initial loopback covers load once.
        assertPeelColor(card, app, channel: 0)
        let beforeQuickDrag = card.value as? String
        card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
            .press(forDuration: 0.05, thenDragTo: card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)))
        XCTAssertEqual(card.value as? String, beforeQuickDrag, "A quick drag scrolls without advancing the series")
        for _ in 0..<5 {
            if card.isHittable && card.frame.maxY < app.frame.maxY - 24 { break }
            app.swipeUp()
        }
        XCTAssertTrue(card.isHittable)
        assertPeelColor(card, app, channel: 0)
        let heldDragDuration = UILongPressGestureRecognizer().minimumPressDuration + 0.1
        for (value, channel) in [("2 / 3", 1), ("3 / 3", 2), ("1 / 3", 0)] {
            card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
                .press(forDuration: heldDragDuration, thenDragTo: card.coordinate(
                    withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)))
            expectation(for: NSPredicate(format: "value BEGINSWITH %@", value + ", "), evaluatedWith: card)
            waitForExpectations(timeout: 5)
            assertPeelColor(card, app, channel: channel)
        }
        disconnectFixture(app)
    }

    private func assertPeelColor(_ card: XCUIElement, _ app: XCUIApplication, channel: Int,
                                 file: StaticString = #filePath, line: UInt = #line) {
        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "peel-front-channel-\(channel)"
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let image = screenshot.image.cgImage,
              let context = CGContext(data: nil, width: 1, height: 1, bitsPerComponent: 8,
                  bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return XCTFail("Cannot inspect fixture artwork", file: file, line: line)
        }
        let scale = CGFloat(image.width) / app.frame.width
        let x = Int(card.frame.midX * scale)
        let y = Int((card.frame.minY + card.frame.height * 0.35) * scale)
        guard let pixel = image.cropping(to: CGRect(x: x, y: y, width: 1, height: 1)) else {
            return XCTFail("Fixture artwork sample outside image", file: file, line: line)
        }
        context.draw(pixel, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        let bytes = context.data!.assumingMemoryBound(to: UInt8.self)
        let expected = [[190, 40, 40], [35, 145, 75], [35, 75, 190]][channel]
        XCTAssertTrue((0..<3).allSatisfy { abs(Int(bytes[$0]) - expected[$0]) < 18 },
            "Outgoing artwork must not return after the issue index advances; actual RGB \(bytes[0]),\(bytes[1]),\(bytes[2])", file: file, line: line)
    }

    func testCleanLibraryKeepsContinueAndMovesJellyfinControlsToMenu() {
        let app = connectCinematicFixture()
        let resume = app.buttons["library.continue"].firstMatch
        XCTAssertTrue(resume.waitForExistence(timeout: 10))
        let resumeLabel = resume.label
        XCTAssertFalse(app.staticTexts["Fixture Library"].exists)
        XCTAssertFalse(app.buttons["Jellyfin libraries"].exists)
        XCTAssertFalse(app.buttons["Retry"].exists)
        app.buttons["MENU"].tap()
        app.buttons["Settings and more"].tap()
        let connection = app.buttons["Jellyfin settings"].firstMatch
        reveal(connection, app: app)
        XCTAssertGreaterThanOrEqual(connection.frame.height, 44)
        XCTAssertTrue(app.buttons["Jellyfin libraries"].firstMatch.exists)
        connection.tap()
        XCTAssertTrue(app.staticTexts["fixture"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["http://127.0.0.1:56823"].exists)
        XCTAssertTrue(app.buttons["Disconnect"].exists)
        let refresh = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "Last catalogue refresh")).firstMatch
        XCTAssertTrue(refresh.exists)
        XCTAssertLessThan(refresh.frame.minY, app.buttons["Disconnect"].frame.minY)
        let refreshDescription = refresh.label + " " + (refresh.value as? String ?? "")
        XCTAssertFalse(refreshDescription.contains("Not yet refreshed"))
        XCTAssertNotNil(refreshDescription.rangeOfCharacter(from: .decimalDigits))
        app.navigationBars.buttons["Done"].tap()
        app.buttons["Back"].tap()
        XCTAssertTrue(resume.waitForExistence(timeout: 5))
        XCTAssertEqual(resume.label, resumeLabel)
        app.segmentedControls["library.views"].buttons["All"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == %@", cinematicTitle + ", Jellyfin")).firstMatch.waitForExistence(timeout: 5))
        capture("clean-library-all", app: app)
        finishCinematicFixture(app)
    }

    func testServerOnlyComicsCanBePickedAndKeepTheirListGridAfterRelaunch() {
        let app = connectCinematicFixture()
        app.segmentedControls["library.views"].buttons["Lists"].tap()
        app.buttons["Create Reading List"].tap()
        let name = app.textFields["library.newListName"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap(); name.typeText("QA Remote List")
        app.navigationBars.buttons["Create & Add Comics"].tap()
        XCTAssertTrue(app.navigationBars["Add Comics to List"].waitForExistence(timeout: 5))
        let choiceID = "readingList.choice.jellyfin/fixture-server/fixture-user/fixture-book"
        let choice = app.buttons[choiceID]
        capture("remote-list-picker-before-add", app: app)
        XCTAssertTrue(choice.waitForExistence(timeout: 5), "The list picker must include the server-only catalogue")
        choice.tap()
        app.navigationBars.buttons["Add (1)"].tap()
        let memberID = "readingList.member.jellyfin/fixture-server/fixture-user/fixture-book"
        let member = app.descendants(matching: .any).matching(identifier: memberID).firstMatch
        XCTAssertTrue(member.waitForExistence(timeout: 5))
        capture("remote-list-grid", app: app)
        XCTAssertFalse(app.staticTexts["THIS LIST IS EMPTY"].exists)
        app.buttons["ADD COMICS"].tap()
        XCTAssertTrue(app.navigationBars["Add Comics to List"].waitForExistence(timeout: 5))
        capture("remote-list-picker-excludes-existing", app: app)
        XCTAssertFalse(app.buttons[choiceID].exists, "An existing remote member must not appear twice in the picker")
        app.navigationBars.buttons["Cancel"].tap()
        app.buttons["Back"].tap()
        app.terminate(); app.launch()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 15))
        app.segmentedControls["library.views"].buttons["Lists"].tap()
        let list = app.buttons["QA Remote List"].firstMatch
        reveal(list, app: app); list.tap()
        XCTAssertTrue(member.waitForExistence(timeout: 5), "Remote membership must survive relaunch without a download")
        capture("remote-list-relaunch", app: app)
        app.buttons["Delete Reading List"].tap()
        app.alerts.buttons["Delete"].tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 5))
        app.segmentedControls["library.views"].buttons["Series"].tap()
        disconnectFixture(app)
    }

    func testHiddenCaptionsKeepReadingAndAvailabilityIndependent() {
        let app = connectCinematicFixture()
        app.segmentedControls["library.views"].buttons["All"].tap()
        func card(_ title: String) -> XCUIElement {
            let entry = app.buttons[title + ", Jellyfin"].firstMatch
            for _ in 0..<4 where !entry.isHittable { app.scrollViews.firstMatch.swipeDown() }
            reveal(entry, app: app)
            return entry
        }
        for (title, state) in [(cinematicTitle, "In progress"), ("QA Creator Match", "UNREAD"),
                               ("QA Unknown Reading State", "Reading status unknown"),
                               ("QA Finished Reading State", "Finished")] {
            let entry = card(title)
            XCTAssertEqual(entry.value as? String, "Needs connection, " + state)
            XCTAssertGreaterThanOrEqual(entry.frame.height, 44)
        }
        capture("hidden-captions-known-unknown-reading", app: app)
        app.buttons["MENU"].tap(); app.buttons["Library appearance"].tap()
        let reading = app.switches["library.appearance.reading"]
        XCTAssertEqual(reading.value as? String, "1"); reading.tap()
        XCTAssertEqual(reading.value as? String, "0")
        app.buttons["Done"].tap()
        XCTAssertEqual(card("QA Unknown Reading State").value as? String, "Needs connection, Reading status unknown")
        let main = card(cinematicTitle)
        XCTAssertEqual(main.value as? String, "Needs connection, In progress")
        capture("hidden-captions-indicators-off-retain-meaning", app: app)
        main.tap()
        let download = app.buttons["Download"]
        revealDetailControl(download, app)
        download.tap()
        XCTAssertTrue(app.buttons["Remove"].waitForExistence(timeout: 20))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        let localCopy = card(cinematicTitle)
        let available = NSPredicate(format: "value BEGINSWITH %@", "Available offline,")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: available, object: localCopy)], timeout: 5), .completed)
        XCTAssertFalse((localCopy.value as? String ?? "").contains("Needs connection"))
        capture("hidden-captions-downloaded-availability", app: app)
        finishCinematicFixture(app)
    }

    private let cinematicWriter = "Alexandra Longname Example de la Biblioteca"
    private let cinematicTitle = "QA A Very Long Comic Title for the Cinematic Detail Layout and Creator Navigation"

    func testRetinaCoverRequestsFollowColumnsAndRotation() {
        let app = connectCinematicFixture()
        defer { XCUIDevice.shared.orientation = .portrait }
        app.segmentedControls["library.views"].buttons["All"].tap()
        let card = app.buttons[cinematicTitle + ", Jellyfin"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        func columns(_ count: Int) {
            app.buttons["MENU"].tap(); app.buttons["Settings and more"].tap()
            let option = app.buttons["\(count) COL"]
            reveal(option, app: app); XCTAssertTrue(option.isHittable); option.tap()
            app.buttons["Back"].tap()
            XCTAssertTrue(card.waitForExistence(timeout: 10))
            reveal(card, app: app)
        }
        columns(2)
        capture("retina-covers-two-columns-portrait", app: app)
        XCUIDevice.shared.orientation = .landscapeLeft
        let landscape = NSPredicate { _, _ in app.frame.width > app.frame.height }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: landscape, object: app)], timeout: 10), .completed)
        reveal(card, app: app)
        XCTAssertTrue(card.isHittable)
        capture("retina-covers-two-columns-landscape", app: app)
        columns(4)
        capture("retina-covers-four-columns-landscape", app: app)
        XCUIDevice.shared.orientation = .portrait
        let portrait = NSPredicate { _, _ in app.frame.height > app.frame.width }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: portrait, object: app)], timeout: 10), .completed)
        app.segmentedControls["library.views"].buttons["Series"].tap()
        capture("retina-covers-series-portrait", app: app)
        disconnectFixture(app)
    }

    private func connectCinematicFixture() -> XCUIApplication {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        app.buttons["onboarding.useJellyfin"].tap()
        app.buttons["onboarding.connect"].tap()
        let server = app.textFields["Server URL"]
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        server.tap(); server.typeText("http://127.0.0.1:56823")
        app.textFields["Username"].tap(); app.textFields["Username"].typeText("fixture")
        app.secureTextFields["Password"].tap(); app.secureTextFields["Password"].typeText("fixture")
        let consent = app.switches["Allow unencrypted local HTTP"]
        XCTAssertEqual(consent.value as? String, "0")
        consent.switches.firstMatch.tap()
        let signIn = app.buttons["Sign in"]
        for _ in 0..<6 { if signIn.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(signIn.isEnabled && signIn.isHittable)
        signIn.tap()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 15))
        return app
    }

    private func openCinematicFixture(_ app: XCUIApplication) {
        let card = app.buttons["library.continue"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertTrue(card.label.contains(cinematicTitle))
        card.tap()
        XCTAssertTrue(app.scrollViews.staticTexts[cinematicTitle].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["ReaderCanvas"].exists)
    }

    private func revealDetailControl(_ control: XCUIElement, _ app: XCUIApplication) {
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<12 {
            let navigation = app.navigationBars.firstMatch
            let frame = scroll.frame.intersection(app.frame)
            let top = navigation.exists ? max(frame.minY, navigation.frame.maxY) : frame.minY
            let viewport = CGRect(x: frame.minX, y: top, width: frame.width, height: max(0, frame.maxY - top))
            if control.exists && control.isHittable && viewport.insetBy(dx: -1, dy: -1).contains(control.frame) { return }
            let above = control.exists && control.frame.minY < viewport.minY
            scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: above ? 0.35 : 0.65))
                .press(forDuration: 0.05, thenDragTo: scroll.coordinate(
                    withNormalizedOffset: CGVector(dx: 0.5, dy: above ? 0.65 : 0.35)))
        }
        XCTFail("Detail control must fit fully below the navigation bar inside its scroll viewport")
    }

    private func finishCinematicFixture(_ app: XCUIApplication) {
        app.terminate()
        let english = launch()
        XCTAssertTrue(english.segmentedControls["library.views"].waitForExistence(timeout: 10))
        disconnectFixture(english)
    }

    private func creatorMatchButton(_ app: XCUIApplication) -> XCUIElement {
        let matches = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "QA Creator Match,"))
        let match = matches.firstMatch
        XCTAssertTrue(match.waitForExistence(timeout: 10))
        XCTAssertEqual(matches.count, 1, "The fixture must expose one distinct comic button")
        XCTAssertEqual(match.label, "QA Creator Match, Jellyfin", "The decorative cover must not repeat the comic title")
        return match
    }

    func testCinematicCreditsCreatorBackAndMissingMetadata() {
        let app = connectCinematicFixture()
        openCinematicFixture(app)
        let writer = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", cinematicWriter)).firstMatch
        revealDetailControl(writer, app)
        XCTAssertGreaterThanOrEqual(writer.frame.height, 44)
        XCTAssertTrue(writer.label.contains("Writer"))
        let savedY = writer.frame.minY
        capture("cinematic-long-credit-and-title", app: app)
        writer.tap()
        let match = creatorMatchButton(app)
        capture("cinematic-creator-results", app: app)
        match.tap()
        XCTAssertTrue(app.navigationBars["QA Creator Match"].waitForExistence(timeout: 10))
        let read = app.buttons["Read"]
        revealDetailControl(read, app)
        XCTAssertGreaterThanOrEqual(read.frame.height, 44)
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Find comics by ")).firstMatch.exists)
        capture("cinematic-missing-cover-and-credits", app: app)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(match.waitForExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(writer.waitForExistence(timeout: 5))
        XCTAssertEqual(writer.frame.minY, savedY, accuracy: 8)
        capture("cinematic-back-restores-detail-position", app: app)
        finishCinematicFixture(app)
    }

    func testSpanishCinematicCreditRolesAreLocalized() {
        let initial = connectCinematicFixture()
        initial.terminate()
        let app = launch(theme: "dark", largeText: true, spanish: true)
        openCinematicFixture(app)
        let writer = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", cinematicWriter)).firstMatch
        revealDetailControl(writer, app)
        capture("cinematic-spanish-large-credit", app: app)
        XCTAssertTrue(writer.label.contains("Guionista"))
        XCTAssertGreaterThanOrEqual(writer.frame.height, 44)
        let artist = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Jordan Example")).firstMatch
        revealDetailControl(artist, app)
        XCTAssertTrue(artist.label.contains("Artista"))
        capture("cinematic-spanish-large-second-role", app: app)
        let read = app.buttons["Leer"]
        revealDetailControl(read, app)
        XCTAssertGreaterThanOrEqual(read.frame.height, 44)
        finishCinematicFixture(app)
    }

    func testCachedCinematicDetailsAndCreatorErrorsCanRetry() {
        let app = connectCinematicFixture()
        openCinematicFixture(app)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.terminate(); app.launch()
        openCinematicFixture(app)
        let saved = app.staticTexts["Saved details. Connect to refresh."]
        XCTAssertTrue(saved.waitForExistence(timeout: 5))
        let error = app.staticTexts["Jellyfin could not complete the request. Try again later."]
        XCTAssertTrue(error.waitForExistence(timeout: 5))
        let retry = app.buttons["Retry"]
        revealDetailControl(retry, app)
        capture("cinematic-cached-details-error", app: app)
        retry.tap()
        XCTAssertTrue(error.waitForNonExistence(timeout: 5))
        let writer = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", cinematicWriter)).firstMatch
        revealDetailControl(writer, app)
        writer.tap()
        XCTAssertTrue(error.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["No comics by this creator in this library."].exists)
        capture("cinematic-creator-failure-is-not-empty", app: app)
        app.buttons["Retry"].tap()
        _ = creatorMatchButton(app)
        capture("cinematic-creator-retry-success", app: app)
        finishCinematicFixture(app)
    }


    func testNativePickerAccessibilityDiagnostic() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["onboarding.welcomeTitle"].waitForExistence(timeout: 15))
        app.buttons["onboarding.chooseFile"].tap()
        let ready = app.buttons["Cancel"].waitForExistence(timeout: 5)
        let timing = XCTAttachment(string: "Original five-second Cancel selector exists=\(ready). Diagnostic collection is not picker acceptance.")
        timing.name = "native-picker-selector-observation"; timing.lifetime = .keepAlways; add(timing)
        let screen = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screen.name = "native-picker-full-owned-screen"; screen.lifetime = .keepAlways; add(screen)
        for bundle in ["net.digitalbc.leafreader", "com.apple.DocumentsApp", "com.apple.springboard"] {
            let application = XCUIApplication(bundleIdentifier: bundle)
            let hierarchy = XCTAttachment(string: application.debugDescription)
            hierarchy.name = "native-picker-accessibility-" + bundle
            hierarchy.lifetime = .keepAlways; add(hierarchy)
        }
    }

}
