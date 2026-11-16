import XCTest
import UIKit

/// Separate synthetic Continue library; the original 16-comic header regressions keep their fixtures.
final class ContinueLibraryUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        try XCTSkipUnless((ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? "").hasPrefix("Leaf Header QA Completion "))
    }
    private func launch(theme: String = "light", large: Bool = false, spanish: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-leaf.appTheme", theme,
            "-AppleLanguages", spanish ? "(es-MX)" : "(en)", "-AppleLocale", spanish ? "es_MX" : "en_US"]
        if large { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 15))
        return app
    }
    private func capture(_ name: String, _ app: XCUIApplication) {
        let image = XCTAttachment(screenshot: app.screenshot()); image.name = name; image.lifetime = .keepAlways; add(image)
    }
    private func select(_ title: String, _ app: XCUIApplication, large: Bool = false) {
        if large { app.buttons["library.views"].tap(); app.buttons[title].tap() }
        else { app.segmentedControls["library.views"].buttons[title].tap() }
    }

    func testContinueAcrossAllViewsUnreadAndEmptySearchHidesUntilClosed() {
        let app = launch()
        let card = app.buttons["library.continue"].firstMatch
        for view in ["Series", "Lists", "All", "Offline"] {
            select(view, app)
            XCTAssertTrue(card.waitForExistence(timeout: 10))
            XCTAssertTrue(card.label.contains("QA Continue 01"))
        }
        app.buttons["library.unread"].tap()
        XCTAssertTrue(card.exists && app.buttons["library.clearUnread"].exists)
        app.buttons["SEARCH COMICS, SERIES OR LISTS…"].tap()
        XCTAssertTrue(app.textFields.firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields.firstMatch.value as? String, "SEARCH COMICS, SERIES OR LISTS…")
        XCTAssertFalse(card.exists, "Opening search must hide Continue before a query is typed")
        capture("continue-hidden-during-empty-search", app)
        app.buttons["Close"].tap()
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        app.buttons["library.clearUnread"].tap()
        app.buttons["MENU"].tap()
        XCTAssertTrue(app.buttons["Select comics"].waitForExistence(timeout: 5))
        capture("continue-menu-selection", app)
        app.buttons["Select comics"].tap()
        XCTAssertTrue(app.buttons["DONE"].waitForExistence(timeout: 5))
        XCTAssertFalse(card.exists)
        app.buttons["DONE"].tap()
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        capture("continue-restored-after-selection", app)
    }

    func testContinueOpensReaderAtSavedPageAndReturnsWithoutDetail() {
        let app = launch()
        app.buttons["library.continue"].firstMatch.tap()
        let canvas = app.otherElements["ReaderCanvas"]
        XCTAssertTrue(canvas.waitForExistence(timeout: 10))
        let status = app.staticTexts["PageStatus"]
        if !status.exists { canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap() }
        XCTAssertTrue(status.waitForExistence(timeout: 5))
        XCTAssertTrue(status.label.contains("2") && status.label.contains("4"), status.label)
        capture("continue-reader-saved-page", app)
        app.buttons["BackButton"].tap()
        XCTAssertTrue(app.buttons["library.continue"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.segmentedControls["library.views"].buttons["Series"].isSelected)
    }

    func testPhoneSingleAndTabletFiveCardRowWithAdaptiveHeader() {
        let app = launch()
        let views = app.segmentedControls["library.views"]
        let cards = app.buttons.matching(identifier: "library.continue")
        let tablet = UIDevice.current.userInterfaceIdiom == .pad
        XCTAssertEqual(cards.count, tablet ? 5 : 1)
        if tablet {
            XCTAssertEqual(cards.element(boundBy: 0).frame.width, 340, accuracy: 1)
            let row = app.scrollViews.containing(.button, identifier: "library.continue").allElementsBoundByIndex
                .first { $0.frame.height < 400 }
            XCTAssertNotNil(row)
            row?.swipeLeft()
            XCTAssertTrue(cards.element(boundBy: 4).isHittable)
            XCTAssertTrue(cards.element(boundBy: 4).label.contains("QA Continue 05"))
            row?.swipeRight()
            XCTAssertEqual(views.frame.midY, app.buttons["MENU"].frame.midY, accuracy: 8)
        }
        let add = app.buttons["Add comics"]
        XCTAssertGreaterThanOrEqual(add.frame.height, 44)
        let vertical = app.scrollViews.firstMatch
        XCTAssertGreaterThanOrEqual(add.frame.minY, vertical.frame.maxY - 1)
        capture(tablet ? "continue-tablet-light-row" : "continue-phone-light-single", app)
    }

    func testDarkAccessibilityAndSpanishKeepContinueUsable() {
        let app = launch(theme: "dark", large: true)
        let card = app.buttons["library.continue"].firstMatch
        XCTAssertGreaterThanOrEqual(card.frame.height, 44)
        XCTAssertLessThanOrEqual(card.frame.maxX, app.frame.maxX + 1)
        XCTAssertTrue(card.isHittable)
        select("All", app, large: true)
        capture("continue-dark-accessibility3", app)
        let spanish = launch(theme: "dark", spanish: true)
        XCTAssertTrue(spanish.buttons["Agregar cómics"].exists)
        spanish.buttons["MENÚ"].tap()
        XCTAssertTrue(spanish.buttons["Seleccionar cómics"].waitForExistence(timeout: 5))
        XCTAssertTrue(spanish.buttons["Ajustes y más"].exists)
        capture("continue-spanish-menu", spanish)
    }
}
