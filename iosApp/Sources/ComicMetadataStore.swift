import Foundation
import LeafShared

/// Structured comic metadata (series, volume, issue number, year, title) matching Android's ComicEntity fields.
public struct ComicMetadataRecord: Codable, Equatable, Sendable {
    public var series: String?
    public var volume: String?
    public var issueNumber: Float?
    public var year: Int?
    public var title: String?
    public var userModified: Bool?

    public init(
        series: String? = nil,
        volume: String? = nil,
        issueNumber: Float? = nil,
        year: Int? = nil,
        title: String? = nil,
        userModified: Bool? = nil
    ) {
        self.series = series
        self.volume = volume
        self.issueNumber = issueNumber
        self.year = year
        self.title = title
        self.userModified = userModified
    }
}

/// Store for comic metadata on iOS. Automatically extracts metadata from ComicInfo.xml
/// (or filename heuristics via shared `ComicMetadataParser`), and allows manual series grouping.
public enum ComicMetadataStore {
    private static let key = "leaf.comicMetadata.v1"
    private static let defaults = UserDefaults.standard
    private static let lock = NSLock()
    private static var inMemoryMetadata: [String: ComicMetadataRecord]?

    /// Older Jellyfin imports used UUID filenames; keep their progress keys intact.
    static func displayFilename(for url: URL) -> String {
        let base = url.deletingPathExtension().lastPathComponent
        if base.hasPrefix("Jellyfin-"), UUID(uuidString: String(base.dropFirst(9))) != nil,
           let title = get(url)?.title?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty {
            return title
        }
        return base
    }

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

    public static func clear(_ url: URL) {
        lock.lock()
        var map = inMemoryMetadata ?? loadLocked()
        map.removeValue(forKey: url.lastPathComponent)
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
            existing.userModified = true
            map[url.lastPathComponent] = existing
        }
        inMemoryMetadata = map
        saveLocked(map)
        lock.unlock()
    }

    /// Backfills missing series/issue metadata for existing library comics using ComicInfo.xml
    /// and filename heuristics, without overwriting explicit user series edits.
    public static func backfillMetadataIfNeeded(for urls: [URL]) {
        lock.lock()
        var map = inMemoryMetadata ?? loadLocked()
        var dirty = false
        for url in urls {
            let filename = url.lastPathComponent
            if var existing = map[filename] {
                if existing.userModified == true {
                    if existing.issueNumber == nil {
                        let parsed = parseDefault(for: url)
                        if let issueNum = parsed.issueNumber {
                            existing.issueNumber = issueNum
                            map[filename] = existing
                            dirty = true
                        }
                    }
                    continue
                }
                if existing.series == nil || existing.issueNumber == nil {
                    let parsed = parseDefault(for: url)
                    let newSeries = existing.series ?? parsed.series
                    let newVolume = existing.volume ?? parsed.volume
                    let newIssue = existing.issueNumber ?? parsed.issueNumber
                    let newYear = existing.year ?? parsed.year
                    let newTitle = existing.title ?? parsed.title
                    if newSeries != existing.series || newVolume != existing.volume || newIssue != existing.issueNumber {
                        existing.series = newSeries
                        existing.volume = newVolume
                        existing.issueNumber = newIssue
                        existing.year = newYear
                        existing.title = newTitle
                        map[filename] = existing
                        dirty = true
                    }
                }
            } else {
                map[filename] = parseDefault(for: url)
                dirty = true
            }
        }
        if dirty {
            inMemoryMetadata = map
            saveLocked(map)
        }
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
        let xml = (try? CbzArchive(url: url))?.readComicInfoXml()
        return parseMetadata(filename: url.lastPathComponent, xml: xml)
    }

    static func parseMetadata(filename: String, xml: String? = nil) -> ComicMetadataRecord {
        let fileMeta = ComicMetadataParser.shared.parseFilename(filename: filename)
        let baseTitle = (filename as NSString).deletingPathExtension

        if let xml {
            let xmlMeta = ComicMetadataParser.shared.parseComicInfoXml(xml: xml)
            let series = xmlMeta.series ?? fileMeta.series
            let volume = xmlMeta.volume ?? fileMeta.volume
            let issueNum = xmlMeta.issueNumber?.floatValue ?? fileMeta.issueNumber?.floatValue
            let year = xmlMeta.year?.intValue ?? fileMeta.year?.intValue
            let title = xmlMeta.title ?? fileMeta.title ?? baseTitle
            return ComicMetadataRecord(
                series: series,
                volume: volume,
                issueNumber: issueNum,
                year: year,
                title: title
            )
        }

        // 2. Fall back to filename heuristic parsing via shared parser
        return ComicMetadataRecord(
            series: fileMeta.series,
            volume: fileMeta.volume,
            issueNumber: fileMeta.issueNumber?.floatValue,
            year: fileMeta.year?.intValue,
            title: fileMeta.title ?? baseTitle
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
