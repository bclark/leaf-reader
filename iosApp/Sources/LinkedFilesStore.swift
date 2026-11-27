import SwiftUI
import UniformTypeIdentifiers

/// A device-local shelf entry. Neither this identifier nor the bookmark is an owned archive.
struct LinkedComic: Codable, Identifiable, Equatable {
    let id: UUID
    let bookmark: Data
    let title: String
    let pageCount: Int
    let fingerprint: String
    var metadata: ComicMetadataRecord? = nil

    var shelfMetadata: ComicMetadataRecord {
        metadata ?? ComicMetadataStore.parseMetadata(filename: title + ".cbz")
    }
    var listToken: String { "leaf-files:" + id.uuidString }
    static func listID(_ token: String) -> UUID? {
        guard token.hasPrefix("leaf-files:") else { return nil }
        return UUID(uuidString: String(token.dropFirst("leaf-files:".count)))
    }

    // Existing progress uses the last path component; a no-extension UUID cannot match
    // an imported CBZ/PDF filename. Never send this identity to archive or deletion APIs.
    var progressURL: URL { URL(string: "leaf-files://shelf/linked-\(id.uuidString)")! }

    func resolveURL() throws -> URL {
        var stale = false
        let url = try URL(resolvingBookmarkData: bookmark,
                          options: [.withoutUI, .withoutImplicitStartAccessing],
                          relativeTo: nil, bookmarkDataIsStale: &stale)
        // A stale resolution does not silently migrate a saved identity or its progress.
        guard !stale else { throw CocoaError(.fileReadNoSuchFile) }
        return url
    }
}

@MainActor
final class LinkedFilesStore: ObservableObject {
    struct Prepared: Sendable {
        let bookmark: Data
        let title: String
        let pageCount: Int
        let cover: Data?
        let fingerprint: String
        var metadata: ComicMetadataRecord? = nil
    }

    enum LinkError: LocalizedError {
        case differentFile
        var errorDescription: String? {
            String(localized: "This is a different file. Locate the original comic to keep this link.")
        }
    }

    nonisolated static let maxCoverBytes = 192 * 1024
    static var supportedTypes: [UTType] {
        [UTType(exportedAs: "com.chakra.comicreader.cbz", conformingTo: .zip), .pdf]
    }

    @Published private(set) var comics: [LinkedComic]
    @Published private(set) var coverRevisions: [UUID: UInt] = [:]
    private var refreshingCovers: Set<UUID> = []
    private var upgradedCovers: Set<UUID> = []
    private let directory: URL
    private let cacheDirectory: URL
    private let prepareLink: @Sendable (URL) throws -> Prepared
    private let readError: Error?

    init(directory: URL? = nil, cacheDirectory: URL? = nil,
         prepare: @escaping @Sendable (URL) throws -> Prepared = { try LinkedFilesStore.prepare($0) }) {
        let fm = FileManager.default
        let storage = directory ?? fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LinkedFiles", isDirectory: true)
        self.directory = storage
        self.cacheDirectory = cacheDirectory ?? storage.appendingPathComponent("Covers", isDirectory: true)
        self.prepareLink = prepare
        let index = self.directory.appendingPathComponent("linked-files.json")
        do {
            comics = try JSONDecoder().decode([LinkedComic].self, from: Data(contentsOf: index))
            readError = nil
        } catch {
            comics = []
            // Only a genuinely absent index may be replaced. Preserve unreadable/corrupt data.
            readError = (error as? CocoaError)?.code == .fileReadNoSuchFile ? nil : error
        }
    }

    @discardableResult
    func link(_ url: URL, replacing id: UUID? = nil) async throws -> LinkedComic {
        if let readError { throw readError }
        let original = id.flatMap { id in comics.first { $0.id == id } }
        guard id == nil || original != nil else { throw CocoaError(.fileReadNoSuchFile) }
        let prepare = prepareLink
        let payload = try await Task.detached(priority: .userInitiated) { try prepare(url) }.value
        if let original {
            guard comics.contains(original) else { throw CocoaError(.fileReadNoSuchFile) }
            guard original.fingerprint == payload.fingerprint else { throw LinkError.differentFile }
        }
        let comic = LinkedComic(id: id ?? UUID(), bookmark: payload.bookmark,
                                title: payload.title, pageCount: payload.pageCount, fingerprint: payload.fingerprint,
                                metadata: original?.shelfMetadata ?? payload.metadata)
        if let index = comics.firstIndex(where: { $0.id == comic.id }) {
            var updated = comics
            updated[index] = comic
            try save(updated)
        } else {
            try save([comic] + comics)
        }
        if let data = payload.cover { _ = saveCover(data, for: comic) }
        return comic
    }

