import Foundation

/// Filter tabs for the library grid, matching Android's LibraryFilter enum.
public enum LibraryFilter: String, CaseIterable, Identifiable {
    case all = "ALL"
    case series = "SERIES"
    case readingLists = "LISTS"
    case unread = "UNREAD"

    public var id: String { rawValue }
    public var label: String { rawValue }
}

/// Sealed representation of items in the library grid: single comic, series stack, or reading list stack.
public enum LibraryItem: Identifiable, Equatable {
    case singleComic(url: URL, metadata: ComicMetadataRecord?)
    case seriesStack(
        seriesName: String,
        volume: String?,
        issues: [URL],
        topCoverComic: URL,
        totalIssues: Int,
        unreadIssues: Int,
        progressFraction: Double
    )
    case readingListItem(
        list: ReadingListRecord,
        previewUrls: [URL],
        itemCount: Int
    )

    public var id: String {
        switch self {
        case .singleComic(let url, _):
            return "single-\(url.lastPathComponent)"
        case .seriesStack(let seriesName, let volume, _, _, _, _, _):
            return "series-\(seriesName.lowercased())-\(volume?.lowercased() ?? "")"
        case .readingListItem(let list, _, _):
            return "list-\(list.id)"
        }
    }
}
