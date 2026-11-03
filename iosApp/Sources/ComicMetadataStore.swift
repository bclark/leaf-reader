import Foundation
import LeafShared

/// Structured comic metadata (series, volume, issue number, year, title) matching Android's ComicEntity fields.
public struct ComicMetadataRecord: Codable, Equatable {
    public var series: String?
    public var volume: String?
    public var issueNumber: Float?
    public var year: Int?
    public var title: String?

    public init(
        series: String? = nil,
        volume: String? = nil,
        issueNumber: Float? = nil,
        year: Int? = nil,
        title: String? = nil
    ) {
        self.series = series
        self.volume = volume
        self.issueNumber = issueNumber
        self.year = year
        self.title = title
    }
}

/// Store for comic metadata on iOS. Automatically extracts metadata from ComicInfo.xml
/// (or filename heuristics via shared `ComicMetadataParser`), and allows manual series grouping.
public enum ComicMetadataStore {
    private static let key = "leaf.comicMetadata.v1"
    private static let defaults = UserDefaults.standard
    private static let lock = NSLock()
    private static var inMemoryMetadata: [String: ComicMetadataRecord]?

    public static func get(_ url: URL) -> ComicMetadataRecord? {
        let filename = url.lastPathComponent
        lock.lock()
        defer { lock.unlock() }
        if let mem = inMemoryMetadata {
            return mem[filename]
        }
        let map = loadLocked()
        return map[filename]
    }

    public static func all() -> [String: ComicMetadataRecord] {
        lock.lock()
        defer { lock.unlock() }
        if let mem = inMemoryMetadata { return mem }
        return loadLocked()
    }

    public static func set(_ url: URL, metadata: ComicMetadataRecord) {
        lock.lock()
        var map = inMemoryMetadata ?? loadLocked()
        map[url.lastPathComponent] = metadata
        inMemoryMetadata = map
        saveLocked(map)
        lock.unlock()
    }

    public static func updateSeries(for urls: [URL], series: String?, volume: String?) {
        lock.lock()
        var map = inMemoryMetadata ?? loadLocked()
        let cleanSeries = series?.trimmingCharacters(in: .whitespacesAndNewlines).ifEmptyNil
        let cleanVolume = volume?.trimmingCharacters(in: .whitespacesAndNewlines).ifEmptyNil
        for url in urls {
            var existing = map[url.lastPathComponent] ?? parseDefault(for: url)
            existing.series = cleanSeries
            existing.volume = cleanVolume
            map[url.lastPathComponent] = existing
        }
        inMemoryMetadata = map
        saveLocked(map)
        lock.unlock()
    }

    public static func parseAndStoreIfNeeded(_ url: URL) -> ComicMetadataRecord {
        if let existing = get(url) {
            return existing
        }
        let parsed = parseDefault(for: url)
        set(url, metadata: parsed)
        return parsed
    }

    public static func parseDefault(for url: URL) -> ComicMetadataRecord {
        let filename = url.lastPathComponent
        // 1. Try reading ComicInfo.xml if CBZ
        if let archive = try? CbzArchive(url: url), let xml = archive.readComicInfoXml() {
            let sharedMeta = ComicMetadataParser.shared.parseComicInfoXml(xml: xml)
            let series = sharedMeta.series
            let volume = sharedMeta.volume
            let issueNum = sharedMeta.issueNumber?.floatValue
            let year = sharedMeta.year?.intValue
            let title = sharedMeta.title ?? url.deletingPathExtension().lastPathComponent
            if series != nil || issueNum != nil {
                return ComicMetadataRecord(
                    series: series,
                    volume: volume,
                    issueNumber: issueNum,
                    year: year,
                    title: title
                )
            }
        }

        // 2. Fall back to filename heuristic parsing via shared parser
        let sharedMeta = ComicMetadataParser.shared.parseFilename(filename: filename)
        return ComicMetadataRecord(
            series: sharedMeta.series,
            volume: sharedMeta.volume,
            issueNumber: sharedMeta.issueNumber?.floatValue,
            year: sharedMeta.year?.intValue,
            title: sharedMeta.title ?? url.deletingPathExtension().lastPathComponent
        )
    }

    private static func loadLocked() -> [String: ComicMetadataRecord] {
        if let data = defaults.data(forKey: key),
           let decoded = try? JSONDecoder().decode([String: ComicMetadataRecord].self, from: data) {
            inMemoryMetadata = decoded
            return decoded
        }
        inMemoryMetadata = [:]
        return [:]
    }

    private static func saveLocked(_ map: [String: ComicMetadataRecord]) {
        if let data = try? JSONEncoder().encode(map) {
            defaults.set(data, forKey: key)
        }
    }
}

private extension String {
    var ifEmptyNil: String? {
        isEmpty ? nil : self
    }
}
