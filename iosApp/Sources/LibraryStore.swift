import Foundation
import UniformTypeIdentifiers
import LeafShared

/// Comics live as plain .cbz/.pdf files in Documents/Comics. Importing a CBZ/ZIP copies it in;
/// importing a CBR/RAR converts it to CBZ first (see CbrConverter). Metadata and reading lists
/// are synchronized reactively with the local file store.
@MainActor
final class LibraryStore: ObservableObject {
    @Published private(set) var comics: [URL] = []
    @Published private(set) var metadata: [String: ComicMetadataRecord] = [:]
    @Published private(set) var readingLists: [ReadingListRecord] = []
    @Published var importing = false
    @Published var importError: String?

    private let dir: URL
    private let importPipeline: ComicImportPipeline
    private var pendingImports = 0

    /// Types the file importer allows. Includes the app's exported CBZ/CBR types plus the generic
    /// zip/archive/rar/pdf families so a plain .zip, .rar, or .pdf comic is selectable too.
    static var importableTypes: [UTType] {
        var types: [UTType] = [.zip, .archive, .pdf]
        if let cbz = UTType("com.chakra.comicreader.cbz") { types.append(cbz) }
        if let cbr = UTType("com.chakra.comicreader.cbr") { types.append(cbr) }
        if let rar = UTType("com.rarlab.rar-archive") { types.append(rar) }
        return types
    }

