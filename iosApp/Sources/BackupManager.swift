import Foundation
import LeafShared

/// Manages versioned, cross-platform serialization and restoration of Leaf library data.
/// Guarantees 100% interoperability with Android's BackupManager JSON format.
enum BackupManager {
    static let currentVersion = 1

    // MARK: - Export

    static func exportToJson(
        comics: [URL],
        metadata: [String: ComicMetadataRecord],
        readingLists: [ReadingListRecord],
        settings: LeafSettings,
        appVersion: String = "0.3.4"
    ) -> String {
        let backupSettings = BackupSettings(
            defaultRightToLeft: ReadingPrefs.defaultRightToLeft,
            amoledTheme: settings.amoled,
            pageSlideEnabled: settings.pageSlideEnabled,
            pageFitMode: settings.pageFitMode == .fitWidth ? "FIT_WIDTH" : "FIT_PAGE",
            portraitSpread: settings.portraitSpread,
            landscapeSpread: settings.landscapeSpread,
            libraryColumns: settings.libraryColumns
        )

        var backupComics: [BackupComic] = []
        for url in comics {
            let filename = url.lastPathComponent
            let meta = metadata[filename]
            let title = meta?.title ?? url.deletingPathExtension().lastPathComponent
            let progress = ReadingProgress.get(url)
            let pageCount = progress?.total ?? 0
            let rtl = progress?.rtl ?? ReadingPrefs.defaultRightToLeft
            let lastPage = progress?.page ?? 0
            let lastSlot = progress?.step ?? 0
            let openedSec = ReadingProgress.openedAt(url)
            let lastOpened = Int64(openedSec * 1000)

            let fileAttrs = try? FileManager.default.attributesOfItem(atPath: url.path)
            let creationDate = fileAttrs?[.creationDate] as? Date ?? Date()
            let dateAdded = Int64(creationDate.timeIntervalSince1970 * 1000)

            backupComics.append(
                BackupComic(
                    title: title,
                    series: meta?.series,
                    volume: meta?.volume,
                    issueNumber: meta?.issueNumber != nil ? Double(meta!.issueNumber!) : nil,
                    format: url.pathExtension.lowercased(),
                    pageCount: pageCount,
                    rightToLeft: rtl,
                    lastPage: lastPage,
                    lastSlot: lastSlot,
                    dateAdded: dateAdded,
                    lastOpened: lastOpened
                )
            )
        }

        var backupLists: [BackupReadingList] = []
        for list in readingLists {
            var items: [BackupReadingListItem] = []
            for (idx, filename) in list.itemFilenames.enumerated() {
                let meta = metadata[filename]
                let comicUrl = comics.first { $0.lastPathComponent == filename }
                let title = meta?.title ?? (comicUrl?.deletingPathExtension().lastPathComponent ?? filename)
                items.append(
                    BackupReadingListItem(
                        comicTitle: title,
                        series: meta?.series,
                        issueNumber: meta?.issueNumber != nil ? Double(meta!.issueNumber!) : nil,
                        sortOrder: idx
                    )
                )
            }
            backupLists.append(
                BackupReadingList(
                    name: list.name,
                    createdAt: Int64(list.createdAt * 1000),
                    items: items
                )
            )
        }

        let payload = BackupPayload(
            version: currentVersion,
            exportedAt: Int64(Date().timeIntervalSince1970 * 1000),
            appVersion: appVersion,
            settings: backupSettings,
            comics: backupComics,
            readingLists: backupLists
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(payload),
           let str = String(data: data, encoding: .utf8) {
            return str
        }
        return "{}"
    }

    // MARK: - Validation & Preview

    static func parseAndValidate(jsonString: String) throws -> BackupPayload {
        guard let data = jsonString.data(using: .utf8) else {
            throw BackupError.invalidData("Could not decode string as UTF-8.")
        }
        guard let jsonObject = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw BackupError.invalidData("File does not contain valid JSON.")
        }
        guard let version = jsonObject["version"] as? Int else {
            throw BackupError.missingVersion
        }
        guard version == currentVersion else {
            throw BackupError.unsupportedVersion(version, expected: currentVersion)
        }

        let decoder = JSONDecoder()
        do {
            return try decoder.decode(BackupPayload.self, from: data)
        } catch {
            throw BackupError.invalidData("Failed to parse backup payload: \(error.localizedDescription)")
        }
    }