    func remove(_ comic: LinkedComic) throws {
        guard comics.contains(where: { $0.id == comic.id }) else { return }
        try save(comics.filter { $0.id != comic.id })
        try? FileManager.default.removeItem(at: coverURL(comic))
        coverRevisions.removeValue(forKey: comic.id)
        upgradedCovers.remove(comic.id)
        for list in ReadingListStore.all() where list.itemFilenames.contains(comic.listToken) {
            ReadingListStore.removeComic(from: list.id, filename: comic.listToken)
        }
        ReadingProgress.clear(comic.progressURL)
        ReadingProgress.flushSync()
    }

    func comic(for url: URL) -> LinkedComic? {
        guard url.scheme == "leaf-files" else { return nil }
        return comics.first { $0.progressURL == url }
    }

    func setSeries(urls: [URL], name: String?, volume: String?) throws {
        let ids = Set(urls)
        var updated = comics
        for i in updated.indices where ids.contains(updated[i].progressURL) {
            var metadata = updated[i].shelfMetadata
            let series = name?.trimmingCharacters(in: .whitespacesAndNewlines)
            let volume = volume?.trimmingCharacters(in: .whitespacesAndNewlines)
            metadata.series = series == "" ? nil : series
            metadata.volume = volume == "" ? nil : volume
            metadata.userModified = true
            updated[i].metadata = metadata
        }
        if updated != comics { try save(updated) }
    }

    func markRead(_ comic: LinkedComic, read: Bool) {
        let url = comic.progressURL
        ReadingProgress.set(url, page: read ? max(1, comic.pageCount - 1) : 0, step: -1,
                            total: comic.pageCount, rtl: ReadingProgress.readingDirection(url), completed: read)
        ReadingProgress.flushSync()
        objectWillChange.send()
    }

    func cover(for comic: LinkedComic) -> UIImage? {
        UIImage(contentsOfFile: coverURL(comic).path)
    }

    /// Called after an explicitly opened linked page is visible, never while drawing the shelf.
    func refreshCoverIfNeeded(for comic: LinkedComic) {
        guard comics.contains(comic), !refreshingCovers.contains(comic.id),
              !upgradedCovers.contains(comic.id) else { return }
        if let image = cover(for: comic), max(image.size.width, image.size.height) >= 600 { return }
        refreshingCovers.insert(comic.id)
        Task {
            let jpeg = await Task.detached(priority: .utility) { () -> Data? in
                guard let url = try? comic.resolveURL(),
                      let archive = try? CbzArchive(url: url, readInPlace: true, reopeningBookmark: true) else { return nil }
                return try? archive.linkedCover(maxBytes: Self.maxCoverBytes)
            }.value
            refreshingCovers.remove(comic.id)
            guard comics.contains(comic), let jpeg, saveCover(jpeg, for: comic) else { return }
            upgradedCovers.insert(comic.id)
        }
    }

    @discardableResult
    private func saveCover(_ data: Data, for comic: LinkedComic) -> Bool {
        guard data.count <= Self.maxCoverBytes else { return false }
        do {
            try FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
            try data.write(to: coverURL(comic), options: .atomic)
            coverRevisions[comic.id, default: 0] &+= 1
            return true
        } catch {
            // A failed refresh leaves any previous valid file intact.
            return false
        }
    }

    private func coverURL(_ comic: LinkedComic) -> URL {
        cacheDirectory.appendingPathComponent(comic.id.uuidString + ".jpg")
    }

    private func save(_ entries: [LinkedComic]) throws {
        // These permission tokens are device-local and cannot be restored by Leaf backups.
        var dir = directory
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try dir.setResourceValues(values)
        try JSONEncoder().encode(entries).write(to: dir.appendingPathComponent("linked-files.json"),
                                                 options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        comics = entries
    }

    nonisolated static func prepare(_ url: URL) throws -> Prepared {
        let archive = try CbzArchive(url: url, readInPlace: true)
        guard archive.pageCount > 0 else { throw CocoaError(.fileReadCorruptFile) }
        // The archive holds the picker-granted scope while Foundation captures the bookmark.
        let data = try withExtendedLifetime(archive) {
            try url.bookmarkData(options: [.minimalBookmark], includingResourceValuesForKeys: nil, relativeTo: nil)
        }
        let fingerprint = try archive.linkedFingerprint()
        return Prepared(bookmark: data, title: url.deletingPathExtension().lastPathComponent,
                        pageCount: archive.pageCount, cover: try? archive.linkedCover(maxBytes: Self.maxCoverBytes), fingerprint: fingerprint,
                        metadata: ComicMetadataStore.parseMetadata(filename: url.lastPathComponent, xml: try? archive.linkedComicInfo()))
    }
}
