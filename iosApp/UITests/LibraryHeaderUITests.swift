import XCTest
import UIKit

/// Runs only on task-owned simulators seeded with synthetic local files and lists.
final class LibraryHeaderUITests: XCTestCase {
    private func launch(theme: String = "light", largeText: Bool = false, spanish: Bool = false,
                        selectAll: Bool = true) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-leaf.appTheme", theme,
            "-AppleLanguages", spanish ? "(es-MX)" : "(en)", "-AppleLocale", spanish ? "es_MX" : "en_US"]
        if largeText { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        // Arrangement/grid regressions explicitly choose All; production cold launch remains Series.
        if selectAll && !largeText && !spanish {
            let views = app.segmentedControls["library.views"]
            XCTAssertTrue(views.waitForExistence(timeout: 15))
            views.buttons["All"].tap()
        }
        return app
    }

    func testSeriesColdDefaultOrderAndInSessionSelection() {
        continueAfterFailure = false
        let app = launch(selectAll: false)
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        XCTAssertTrue(views.buttons["Series"].isSelected)
        let order = ["Series", "Lists", "All", "Offline"]
        for (left, right) in zip(order, order.dropFirst()) {
            XCTAssertLessThan(views.buttons[left].frame.midX, views.buttons[right].frame.midX)
        }
        capture("series-cold-default", app: app)
        views.buttons["Lists"].tap()
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(views.waitForExistence(timeout: 10))
        XCTAssertTrue(views.buttons["Lists"].isSelected)
        app.terminate(); app.launch()
        XCTAssertTrue(views.waitForExistence(timeout: 10))
        XCTAssertTrue(views.buttons["Series"].isSelected)
    }

    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testLibraryPrimaryActionReservesSpaceAndKeepsCreateListReachable() {
        continueAfterFailure = false
        for (theme, largeText) in [("light", false), ("dark", true)] {
            let app = launch(theme: theme, largeText: largeText)
            if largeText {
                let picker = app.buttons["library.views"]
                XCTAssertTrue(picker.waitForExistence(timeout: 15))
                picker.tap()
                app.buttons["Lists"].tap()
            } else {
                let views = app.segmentedControls["library.views"]
                XCTAssertTrue(views.waitForExistence(timeout: 15))
                views.buttons["Lists"].tap()
            }
            let action = app.buttons["Create Reading List"]
            XCTAssertTrue(action.waitForExistence(timeout: 5))
            capture("library-primary-action-" + theme, app: app)
            XCTAssertGreaterThanOrEqual(action.frame.height, 44)
            let content = app.scrollViews.firstMatch
            XCTAssertGreaterThanOrEqual(action.frame.minY, content.frame.maxY - 1,
                "The primary action must have its own space instead of covering library content")
            let addTile = app.buttons["CREATE LIST"]
            for _ in 0..<12 {
                if addTile.exists && addTile.isHittable && addTile.frame.maxY <= content.frame.maxY { break }
                scrollUp(app)
            }
            XCTAssertTrue(addTile.exists && addTile.isHittable)
            XCTAssertLessThanOrEqual(addTile.frame.maxY, content.frame.maxY)
            capture("library-primary-action-final-item-" + theme, app: app)
            action.tap()
            let name = app.textFields["library.newListName"]
            XCTAssertTrue(name.waitForExistence(timeout: 5))
            XCTAssertFalse(app.navigationBars.buttons["Create & Add Comics"].isEnabled)
            name.tap(); name.typeText("Discarded QA List")
            capture("library-create-list-form-" + theme, app: app)
            let cancel = app.navigationBars.buttons["Cancel"]
            XCTAssertTrue(cancel.exists && cancel.isHittable)
            cancel.tap()
            XCTAssertTrue(action.waitForExistence(timeout: 5) && action.isHittable)
        }
        // Verify the existing Create & Add Comics route, then remove only this test's list.
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        views.buttons["Lists"].tap()
        app.buttons["Create Reading List"].tap()
        let name = app.textFields["library.newListName"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap(); name.typeText("QA Created List")
        let create = app.navigationBars.buttons["Create & Add Comics"]
        XCTAssertTrue(create.exists && create.isEnabled)
        create.tap()
        XCTAssertTrue(app.navigationBars["Add Comics to List"].waitForExistence(timeout: 5))
        capture("library-create-and-add-comics", app: app)
        app.navigationBars.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
        app.buttons["Back"].tap()
        let created = app.buttons["QA Created List"]
        XCTAssertTrue(created.waitForExistence(timeout: 5))
        created.press(forDuration: 1)
        app.buttons["Delete List"].tap()
        XCTAssertFalse(created.exists)
    }

    func testMixedTitleCardsKeepIntrinsicHeightsAndAlignTopEdges() {
        continueAfterFailure = false
        let app = launch()
        XCTAssertTrue(app.segmentedControls["library.views"].waitForExistence(timeout: 15))
        let single = app.buttons.containing(.staticText, identifier: "QA Completed").firstMatch
        let series = app.buttons["QA Series"]
        XCTAssertTrue(single.exists && single.isHittable && series.exists && series.isHittable)
        XCTAssertEqual(single.frame.width, series.frame.width, accuracy: 1)
        XCTAssertEqual(single.frame.height, series.frame.height, accuracy: 12,
            "Single comics and series must share cover sizing; only stack peeking may add a small offset")
        let longTitle = "QA Shelf 01 — A deliberately long comic title that wraps onto a second line"
        let long = app.buttons.containing(.staticText, identifier: longTitle).firstMatch
        let short = app.buttons.containing(.staticText, identifier: "QA Shelf 02").firstMatch
        for _ in 0..<3 {
            if long.exists && long.isHittable && short.exists && short.isHittable { break }
            scrollUp(app)
        }
        XCTAssertTrue(long.exists && long.isHittable && short.exists && short.isHittable)
        XCTAssertEqual(long.frame.minY, short.frame.minY, accuracy: 1,
            "Mixed title lengths must not shift neighboring covers vertically")
        if UIDevice.current.userInterfaceIdiom == .phone {
            XCTAssertGreaterThan(long.frame.height, short.frame.height + 5,
                "A one-line title must keep its intrinsic height instead of adopting its neighbor's height")
        }
        capture("library-mixed-title-top-alignment", app: app)
    }

    func testOfflineUnreadSeriesListsSearchAndReaderReturn() throws {
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        capture("library-all", app: app)
        for title in ["All", "Series", "Lists", "Offline"] {
            XCTAssertGreaterThanOrEqual(views.buttons[title].frame.height, 44)
            XCTAssertGreaterThanOrEqual(views.buttons[title].frame.width, 44)
        }
        let unread = app.buttons["library.unread"]
        XCTAssertTrue(unread.exists)
        XCTAssertGreaterThanOrEqual(unread.frame.width, 44)
        XCTAssertGreaterThanOrEqual(unread.frame.height, 44)
        unread.tap()
        let clear = app.buttons["library.clearUnread"]
        XCTAssertTrue(clear.waitForExistence(timeout: 3))
        for name in ["Series", "Lists", "Offline", "All"] {
            views.buttons[name].tap()
            XCTAssertTrue(clear.exists, "Unread state was lost in " + name)
            XCTAssertTrue(views.buttons[name].isSelected)
        }
        views.buttons["Series"].tap()
        let series = app.buttons["QA Series"]
        XCTAssertTrue(series.waitForExistence(timeout: 5))
        series.tap()
        XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["library.clearUnread"].exists)
        capture("series-unread", app: app)
        app.buttons["Back"].tap()
        XCTAssertTrue(views.buttons["Series"].waitForExistence(timeout: 5))
        XCTAssertTrue(views.buttons["Series"].isSelected)
        views.buttons["Lists"].tap()
        let list = app.buttons["QA Reading Order"]
        XCTAssertTrue(list.waitForExistence(timeout: 5))
        list.tap()
        XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["library.clearUnread"].exists)
        capture("list-unread", app: app)
        app.buttons["Back"].tap()
        views.buttons["Offline"].tap()
        capture("offline-unread", app: app)
        clear.tap()
        XCTAssertFalse(clear.exists)
        XCTAssertTrue(views.buttons["Offline"].isSelected)
        app.buttons["SEARCH COMICS, SERIES OR LISTS…"].tap()
        let search = app.textFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tap(); search.typeText("QA Standalone")
        app.swipeDown()
        let comic = app.staticTexts["QA Standalone"]
        XCTAssertTrue(comic.waitForExistence(timeout: 5))
        app.buttons["library.unread"].tap()
        XCTAssertTrue(app.buttons["library.clearUnread"].exists)
        comic.tap()
        let canvas = app.otherElements["ReaderCanvas"]
        XCTAssertTrue(canvas.waitForExistence(timeout: 10))
        XCTAssertTrue(NSPredicate(format: "isHittable == true").evaluate(with: canvas))
        capture("offline-reader", app: app)
        canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let back = app.buttons["BackButton"]
        if !back.waitForExistence(timeout: 2) {
            canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        XCTAssertTrue(back.waitForExistence(timeout: 3))
        back.tap()
        XCTAssertTrue(views.waitForExistence(timeout: 5))
        XCTAssertTrue(views.buttons["Offline"].isSelected)
        XCTAssertEqual(app.textFields.firstMatch.value as? String, "QA Standalone")
        XCTAssertTrue(app.buttons["library.clearUnread"].exists)
        capture("offline-return-search", app: app)
    }

    private func scrollUp(_ app: XCUIApplication, column: CGFloat = 0.25) {
        let content = app.scrollViews.firstMatch
        XCTAssertTrue(content.exists && content.isHittable)
        content.coordinate(withNormalizedOffset: CGVector(dx: column, dy: 0.8))
            .press(forDuration: 0.05,
                   thenDragTo: content.coordinate(withNormalizedOffset: CGVector(dx: column, dy: 0.2)))
    }

    func testUpwardSwipeOnSeriesCardScrollsLibrary() {
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        let series = app.buttons["QA Series"]
        XCTAssertTrue(series.exists && series.isHittable)
        // A prior reader return can add Continue and move this card to the next row.
        // Arrange enough visible cover before measuring the original upward gesture.
        for _ in 0..<4 {
            if series.frame.intersection(app.scrollViews.firstMatch.frame).height > 120 { break }
            scrollUp(app, column: 0.75)
        }
        let viewport = app.scrollViews.firstMatch.frame
        let frame = series.frame.intersection(viewport)
        XCTAssertGreaterThan(frame.height, 120)
        let start = app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.7))
        let end = app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.2))
        let beforeY = series.frame.minY
        capture("series-card-before-upward-scroll", app: app)
        start.press(forDuration: 0.05, thenDragTo: end)
        capture("series-card-after-upward-scroll", app: app)
        XCTAssertTrue(views.buttons["All"].isSelected)
        XCTAssertLessThan(series.frame.minY, beforeY - 40, "An upward drag over the Series cover must scroll the Library")
    }

    func testDownwardPeelCyclesSeriesAndListCovers() {
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        for (view, title, count) in [("Series", "QA Series", 2), ("Lists", "QA Reading Order", 3)] {
            views.buttons[view].tap()
            let card = app.buttons[title]
            XCTAssertTrue(card.exists && card.isHittable)
            let initial = card.value as? String
            XCTAssertNotNil(initial)
            for index in 0..<count {
                let frame = card.frame.intersection(app.scrollViews.firstMatch.frame)
                let origin = app.coordinate(withNormalizedOffset: .zero)
                origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.15))
                    .press(forDuration: 0.05, thenDragTo: origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.height * 0.85)))
                if index == 0 { XCTAssertNotEqual(card.value as? String, initial) }
            }
            XCTAssertEqual(card.value as? String, initial, "A full downward-peel cycle must return to its starting cover")
            capture(view + "-downward-peel-cycle", app: app)
            card.tap()
            XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
            app.buttons["Back"].tap()
            XCTAssertTrue(views.buttons[view].isSelected)
            card.press(forDuration: 1)
            let open = app.buttons[view == "Series" ? "Open Series" : "Open List"]
            XCTAssertTrue(open.waitForExistence(timeout: 5), "The native peel layer must preserve context menus")
            capture(view + "-context-menu", app: app)
            open.tap()
            XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 5))
            app.buttons["Back"].tap()
            XCTAssertTrue(views.buttons[view].isSelected)
        }
    }

    func testScrollRestorationAndExistingSourceActions() {
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        let target = app.staticTexts["QA Shelf 12"]
        for _ in 0..<16 {
            if target.exists && target.isHittable { break }
            scrollUp(app)
        }
        if !target.exists || !target.isHittable { capture("scroll-target-unreachable", app: app) }
        XCTAssertTrue(target.exists && target.isHittable)
        let savedShelfY = target.frame.minY
        capture("library-scrolled-before", app: app)
        views.buttons["Series"].tap()
        views.buttons["All"].tap()
        XCTAssertTrue(target.waitForExistence(timeout: 5))
        XCTAssertTrue(target.isHittable, "Returning to All lost the saved shelf position")
        XCTAssertEqual(target.frame.minY, savedShelfY, accuracy: 3,
            "Returning to All must preserve the shelf's partial scroll offset")
        capture("library-scrolled-return", app: app)
        let add = app.buttons["Add comics"]
        XCTAssertTrue(add.exists && add.isHittable)
        XCTAssertGreaterThanOrEqual(add.frame.minY, app.scrollViews.firstMatch.frame.maxY - 1,
            "The reserved Add Comics band must stay outside scrolling covers")
        add.tap()
        XCTAssertTrue(app.buttons["From Files"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["From SMB Drive"].exists)
        XCTAssertTrue(app.buttons["From Jellyfin"].exists)
        capture("source-chooser", app: app)
        if app.buttons["Cancel"].exists { app.buttons["Cancel"].tap() }
        else { app.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.1)).tap() }
        app.buttons["MENU"].tap()
        XCTAssertTrue(app.buttons["Settings and more"].waitForExistence(timeout: 5))
        capture("library-menu-dropdown", app: app)
        app.buttons["Settings and more"].tap()
        let direction = app.switches["Default right-to-left reading"]
        for _ in 0..<6 {
            if direction.exists && direction.isHittable { break }
            scrollUp(app)
        }
        XCTAssertTrue(direction.exists && direction.isHittable)
        XCTAssertTrue(app.staticTexts["Grid Columns"].exists)
        capture("menu-reading-and-grid-preferences", app: app)
        app.buttons["Back"].tap()
        XCTAssertTrue(views.waitForExistence(timeout: 5))
    }

    func testSelectedComicCanCreateReadingListFromAddSheet() {
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        app.buttons["MENU"].tap()
        XCTAssertTrue(app.buttons["Select comics"].waitForExistence(timeout: 5))
        app.buttons["Select comics"].tap()
        let comic = app.buttons.containing(.staticText, identifier: "QA Completed").firstMatch
        XCTAssertTrue(comic.exists && comic.isHittable)
        comic.tap()
        let add = app.buttons["ADD TO LIST"]
        XCTAssertTrue(add.exists && add.isEnabled)
        add.tap()
        let newList = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "CREATE NEW READING LIST")).firstMatch
        XCTAssertTrue(newList.waitForExistence(timeout: 5))
        newList.tap()
        let name = app.textFields["library.newListName"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap(); name.typeText("QA Selected List")
        app.navigationBars.buttons["Create"].tap()
        XCTAssertTrue(views.waitForExistence(timeout: 5))
        views.buttons["Lists"].tap()
        let created = app.buttons["QA Selected List"]
        XCTAssertTrue(created.waitForExistence(timeout: 5))
        created.tap()
        XCTAssertTrue(app.staticTexts["QA Completed"].waitForExistence(timeout: 5))
        capture("library-created-list-preserves-selection", app: app)
        app.buttons["Back"].tap()
        XCTAssertTrue(created.waitForExistence(timeout: 5))
        created.press(forDuration: 1)
        app.buttons["Delete List"].tap()
        XCTAssertFalse(created.exists)
    }

    func testTabletRotationKeepsViewAndUnread() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad, "Tablet-only rotation coverage")
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        views.buttons["Offline"].tap()
        app.buttons["library.unread"].tap()
        defer { XCUIDevice.shared.orientation = .portrait }
        func captureDisplay(_ name: String) {
            let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            attachment.name = name
            attachment.lifetime = .keepAlways
            add(attachment)
            let bounds = XCTAttachment(string: "App: \(app.frame); selector: \(views.frame); menu: \(app.buttons["MENU"].frame)")
            bounds.name = name + "-bounds"
            bounds.lifetime = .keepAlways
            add(bounds)
        }
        func waitForViewport(landscape: Bool) {
            let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                let bounds = app.frame
                let offline = views.buttons["Offline"]
                let menu = app.buttons["MENU"]
                let chip = app.buttons["library.clearUnread"]
                return (landscape ? bounds.width > bounds.height : bounds.height > bounds.width)
                    && offline.isHittable && menu.isHittable && chip.isHittable
                    && views.frame.maxX <= bounds.maxX + 1
                    && menu.frame.maxX <= bounds.maxX + 1
                    && chip.frame.maxX <= bounds.maxX + 1
            }, object: app)
            XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 8), .completed,
                "Wait for the new viewport and complete header layout before accepting rotation")
        }
        XCUIDevice.shared.orientation = .landscapeLeft
        waitForViewport(landscape: true)
        XCTAssertTrue(app.buttons["library.clearUnread"].waitForExistence(timeout: 5))
        XCTAssertTrue(views.buttons["Offline"].isSelected)
        capture("tablet-landscape-offline-unread", app: app)
        captureDisplay("tablet-landscape-full-display")
        XCUIDevice.shared.orientation = .portrait
        waitForViewport(landscape: false)
        XCTAssertTrue(app.buttons["library.clearUnread"].waitForExistence(timeout: 5))
        XCTAssertTrue(views.buttons["Offline"].isSelected)
        capture("tablet-portrait-offline-unread", app: app)
        captureDisplay("tablet-portrait-full-display")
    }

    func testZZEmptyOfflineExplainsAddingLocalCopiesWithEitherUnreadState() throws {
        // Never remove a user's library if this suite is selected outside the disposable QA devices.
        try XCTSkipUnless((ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? "").hasPrefix("Leaf Header QA "),
            "Fixture removal is limited to task-owned Header QA simulators")
        continueAfterFailure = false
        let app = launch()
        let views = app.segmentedControls["library.views"]
        XCTAssertTrue(views.waitForExistence(timeout: 15))
        views.buttons["Offline"].tap()
        app.buttons["MENU"].tap()
        XCTAssertTrue(app.buttons["Select comics"].waitForExistence(timeout: 5))
        app.buttons["Select comics"].tap()
        let selectAll = app.buttons["SELECT ALL"]
        XCTAssertTrue(selectAll.waitForExistence(timeout: 5))
        selectAll.tap()
        XCTAssertTrue(app.staticTexts["16 SELECTED"].waitForExistence(timeout: 5))
        app.buttons["DELETE"].tap()
        let confirm = app.alerts.buttons["Delete"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts.staticTexts["Delete 16 comics?"].exists)
        confirm.tap()
        let message = app.staticTexts["Connect your Jellyfin library, or read files stored on this device. You can connect later from Add Comics."]
        XCTAssertTrue(message.waitForExistence(timeout: 15))
        app.buttons["library.unread"].tap()
        XCTAssertTrue(app.buttons["library.clearUnread"].exists)
        XCTAssertTrue(message.exists)
        capture("offline-empty-unread", app: app)
        app.buttons["library.clearUnread"].tap()
        XCTAssertTrue(message.exists)
        XCTAssertTrue(views.buttons["Offline"].isSelected)
        let add = app.buttons["library.empty.add"]
        let connect = app.buttons["library.empty.connect"]
        XCTAssertTrue(add.exists && add.isHittable && connect.exists && connect.isHittable)
        XCTAssertGreaterThanOrEqual(add.frame.height, 44)
        XCTAssertGreaterThanOrEqual(connect.frame.height, 44)
        capture("offline-empty", app: app)
    }

    func testDarkAccessibilityHeaderAndSpanishLabels() {
        continueAfterFailure = false
        let app = launch(theme: "dark", largeText: true)
        XCTAssertTrue(app.buttons["library.unread"].waitForExistence(timeout: 15))
        // At accessibility sizes the same four choices use a native menu picker.
        let picker = app.buttons["library.views"]
        XCTAssertTrue(picker.exists)
        picker.tap()
        app.buttons["Offline"].tap()
        app.buttons["library.unread"].tap()
        XCTAssertTrue(app.buttons["library.clearUnread"].exists)
        capture("offline-dark-accessibility", app: app)
        app.terminate()
        let spanish = launch(spanish: true)
        XCTAssertTrue(spanish.segmentedControls["library.views"].waitForExistence(timeout: 15))
        XCTAssertTrue(spanish.segmentedControls["library.views"].buttons["Sin conexión"].exists)
        spanish.buttons["library.unread"].tap()
        XCTAssertTrue(spanish.buttons["library.clearUnread"].exists)
        capture("library-es-MX", app: spanish)
    }
}
