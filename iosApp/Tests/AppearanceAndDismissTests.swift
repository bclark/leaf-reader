import Foundation
import Testing
import SwiftUI
import UIKit
@testable import Leaf

@Suite @MainActor
struct AppearanceAndDismissTests {
    @Test func nativeAppearanceCanClearForcedModeWithoutDismissing() async throws {
        let host = UIHostingController(rootView: Color.clear.leafAppearance(.dark))
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        try await Task.sleep(nanoseconds: 100_000_000)
        #expect(host.overrideUserInterfaceStyle == .dark)
        host.rootView = Color.clear.leafAppearance(.light)
        try await Task.sleep(nanoseconds: 100_000_000)
        #expect(host.overrideUserInterfaceStyle == .light)
        host.rootView = Color.clear.leafAppearance(.system)
        try await Task.sleep(nanoseconds: 100_000_000)
        #expect(host.overrideUserInterfaceStyle == .unspecified)
    }

    @Test func coverTransitionKeepsOpeningStateWhileProgressChanges() {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).cbz")
        defer { ReadingProgress.clear(url) }
        let unread = OpenComic(url: url)
        #expect(unread.opensAtCover)
        ReadingProgress.set(url, page: 3, step: -1, total: 10, rtl: false)
        #expect(unread.opensAtCover)
        #expect(!OpenComic(url: url).opensAtCover)
        ReadingProgress.set(url, page: 0, step: -1, total: 10, rtl: false)
        #expect(OpenComic(url: url).opensAtCover)
    }

    @Test func themeMigrationIsOneTimeAndHonorsLegacyAmoled() throws {
        let suite = "LeafAppearanceTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set(true, forKey: "chika.amoledTheme")
        let settings = LeafSettings(defaults: defaults)
        #expect(settings.theme == .dark)
        settings.theme = .system
        settings.amoled = true
        #expect(LeafSettings(defaults: defaults).theme == .system)
        settings.theme = .light
        #expect(LeafSettings(defaults: defaults).theme == .light)
    }

    @Test func newInstallRemainsSystemAfterEnablingAmoled() throws {
        let suite = "LeafAppearanceTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = LeafSettings(defaults: defaults)
        #expect(settings.theme == .system)
        settings.amoled = true
        #expect(LeafSettings(defaults: defaults).theme == .system)
    }

    @Test func dragEngagesAfter48PointsAndKeepsItsCapturedOrigin() {
        let motion = ReaderDismissMotion()
        defer { motion.reset() }
        motion.begin(eligible: true, touches: 1)
        motion.change(CGSize(width: 10, height: 40))
        #expect(motion.offset == .zero)
        motion.change(CGSize(width: 24, height: 48))
        motion.change(CGSize(width: 50, height: 100))
        #expect(abs(motion.offset.width - 14.3) < 0.001)
        #expect(abs(motion.offset.height - 28.6) < 0.001)
        motion.change(CGSize(width: 50, height: 100))
        #expect(abs(motion.offset.height - 28.6) < 0.001)
    }

    @Test func cancellationAndMultipleTouchesCannotCommit() {
        let motion = ReaderDismissMotion()
        defer { motion.reset() }
        motion.begin(eligible: true, touches: 1)
        motion.change(CGSize(width: 0, height: 300))
        motion.cancelGesture()
        motion.release(velocity: CGSize(width: 0, height: 4000), height: 800) {
            Issue.record("Cancelled gesture navigated")
        }
        #expect(!motion.committed)
        motion.begin(eligible: true, touches: 2)
        motion.change(CGSize(width: 0, height: 800))
        #expect(motion.intent == .cancelled)
        #expect(!motion.committed)
    }

    @Test func intentLocksAndTallContentKeepsPanning() {
        let motion = ReaderDismissMotion()
        defer { motion.reset() }
        motion.begin(eligible: true, touches: 1)
        motion.change(CGSize(width: 30, height: 10))
        motion.change(CGSize(width: 40, height: 500))
        #expect(motion.intent == .horizontal)
        #expect(motion.offset == .zero)
        motion.begin(eligible: false, touches: 1)
        motion.change(CGSize(width: 0, height: 500))
        #expect(motion.intent == .pan)
        #expect(motion.offset == .zero)
    }

    @Test func flyOutNavigatesOnceAndResetUnlocksInput() async throws {
        let motion = ReaderDismissMotion()
        defer { motion.reset() }
        var dismissCount = 0
        motion.begin(eligible: true, touches: 1)
        motion.change(CGSize(width: 0, height: 150))
        motion.release(velocity: CGSize(width: 500, height: 2000), height: 800) { dismissCount += 1 }
        #expect(motion.committed)
        motion.release(velocity: CGSize(width: 500, height: 2000), height: 800) { dismissCount += 1 }
        try await Task.sleep(nanoseconds: 400_000_000)
        #expect(dismissCount == 1)
        motion.reset()
        #expect(!motion.committed)
        #expect(motion.offset == .zero)
        motion.begin(eligible: true, touches: 1)
        motion.change(CGSize(width: 0, height: 150))
        motion.release(velocity: CGSize(width: 0, height: 2000), height: 800) { dismissCount += 1 }
        motion.reset()
        try await Task.sleep(nanoseconds: 350_000_000)
        #expect(dismissCount == 1)
    }

    @Test func allIncludesEmptyListsAndPreservesAllOrderedMembers() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let library = LibraryStore(directory: directory)
        defer { try? FileManager.default.removeItem(at: directory) }
        let name = "List-\(UUID().uuidString)"
        let list = library.createReadingList(name: name)
        defer { library.deleteReadingList(id: list.id) }
        let emptyItems = library.libraryItems(filter: .all, searchQuery: "  \(name.lowercased())  ")
        #expect(emptyItems.count == 1)
        if case .readingListItem(_, let previews, let count) = emptyItems.first {
            #expect(previews.isEmpty)
            #expect(count == 0)
        } else { Issue.record("Missing empty reading list") }
        let urls = (1...6).map { directory.appendingPathComponent("issue\($0).cbz") }
        for url in urls { try Data().write(to: url) }
        library.refresh(backfill: false)
        library.addComicsToReadingList(listId: list.id, urls: urls.reversed())
        let items = library.libraryItems(filter: .all, searchQuery: name)
        if case .readingListItem(_, let previews, let count) = items.first {
            #expect(previews == Array(urls.reversed()))
            #expect(count == 6)
        } else { Issue.record("Missing named reading list") }
        #expect(library.libraryItems(filter: .all, searchQuery: "").count >= 7)
        #expect(library.libraryItems(filter: .series, searchQuery: name).isEmpty)
        #expect(library.libraryItems(filter: .unread, searchQuery: name).isEmpty)
    }
}