    init(directory: URL? = nil) {
        if let directory {
            dir = directory
        } else {
            dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("Comics", isDirectory: true)
        }
        importPipeline = ComicImportPipeline(directory: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        refresh()
    }

    func refresh(backfill: Bool = true) {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        let comicFiles = files
            .filter { ["cbz", "zip", "pdf"].contains($0.pathExtension.lowercased()) }
            // Most-recently-opened first (Android's `ORDER BY lastOpened DESC`), then natural filename.
            .sorted {
                let ta = ReadingProgress.openedAt($0), tb = ReadingProgress.openedAt($1)
                if ta != tb { return ta > tb }
                return $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending
            }
        comics = comicFiles

        // Backfill missing series/issue metadata for existing or newly discovered files
        if backfill { ComicMetadataStore.backfillMetadataIfNeeded(for: comicFiles) }
        metadata = ComicMetadataStore.all()
        readingLists = ReadingListStore.all()
    }

    /// File-picker entry point; the async operation holds security scope through copying.
    func importComic(from url: URL) {
        Task {
            do {
                _ = try await importComicAwaitingCompletion(from: url, collision: .replace)
            } catch is CancellationError {
                // Cancellation is not a failed comic.
            } catch {
                importError = String(localized: "Couldn't import \(url.lastPathComponent): \(error.localizedDescription)")
            }
        }
    }

    /// Returns only after preparation, atomic publication and metadata bookkeeping finish.
    /// Remote callers should explicitly choose a collision policy; Keep Both is the safe default.
    func importComicAwaitingCompletion(
        from url: URL, originalName: String? = nil, expectedBytes: Int64? = nil,
        collision: ComicImportCollision = .keepBoth,
        phase: @escaping @Sendable (ComicImportPhase) -> Void = { _ in }
    ) async throws -> URL {
        pendingImports += 1
        importing = true
        defer {
            pendingImports -= 1
            importing = pendingImports > 0
        }
        let result = try await importPipeline.importFile(from: url,
            originalName: originalName ?? url.lastPathComponent, expectedBytes: expectedBytes,
            collision: collision, phase: phase)
        // Do not check cancellation after publication: finish this transaction consistently.
        ReadingProgress.markOpened(result.url)
        if ComicMetadataStore.get(result.url) == nil {
            ComicMetadataStore.set(result.url, metadata: result.metadata)
        }
        refresh(backfill: false)
        return result.url
    }

    func delete(_ url: URL) {
        try? FileManager.default.removeItem(at: url)
        ReadingProgress.clear(url)
        ComicMetadataStore.clear(url)
        for list in readingLists {
            ReadingListStore.removeComic(from: list.id, filename: url.lastPathComponent)
        }
        refresh()
    }

    func deleteBatch(_ urls: Set<URL>) {
        for url in urls {
            try? FileManager.default.removeItem(at: url)
            ReadingProgress.clear(url)
            ComicMetadataStore.clear(url)
            for list in readingLists {
                ReadingListStore.removeComic(from: list.id, filename: url.lastPathComponent)
            }
        }
        refresh()
    }

    // MARK: - Metadata & Series Operations

    func setSeries(urls: [URL], series: String?, volume: String?) {
        ComicMetadataStore.updateSeries(for: urls, series: series, volume: volume)
        refresh()
    }

    func markComicsRead(urls: [URL], read: Bool) {
        for url in urls {
            let current = ReadingProgress.get(url)
            let resolvedTotal: Int = {
                if let t = current?.total, t > 1 { return t }
                if let archive = try? CbzArchive(url: url), archive.pageCount > 0 {
                    return archive.pageCount
                }
                return max(current?.total ?? 2, 2)
            }()
            let dir = ReadingProgress.readingDirection(url)
            let targetPage = read ? max(1, resolvedTotal - 1) : 0
            ReadingProgress.set(url, page: targetPage, step: -1, total: resolvedTotal, rtl: dir)
        }
        ReadingProgress.flushSync()
        refresh()
    }

    func markSeriesRead(seriesName: String, volume: String?, read: Bool) {
        markComicsRead(urls: comics(inSeries: seriesName), read: read)
    }

    func comics(inSeries name: String) -> [URL] {
        sortIssues(comics.filter {
            metadata[$0.lastPathComponent]?.series?.caseInsensitiveCompare(name) == .orderedSame
        })
    }

    func addToSeries(urls: [URL], name: String) {
        for url in urls {
            ComicMetadataStore.updateSeries(for: [url], series: name,
                                            volume: metadata[url.lastPathComponent]?.volume)
        }
        refresh()
    }

    // MARK: - Reading List Operations

    @discardableResult
    func createReadingList(name: String, urls: [URL] = []) -> ReadingListRecord {
        let list = ReadingListStore.createList(name: name, itemFilenames: urls.map { $0.lastPathComponent })
        refresh()
        return list
    }

    func renameReadingList(id: String, newName: String) {
        ReadingListStore.renameList(id: id, newName: newName)
        refresh()
    }

    func deleteReadingList(id: String) {
        ReadingListStore.deleteList(id: id)
        refresh()
    }

    func addComicsToReadingList(listId: String, urls: [URL]) {
        ReadingListStore.addComics(to: listId, filenames: urls.map { $0.lastPathComponent })
        refresh()
    }

    func removeComicFromReadingList(listId: String, url: URL) {
        ReadingListStore.removeComic(from: listId, filename: url.lastPathComponent)
        refresh()
    }

    func moveComicInReadingList(listId: String, url: URL, direction: Int) {
        ReadingListStore.moveComic(in: listId, filename: url.lastPathComponent, direction: direction)
        refresh()
    }

    func markReadingListRead(listId: String, read: Bool) {
        guard let list = readingLists.first(where: { $0.id == listId }) else { return }
        let comicMap = Dictionary(uniqueKeysWithValues: comics.map { ($0.lastPathComponent, $0) })
        let listUrls = list.itemFilenames.compactMap { comicMap[$0] }
        markComicsRead(urls: listUrls, read: read)
    }

    // MARK: - Item Resolution for Library UI

    func libraryItems(filter: LibraryFilter, searchQuery: String) -> [LibraryItem] {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let comicMap = Dictionary(uniqueKeysWithValues: comics.map { ($0.lastPathComponent, $0) })

        let filteredComics: [URL] = {
            if q.isEmpty { return comics }
            return comics.filter { url in
                let filename = url.lastPathComponent.lowercased()
                let title = url.deletingPathExtension().lastPathComponent.lowercased()
                let series = metadata[url.lastPathComponent]?.series?.lowercased() ?? ""
                return filename.contains(q) || title.contains(q) || series.contains(q)
            }
        }()

        let listItems: [LibraryItem] = {
            let filteredLists = q.isEmpty ? readingLists : readingLists.filter { $0.name.lowercased().contains(q) }
            return filteredLists.map { list in
                let previewUrls = list.itemFilenames.compactMap { comicMap[$0] }
                return LibraryItem.readingListItem(list: list, previewUrls: previewUrls, itemCount: previewUrls.count)
            }
        }()

        switch filter {
        case .all:
            var seriesGroups: [String: [URL]] = [:]
            for url in filteredComics {
                if let s = metadata[url.lastPathComponent]?.series, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    let key = s.lowercased()
                    seriesGroups[key, default: []].append(url)
                }
            }

            var handled = Set<URL>()
            var items: [LibraryItem] = []

            for url in filteredComics {
                if handled.contains(url) { continue }
                if let s = metadata[url.lastPathComponent]?.series, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    let key = s.lowercased()
                    if let group = seriesGroups[key], group.count >= 2 {
                        let sortedIssues = sortIssues(group)
                        let nextIssue = sortedIssues.first { !ReadingProgress.isCompleted($0) }
                            ?? sortedIssues.first ?? url

                        let completedCount = sortedIssues.count { ReadingProgress.isCompleted($0) }
                        let unreadCount = sortedIssues.count { !ReadingProgress.isCompleted($0) }

                        items.append(
                            .seriesStack(
                                seriesName: metadata[group[0].lastPathComponent]?.series ?? String(localized: "Series"),
                                volume: nil,
                                issues: sortedIssues,
                                topCoverComic: nextIssue,
                                totalIssues: sortedIssues.count,
                                unreadIssues: unreadCount,
                                progressFraction: sortedIssues.isEmpty ? 0 : Double(completedCount) / Double(sortedIssues.count)
                            )
                        )
                        for u in group { handled.insert(u) }
                        continue
                    }
                }
                items.append(.singleComic(url: url, metadata: metadata[url.lastPathComponent]))
                handled.insert(url)
            }
            return items + listItems

        case .series:
            var seriesGroups: [String: [URL]] = [:]
            for url in filteredComics {
                if let s = metadata[url.lastPathComponent]?.series, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    let key = s.lowercased()
                    seriesGroups[key, default: []].append(url)
                }
            }