    static func findMatchingComic(
        backup: BackupComic,
        localComics: [URL],
        metadata: [String: ComicMetadataRecord]
    ) -> URL? {
        // 1. Exact match on title and pageCount
        if backup.pageCount > 0 {
            for url in localComics {
                let name = url.lastPathComponent
                let meta = metadata[name]
                let title = meta?.title ?? url.deletingPathExtension().lastPathComponent
                let progress = ReadingProgress.get(url)
                if title.caseInsensitiveCompare(backup.title) == .orderedSame && progress?.total == backup.pageCount {
                    return url
                }
            }
        }

        // 2. Exact match on title or filename without extension
        for url in localComics {
            let name = url.lastPathComponent
            let meta = metadata[name]
            let title = meta?.title ?? url.deletingPathExtension().lastPathComponent
            let baseName = url.deletingPathExtension().lastPathComponent
            if title.caseInsensitiveCompare(backup.title) == .orderedSame ||
               baseName.caseInsensitiveCompare(backup.title) == .orderedSame ||
               name.caseInsensitiveCompare(backup.title) == .orderedSame {
                return url
            }
        }

        // 3. Match on series + volume + issueNumber
        if let bSeries = backup.series?.trimmingCharacters(in: .whitespacesAndNewlines), !bSeries.isEmpty,
           let bIssue = backup.issueNumber {
            for url in localComics {
                let name = url.lastPathComponent
                guard let meta = metadata[name],
                      let mSeries = meta.series,
                      mSeries.caseInsensitiveCompare(bSeries) == .orderedSame else { continue }

                if let bVol = backup.volume?.trimmingCharacters(in: .whitespacesAndNewlines), !bVol.isEmpty {
                    guard let mVol = meta.volume, mVol.caseInsensitiveCompare(bVol) == .orderedSame else { continue }
                }

                if let mIssue = meta.issueNumber {
                    if abs(Double(mIssue) - bIssue) < 0.001 {
                        return url
                    }
                }
            }
        }

        return nil
    }

    static func createPreview(
        payload: BackupPayload,
        localComics: [URL],
        metadata: [String: ComicMetadataRecord]
    ) -> BackupPreview {
        var matching = 0
        for bComic in payload.comics {
            if findMatchingComic(backup: bComic, localComics: localComics, metadata: metadata) != nil {
                matching += 1
            }
        }

        return BackupPreview(
            version: payload.version,
            exportedAt: payload.exportedAt,
            appVersion: payload.appVersion,
            totalComicsInBackup: payload.comics.sizeOrCount,
            matchingComicsCount: matching,
            readingListsCount: payload.readingLists.count,
            hasSettings: payload.settings != nil
        )
    }

    // MARK: - Restore

