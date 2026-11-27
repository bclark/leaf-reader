import Foundation
import LeafShared

/// Continue is independent of the grid's view, search query and Unread filter.
/// Transport and file readability are checked by the caller; unknown server progress stays unknown.
struct LibraryContinueItem: Identifiable {
    struct Local {
        let url: URL
        let progress: Progress
        let openedAt: Double
    }

    let id: String
    let title: String
    let url: URL?
    let book: JellyfinBook?
    var linked: LinkedComic? = nil
    let page: Int
    let total: Int?
    let openedAt: Double

    var fraction: Double? { total.map { min(max(Double(page + 1) / Double($0), 0), 1) } }

    static func ranked(local: [Local], remote: [JellyfinBook], downloadedIDs: Set<String>,
                       remoteAvailable: Bool, linked: [LinkedComic] = []) -> [Self] {
        var result = local.compactMap { entry -> Self? in
            let progress = entry.progress
            guard progress.total > 0, progress.page >= 0, progress.page < progress.total,
                  !progress.isCompleted else { return nil }
            return Self(id: "local:" + entry.url.lastPathComponent,
                title: ComicMetadataStore.displayFilename(for: entry.url), url: entry.url, book: nil,
                page: progress.page, total: progress.total, openedAt: entry.openedAt)
        }
        result += linked.compactMap { comic -> Self? in
            let key = comic.progressURL
            guard let progress = ReadingProgress.get(key), progress.total > 0, progress.page >= 0,
                  progress.page < progress.total, !progress.isCompleted else { return nil }
            return Self(id: comic.listToken, title: comic.title, url: nil, book: nil, linked: comic,
                        page: progress.page, total: progress.total, openedAt: ReadingProgress.openedAt(key))
        }
        if remoteAvailable {
            var seen = downloadedIDs
            for book in remote {
                guard seen.insert(book.id).inserted, book.supported, JellyfinConnection.validID(book.id),
                      let progress = book.userData, progress.Played == false,
                      let ticks = progress.PlaybackPositionTicks, ticks > 0 else { continue }
                // Played is the explicit read/unread intent, independent of page/percentage.
                // Continue opens details for server items and never writes inferred completion.
                let date = progress.LastPlayedDate.flatMap { value -> Date? in
                    let formatter = ISO8601DateFormatter()
                    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                    return formatter.date(from: value) ?? ISO8601DateFormatter().date(from: value)
                }
                result.append(Self(id: "remote:" + book.id, title: book.name, url: nil, book: book,
                    page: Int(JellyfinProgress.shared.ticksToPage(ticks: ticks, maxPages: nil)), total: nil,
                    openedAt: date?.timeIntervalSince1970 ?? 0))
            }
        }
        return result.sorted { $0.openedAt == $1.openedAt ? $0.id < $1.id : $0.openedAt > $1.openedAt }
    }

}
