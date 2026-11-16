import Foundation
import Testing
@testable import Leaf

@MainActor
struct FirstLaunchOnboardingTests {
    private func fixture() throws -> (UserDefaults, URL, String) {
        let name = "leaf.onboarding.tests." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: name))
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return (defaults, directory, name)
    }

    @Test func freshMarkerSurvivesPreferencesCreatedByLaterModels() throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        let model = FirstLaunchOnboarding(defaults: defaults, directory: directory)
        #expect(model.step == .welcome)
        #expect(defaults.string(forKey: FirstLaunchOnboarding.key) == "welcome")
        _ = LeafSettings(defaults: defaults)
        defaults.set(UUID().uuidString, forKey: "leaf.jellyfin.device")
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).step == .welcome)
    }

    @Test(arguments: [false, true]) func existingChoiceIsPreservedEvenWithAnEmptyLibrary(_ choice: Bool) throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        defaults.set(choice, forKey: "leaf.jellyfin.onboardingChosen")
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).isComplete)
        #expect(defaults.bool(forKey: "leaf.jellyfin.onboardingChosen") == choice)
    }

    @Test(arguments: ["leaf.appTheme", "leaf.jellyfin.device", "smb.connection.v1", "leaf.progress.v1",
        "leaf.comicMetadata.v1", "leaf.readingLists.v1", "chika.lastOpened.v1", "chika.progress.v1",
        "leaf.readingDirection.default.rtl", "chika.readingDirection.default.rtl", "leaf.zoomFill", "chika.zoomFill"])
    func otherExistingInstallSignalsDoNotForceOnboarding(_ key: String) throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        defaults.set("preserved fixture", forKey: key)
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).isComplete)
        #expect(defaults.string(forKey: key) == "preserved fixture")
        #expect(defaults.object(forKey: "leaf.jellyfin.onboardingChosen") == nil)
    }

    @Test func existingFilesAndInterruptedPublicationRecoverWithoutReimporting() throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("existing.cbz")
        // Migration observes library presence, not archive validity or contents.
        try Data([1]).write(to: file)
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).isComplete)
        defaults.set("welcome", forKey: FirstLaunchOnboarding.key)
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).isComplete)
        #expect(try Data(contentsOf: file) == Data([1]))
    }

    @Test func onlyPersistedConnectionOrExplicitFinishCompletesPendingFlow() throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        let model = FirstLaunchOnboarding(defaults: defaults, directory: directory)
        model.showJellyfin()
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).step == .jellyfin)
        defaults.set(Data("invalid profile".utf8), forKey: "leaf.jellyfin.profile")
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).step == .jellyfin)
        let profile = JellyfinConnection(id: UUID().uuidString, baseURL: URL(string: "https://fixture.invalid")!,
            userID: "fixture-user", serverID: "fixture-server", username: "fixture", allowsHTTP: false)
        defaults.set(try JSONEncoder().encode(profile), forKey: "leaf.jellyfin.profile")
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).isComplete)
    }

    @Test func backSkipAndRelaunchCannotReenterCompletedFlow() throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        let model = FirstLaunchOnboarding(defaults: defaults, directory: directory)
        model.showJellyfin(); model.showWelcome()
        #expect(model.step == .welcome)
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).step == .welcome)
        model.finish(); model.showWelcome(); model.showJellyfin()
        #expect(model.isComplete)
        #expect(defaults.bool(forKey: "leaf.jellyfin.onboardingChosen"))
        #expect(FirstLaunchOnboarding(defaults: defaults, directory: directory).isComplete)
    }

    @Test(arguments: ["future-version-99", ""])
    func unknownStateIsPreservedWithoutForcingOrOverwritingIt(_ value: String) throws {
        let (defaults, directory, suite) = try fixture()
        defer { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: directory) }
        defaults.set(value, forKey: FirstLaunchOnboarding.key)
        let model = FirstLaunchOnboarding(defaults: defaults, directory: directory)
        #expect(model.isComplete)
        model.finish(); model.showWelcome()
        #expect(defaults.string(forKey: FirstLaunchOnboarding.key) == value)
        #expect(defaults.object(forKey: "leaf.jellyfin.onboardingChosen") == nil)
    }
}