    @discardableResult
    static func applyBackup(
        payload: BackupPayload,
        strategy: BackupConflictStrategy,
        localComics: [URL],
        metadata: inout [String: ComicMetadataRecord],
        settings: LeafSettings
    ) -> (restoredComics: Int, restoredLists: Int) {
        var restoredComicsCount = 0

        // 1. Restore Comics Progress and Metadata
        for bComic in payload.comics {
            guard let localUrl = findMatchingComic(backup: bComic, localComics: localComics, metadata: metadata) else {
                continue
            }
            let filename = localUrl.lastPathComponent
            let localOpenedMs = Int64(ReadingProgress.openedAt(localUrl) * 1000)
            let hasLocalProgress = ReadingProgress.get(localUrl) != nil

            let shouldApplyProgress: Bool
            switch strategy {
            case .overwrite:
                shouldApplyProgress = true
            case .mostRecent:
                // Apply if backup has newer activity, or local has never been opened
                shouldApplyProgress = !hasLocalProgress || bComic.lastOpened >= localOpenedMs
            case .keepLocal:
                // Only populate if local had no progress whatsoever
                shouldApplyProgress = !hasLocalProgress
            }

            if shouldApplyProgress {
                let actualTotal = ReadingProgress.get(localUrl)?.total ?? bComic.pageCount
                ReadingProgress.set(
                    localUrl,
                    page: bComic.lastPage,
                    step: bComic.lastSlot,
                    total: max(actualTotal, bComic.pageCount),
                    rtl: bComic.rightToLeft
                )
                if bComic.lastOpened > 0 {
                    ReadingProgress.setOpenedAt(localUrl, timestamp: Double(bComic.lastOpened) / 1000.0)
                }
                restoredComicsCount += 1
            }

            // Also backfill metadata (series, volume, issue number, title) if local was missing it
            var existingMeta = metadata[filename] ?? ComicMetadataRecord()
            var metaChanged = false
            if existingMeta.series == nil, let bSeries = bComic.series {
                existingMeta.series = bSeries
                metaChanged = true
            }
            if existingMeta.volume == nil, let bVol = bComic.volume {
                existingMeta.volume = bVol
                metaChanged = true
            }
            if existingMeta.issueNumber == nil, let bIssue = bComic.issueNumber {
                existingMeta.issueNumber = Float(bIssue)
                metaChanged = true
            }
            if existingMeta.title == nil {
                existingMeta.title = bComic.title
                metaChanged = true
            }
            if metaChanged {
                metadata[filename] = existingMeta
                ComicMetadataStore.set(localUrl, metadata: existingMeta)
            }
        }

        // 2. Restore Reading Lists
        var restoredListsCount = 0
        let existingLists = ReadingListStore.all()
        for bList in payload.readingLists {
            // Find local matching files for each item
            var matchedFilenames: [String] = []
            for bItem in bList.items.sorted(by: { $0.sortOrder < $1.sortOrder }) {
                // Find comic in local library
                let dummyComic = BackupComic(
                    title: bItem.comicTitle,
                    series: bItem.series,
                    issueNumber: bItem.issueNumber
                )
                if let matchedUrl = findMatchingComic(backup: dummyComic, localComics: localComics, metadata: metadata) {
                    if !matchedFilenames.contains(matchedUrl.lastPathComponent) {
                        matchedFilenames.append(matchedUrl.lastPathComponent)
                    }
                }
            }

            if let existing = existingLists.first(where: { $0.name.caseInsensitiveCompare(bList.name) == .orderedSame }) {
                // Merge items into existing list
                ReadingListStore.addComics(to: existing.id, filenames: matchedFilenames)
            } else {
                // Create new list
                ReadingListStore.createList(name: bList.name, itemFilenames: matchedFilenames)
            }
            restoredListsCount += 1
        }

        // 3. Restore Preferences / Settings (if available)
        if let bSettings = payload.settings {
            settings.amoled = bSettings.amoledTheme
            settings.pageSlideEnabled = bSettings.pageSlideEnabled
            settings.pageFitMode = (bSettings.pageFitMode == "FIT_WIDTH") ? .fitWidth : .fitPage
            settings.portraitSpread = bSettings.portraitSpread
            settings.landscapeSpread = bSettings.landscapeSpread
            if bSettings.libraryColumns > 0 {
                settings.libraryColumns = bSettings.libraryColumns
            }
            ReadingPrefs.defaultRightToLeft = bSettings.defaultRightToLeft
        }

        ReadingProgress.flushSync()
        return (restoredComicsCount, restoredListsCount)
    }
}

enum BackupError: LocalizedError {
    case invalidData(String)
    case missingVersion
    case unsupportedVersion(Int, expected: Int)

    var errorDescription: String? {
        switch self {
        case .invalidData(let message):
            return message
        case .missingVersion:
            return "Invalid backup: missing version identifier."
        case .unsupportedVersion(let version, let expected):
            return "Unsupported backup version \(version). Expected version \(expected)."
        }
    }
}

private extension Array {
    var sizeOrCount: Int { count }
}