            return seriesGroups.values.map { group in
                let sortedIssues = sortIssues(group)
                let nextIssue = sortedIssues.first { !ReadingProgress.isCompleted($0) }
                    ?? sortedIssues.first ?? group[0]

                let completedCount = sortedIssues.count { ReadingProgress.isCompleted($0) }
                let unreadCount = sortedIssues.count { !ReadingProgress.isCompleted($0) }

                return LibraryItem.seriesStack(
                    seriesName: metadata[group[0].lastPathComponent]?.series ?? String(localized: "Series"),
                    volume: nil,
                    issues: sortedIssues,
                    topCoverComic: nextIssue,
                    totalIssues: sortedIssues.count,
                    unreadIssues: unreadCount,
                    progressFraction: sortedIssues.isEmpty ? 0 : Double(completedCount) / Double(sortedIssues.count)
                )
            }.sorted {
                if case .seriesStack(let s1, _, _, _, _, _, _) = $0,
                   case .seriesStack(let s2, _, _, _, _, _, _) = $1 {
                    return s1.localizedStandardCompare(s2) == .orderedAscending
                }
                return false
            }

        case .readingLists:
            return listItems

        case .unread:
            return filteredComics.filter { url in
                !ReadingProgress.isCompleted(url)
            }.map { url in
                LibraryItem.singleComic(url: url, metadata: metadata[url.lastPathComponent])
            }
        }
    }

    private func sortIssues(_ urls: [URL]) -> [URL] {
        urls.sorted { a, b in
            let ma = metadata[a.lastPathComponent]
            let mb = metadata[b.lastPathComponent]
            let volumeA = ma?.volume ?? ""
            let volumeB = mb?.volume ?? ""
            let volumeOrder = volumeA.localizedStandardCompare(volumeB)
            if volumeOrder != .orderedSame { return volumeOrder == .orderedAscending }
            let numA = ma?.issueNumber ?? Float.greatestFiniteMagnitude
            let numB = mb?.issueNumber ?? Float.greatestFiniteMagnitude
            if numA != numB { return numA < numB }
            return a.lastPathComponent.localizedStandardCompare(b.lastPathComponent) == .orderedAscending
        }
    }

}
