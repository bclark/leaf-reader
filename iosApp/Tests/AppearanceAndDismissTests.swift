import Foundation
import Testing
import SwiftUI
import UIKit
@testable import Leaf

@Suite @MainActor
struct AppearanceAndDismissTests {
    // Widths cover dense outer-display grids through large inner-display/iPad cards.
    @Test(arguments: [72.0, 140.0, 220.0, 301.0])
    func landscapeCoverKeepsCardBounds(width: Double) throws {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1200, height: 200)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1200, height: 200))
        }
        let artwork = LibraryCoverArtwork(image: image)
            .aspectRatio(0.7, contentMode: .fit)
        let renderer = ImageRenderer(content: artwork)
        renderer.scale = 1
        renderer.proposedSize = ProposedViewSize(width: width, height: nil)
        let rendered = try #require(renderer.uiImage)
        #expect(abs(rendered.size.width - width) <= 1)
        #expect(abs(rendered.size.height - width / 0.7) <= 1)
    }

    @Test(arguments: [CGSize(width: 1200, height: 200), CGSize(width: 200, height: 1200)])
    func coverArtworkDoesNotPaintIntoNeighboringCardGap(imageSize: CGSize) throws {
        let image = UIGraphicsImageRenderer(size: imageSize).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(origin: .zero, size: imageSize))
        }
        let renderer = ImageRenderer(content:
            HStack(spacing: 20) {
                LibraryCoverArtwork(image: image)
                    .aspectRatio(0.7, contentMode: .fit)
                    .frame(width: 140)
                Color.blue.frame(width: 140, height: 200)
            }
            .background(Color.green)
        )
        renderer.scale = 1
        let output = try #require(renderer.cgImage)
        #expect(output.width == 300)
        #expect(output.height == 200)
        var pixel = [UInt8](repeating: 0, count: 4)
        let gap = try #require(output.cropping(to: CGRect(x: 150, y: 100, width: 1, height: 1)))
        try pixel.withUnsafeMutableBytes { bytes in
            let context = try #require(CGContext(data: bytes.baseAddress, width: 1, height: 1,
                bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(gap, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        #expect(pixel[1] > pixel[0])
        #expect(pixel[1] > pixel[2])
    }

    @Test func unreadSingleOwnedSeriesCoverPreservesTopAndBottomArtworkWithoutEmptyTrack() throws {
        let url = URL(fileURLWithPath: "/fixture/Synthetic.cbz")
        func card(issues: Int, progress: Double) -> ComicStackCard {
            ComicStackCard(seriesName: "Fixture", volume: nil, issues: [url],
                           totalIssues: issues, unreadIssues: issues, progressFraction: progress,
                           onOpenSeries: {})
        }
        let single = card(issues: 1, progress: 0)
        #expect(single.keepsWholeCover)
        #expect(LibraryReadingState.local(nil) == .unread)
        let multiple = card(issues: 2, progress: 0)
        #expect(!multiple.keepsWholeCover)

        let image = UIGraphicsImageRenderer(size: CGSize(width: 140, height: 400)).image { ctx in
            UIColor.blue.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 140, height: 400))
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 140, height: 32))
            UIColor.green.setFill()
            ctx.fill(CGRect(x: 0, y: 368, width: 140, height: 32))
        }
        let renderer = ImageRenderer(content:
            LibraryCoverArtwork(image: image, contentMode: single.keepsWholeCover ? .fit : .fill)
                .frame(width: 140, height: 200)
                .background(.white)
        )
        renderer.scale = 1
        let output = try #require(renderer.cgImage)
        #expect(output.width == 140 && output.height == 200)

        func sample(x: Int, y: Int) throws -> [UInt8] {
            let pixel = try #require(output.cropping(to: CGRect(x: CGFloat(x), y: CGFloat(y), width: 1, height: 1)))
            var bytes = [UInt8](repeating: 0, count: 4)
            try bytes.withUnsafeMutableBytes { raw in
                let context = try #require(CGContext(data: raw.baseAddress, width: 1, height: 1,
                    bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                context.draw(pixel, in: CGRect(x: 0, y: 0, width: 1, height: 1))
            }
            return bytes
        }
        let top = try sample(x: 70, y: 5)
        let bottom = try sample(x: 70, y: 194)
        let neutral = try sample(x: 15, y: 100)
        #expect(top[0] > 200 && top[1] < 60 && top[2] < 60)
        #expect(bottom[1] > 200 && bottom[0] < 60 && bottom[2] < 60)
        #expect(neutral.prefix(3).allSatisfy { $0 > 230 })
    }

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
