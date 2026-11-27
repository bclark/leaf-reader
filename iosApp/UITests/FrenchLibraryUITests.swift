import XCTest

/// Synthetic fixture coverage; content titles are intentionally not translated.
final class FrenchLibraryUITests: XCTestCase {
    override func setUpWithError() throws {
        guard ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.hasPrefix("Leaf Post12 QA ") == true else {
            throw XCTSkip("Requires the named task-owned synthetic Library simulator fixture")
        }
    }

    func testFrenchLibraryAppearanceAndListCancel() {
        continueAfterFailure = false
        let app = XCUIApplication()
        defer { app.terminate() }
        app.launchArguments = ["-leaf.jellyfin.onboardingChosen", "YES", "-AppleLanguages", "(fr)",
            "-AppleLocale", "fr_FR", "-leaf.appTheme", "dark",
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        let menu = app.buttons["MENU"]
        XCTAssertTrue(menu.waitForExistence(timeout: 15)); menu.tap()
        app.buttons["Apparence de la bibliothèque"].tap()
        for key in ["titles", "details", "reading"] {
            let control = app.switches["library.appearance." + key]
            for _ in 0..<5 where !control.isHittable { app.swipeUp() }
            XCTAssertTrue(control.exists && control.isHittable)
        }
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "french-dark-largest-appearance"; shot.lifetime = .keepAlways; add(shot)
        app.buttons["Terminé"].tap()
        app.buttons["library.views"].tap(); app.buttons["Listes"].tap()
        let create = app.buttons["Créer une liste de lecture"]
        XCTAssertTrue(create.waitForExistence(timeout: 5) && create.isHittable)
        create.tap()
        let field = app.textFields["library.newListName"]
        XCTAssertTrue(field.waitForExistence(timeout: 5)); field.tap(); field.typeText("Été — à lire")
        let cancel = app.navigationBars.buttons["Annuler"]
        XCTAssertTrue(cancel.isHittable); cancel.tap()
        XCTAssertTrue(create.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Été — à lire"].exists)
    }
}
