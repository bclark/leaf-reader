import XCTest

/// Runs against a disposable simulator seeded with synthetic documents in On My iPad.
final class ReadFromFilesUITests: XCTestCase {
    func testLinkCBZResumesAfterRelaunch() throws {
        continueAfterFailure = false
        try XCTSkipUnless((ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? "").hasPrefix("Leaf Read From Files QA "))
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
                               "-leaf.portraitSpread", "NO", "-leaf.landscapeSpread", "NO"]
        app.launch()

        let add = app.buttons["library.empty.add"]
        let canvas = app.otherElements["ReaderCanvas"]
        let cancel = app.buttons["Cancel"].firstMatch
        func browse() {
            XCTAssertTrue(add.waitForExistence(timeout: 10))
            add.tap()
            let link = app.buttons["Link from Files"]
            XCTAssertTrue(link.waitForExistence(timeout: 5))
            XCTAssertTrue(app.buttons["Copy from Files"].exists)
            XCTAssertFalse(app.buttons["Read from Files"].exists)
            link.tap()
            XCTAssertTrue(cancel.waitForExistence(timeout: 15))
        }
        func item(_ name: String) -> XCUIElement {
            app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
        }
        func pick(_ name: String) {
            browse()
            let file = item(name)
            if !file.exists {
                let browseTab = app.tabBars["DOC.browsingModeTabBar"].buttons["Browse"]
                if browseTab.isHittable {
                    browseTab.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).tap()
                }
                let local = item(UIDevice.current.userInterfaceIdiom == .pad ? "On My iPad" : "On My iPhone")
                let folder = item("Leaf Read From Files Fixtures")
                if !folder.exists && local.waitForExistence(timeout: 5) { local.tap() }
                if folder.waitForExistence(timeout: 5) { folder.tap() }
            }
            let fileView = app.collectionViews["File View"]
            if fileView.value as? String == "Icon Mode" {
                let viewOptions = app.navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons["OverflowBarButtonItem"]
                if viewOptions.isHittable {
                    viewOptions.tap()
                    let list = app.buttons["List"].firstMatch
                    if list.waitForExistence(timeout: 3) { list.tap() }
                }
            }
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = name + "-picker"; hierarchy.lifetime = .keepAlways; self.add(hierarchy)
            XCTAssertTrue(file.waitForExistence(timeout: 8))
            file.tap()
            let open = app.buttons["Open"].firstMatch
            if open.exists { XCTAssertTrue(open.isEnabled); open.tap() }
        }
        browse()
        let visibleCancel = try XCTUnwrap(app.buttons.matching(identifier: "Cancel").allElementsBoundByIndex.first(where: \.isHittable))
        visibleCancel.tap()
        XCTAssertTrue(add.waitForExistence(timeout: 5))
        XCTAssertFalse(canvas.exists)

        pick("External CBZ")
        let card = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "linked-file-")).firstMatch
        if !card.waitForExistence(timeout: 5) {
            // Metadata may group the issue under Series; All still shows a lone issue directly.
            app.buttons["All"].firstMatch.tap()
        }
        XCTAssertTrue(card.waitForExistence(timeout: 15))
        let stableID = card.identifier
        XCTAssertFalse(app.staticTexts["Linked Files"].exists)
        card.tap()
        XCTAssertTrue(canvas.waitForExistence(timeout: 15))
        Thread.sleep(forTimeInterval: 0.6)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
        Thread.sleep(forTimeInterval: 0.7)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let back = app.buttons["BackButton"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["PageCoinCurrentPage"].label, "2")
        back.tap()
        XCTAssertTrue(app.buttons[stableID].waitForExistence(timeout: 5))

        app.terminate()
        app.launch()
        let continuation = app.buttons["library.continue"]
        XCTAssertTrue(continuation.waitForExistence(timeout: 12))
        XCTAssertTrue(continuation.label.contains("External CBZ"))
        continuation.tap()
        XCTAssertTrue(canvas.waitForExistence(timeout: 15))
        Thread.sleep(forTimeInterval: 0.6)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["PageCoinCurrentPage"].label, "2")
        back.tap()
        app.buttons["All"].firstMatch.tap()
        XCTAssertTrue(app.buttons[stableID].waitForExistence(timeout: 12))
        let linked = app.buttons[stableID]
        XCTAssertTrue((linked.value as? String ?? "").hasPrefix("Files,"))
        linked.press(forDuration: 1)
        app.buttons["Remove Link"].tap()
        let confirmation = app.alerts["Remove link?"]
        XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
        confirmation.buttons["Cancel"].tap()
        XCTAssertTrue(linked.waitForExistence(timeout: 5))
        linked.press(forDuration: 1)
        app.buttons["Remove Link"].tap()
        XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
        confirmation.buttons["Remove Link"].tap()
        XCTAssertTrue(linked.waitForNonExistence(timeout: 10))
    }

    func testCopyPDFStillImports() throws {
        continueAfterFailure = false
        try XCTSkipUnless((ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? "").hasPrefix("Leaf Read From Files QA "))
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let add = app.buttons["library.empty.add"]
        func browse(_ action: String, name: String) {
            XCTAssertTrue(add.waitForExistence(timeout: 10))
            add.tap()
            let source = app.buttons[action]
            XCTAssertTrue(source.waitForExistence(timeout: 5))
            source.tap()
            let cancel = app.buttons["Cancel"].firstMatch
            XCTAssertTrue(cancel.waitForExistence(timeout: 15))
            let file = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH %@", name)).firstMatch
            XCTAssertTrue(file.waitForExistence(timeout: 10))
            // Use the visible file name rather than the center of an iPad row extending under the split view.
            file.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch.tap()
            let open = app.buttons["Open"].firstMatch
            if open.waitForExistence(timeout: 3) { XCTAssertTrue(open.isEnabled); open.tap() }
        }
        browse("Copy from Files", name: "External PDF")
        XCTAssertTrue(add.waitForNonExistence(timeout: 15), "Copy path did not import the PDF")
        XCTAssertFalse(app.alerts["Import failed"].exists)
    }

}
