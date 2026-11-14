import Foundation
import UIKit
import ZIPFoundation
import Testing
@testable import Leaf

@Suite(.serialized)
@MainActor
struct PerformanceRegressionTests {
    private struct Record: Codable, Sendable, Equatable {
        let ids: [String]
    }

    @Test func snapshotsMigrateValidateAndKeepAccountsSeparate() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = MetadataSnapshotStore(directory: root)
        let old = Record(ids: ["old"])
        let legacy = try JSONEncoder().encode(old)
        let migrated = try await store.read(Record.self, key: "account-a", legacy: legacy)
        #expect(migrated == old)
        #expect(try await store.data(for: "account-a") == legacy)
        let newer = Record(ids: ["new", "next"])
        try await store.write(keys: ["account-a"]) { try JSONEncoder().encode(newer) }
        #expect(try await store.read(Record.self, key: "account-a", legacy: legacy) == newer)
        #expect(try await store.read(Record.self, key: "account-b", legacy: nil) == nil)
        // Invalid legacy bytes cannot become a published snapshot.
        await #expect(throws: (any Error).self) {
            _ = try await store.read(Record.self, key: "corrupt", legacy: Data("broken".utf8))
        }
        await #expect(throws: (any Error).self) { _ = try await store.data(for: "corrupt") }
    }

    @Test func failedSnapshotWritePreservesPreviousCheckpoint() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = MetadataSnapshotStore(directory: root)
        let old = Record(ids: ["checkpoint"])
        try await store.write(keys: ["scope"]) { try JSONEncoder().encode(old) }
        await #expect(throws: CocoaError.self) {
            try await store.write(keys: ["scope"]) { throw CocoaError(.fileWriteOutOfSpace) }
        }
        #expect(try await store.read(Record.self, key: "scope", legacy: nil) == old)
        // An unwritable migration must fail so its caller can retain the preference blob.
        let blocked = root.appendingPathComponent("file")
        try Data().write(to: blocked)
        let unavailable = MetadataSnapshotStore(directory: blocked)
        await #expect(throws: (any Error).self) {
            _ = try await unavailable.read(Record.self, key: "scope", legacy: JSONEncoder().encode(old))
        }
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set(try JSONEncoder().encode(old), forKey: "scope")
        #expect(await unavailable.restore(Record.self, key: "scope", defaults: defaults) == old)
        #expect(defaults.data(forKey: "scope") != nil)
    }

    @Test func coverLoadsShareDecodeAndReplacementInvalidatesIt() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let url = root.appendingPathComponent("cover.cbz")
        try makeComic(url, color: .red, pages: 1)
        CoverLoader.invalidate(url)
        let requests = (0..<12).map { _ in Task { await CoverLoader.load(for: url) } }
        var images: [UIImage] = []
        for request in requests {
            let result = await request.value
            #expect(result.pageCount == 1)
            images.append(try #require(result.cover))
        }
        let first = try #require(images.first)
        #expect(images.allSatisfy { $0 === first })
        #expect(max(first.size.width * first.scale, first.size.height * first.scale) <= 600)
        #expect(CoverLoader.cachedCover(for: url) === first)
        CoverLoader.shared.purge()
        #expect(CoverLoader.cachedCover(for: url) == nil)
        try FileManager.default.removeItem(at: url)
        try makeComic(url, color: .blue, pages: 2)
        CoverLoader.invalidate(url)
        let replacement = await CoverLoader.load(for: url)
        #expect(replacement.pageCount == 2)
        #expect(replacement.cover !== first)
        CoverLoader.invalidate(url)
    }

    @Test func cancellingOneCoverConsumerDoesNotCancelTheOther() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let url = root.appendingPathComponent("cover.cbz")
        try makeComic(url, color: .green, pages: 1)
        CoverLoader.invalidate(url)
        let cancelled = Task { await CoverLoader.load(for: url) }
        let surviving = Task { await CoverLoader.load(for: url) }
        await Task.yield()
        cancelled.cancel()
        _ = await cancelled.value
        let result = await surviving.value
        #expect(result.cover != nil)
        #expect(result.pageCount == 1)
        CoverLoader.invalidate(url)
    }

    private func makeComic(_ url: URL, color: UIColor, pages: Int) throws {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1000, height: 1600), format: format).image { context in
            color.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1000, height: 1600))
        }
        let data = try #require(image.jpegData(compressionQuality: 0.8))
        let archive = try Archive(url: url, accessMode: .create)
        for page in 0..<pages {
            try archive.addEntry(with: "page\(page).jpg", type: .file, uncompressedSize: Int64(data.count)) { position, size in
                data.subdata(in: Int(position)..<Int(position) + size)
            }
        }
    }
}
