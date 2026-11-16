import Foundation
import Combine

/// A pending marker is written before other app models create first-launch preferences.
@MainActor
final class FirstLaunchOnboarding: ObservableObject {
    enum Step: String, Hashable { case welcome, jellyfin, complete }
    static let key = "leaf.firstLaunch.v1"
    @Published private(set) var step: Step
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard, directory: URL? = nil) {
        self.defaults = defaults
        Self.prepare(defaults: defaults, directory: directory)
        step = defaults.string(forKey: Self.key).flatMap(Step.init(rawValue:)) ?? .complete
    }

    var isComplete: Bool { step == .complete }

    static func prepare(defaults: UserDefaults = .standard, directory: URL? = nil) {
        if defaults.object(forKey: key) != nil {
            // Preserve unknown future/corrupt state rather than forcing an existing reader through onboarding.
            guard let raw = defaults.string(forKey: key), let saved = Step(rawValue: raw), saved != .complete else { return }
            let savedConnection = defaults.data(forKey: "leaf.jellyfin.profile").flatMap {
                try? JSONDecoder().decode(JellyfinConnection.self, from: $0)
            }
            if savedConnection != nil || hasComics(directory: directory) {
                defaults.set(Step.complete.rawValue, forKey: key)
            }
            return
        }
        let legacyKeys = [
            "leaf.jellyfin.onboardingChosen", "leaf.jellyfin.device", "leaf.jellyfin.profile",
            "smb.connection.v1", "leaf.appTheme", "leaf.amoledTheme", "leaf.pageSlideEnabled",
            "leaf.libraryColumns", "leaf.progress.v1", "leaf.lastOpened.v1",
            "leaf.comicMetadata.v1", "leaf.readingLists.v1", "chika.amoledTheme",
            "chika.pageSlideEnabled", "chika.libraryColumns", "chika.lastOpened.v1",
            "chika.progress.v1", "leaf.readingDirection.default.rtl", "chika.readingDirection.default.rtl",
            "leaf.zoomFill", "chika.zoomFill"
        ]
        let existing = legacyKeys.contains { defaults.object(forKey: $0) != nil } || hasComics(directory: directory)
        defaults.set(existing ? Step.complete.rawValue : Step.welcome.rawValue, forKey: key)
    }

    private static func hasComics(directory: URL?) -> Bool {
        let root = directory ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Comics", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(at: root,
            includingPropertiesForKeys: [.isRegularFileKey], options: .skipsHiddenFiles)) ?? []
        return files.contains {
            ["cbz", "zip", "pdf"].contains($0.pathExtension.lowercased()) &&
                (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
    }

    func showJellyfin() { if !isComplete { save(.jellyfin) } }
    func showWelcome() { if !isComplete { save(.welcome) } }

    /// Only explicit skip or a confirmed import/connection calls this method.
    func finish() {
        guard !isComplete else { return }
        defaults.set(true, forKey: "leaf.jellyfin.onboardingChosen")
        save(.complete)
    }

    private func save(_ step: Step) {
        defaults.set(step.rawValue, forKey: Self.key)
        self.step = step
    }
}
