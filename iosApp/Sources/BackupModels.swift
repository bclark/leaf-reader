import Foundation

/// Strategy for resolving conflicts when restoring reading progress from a backup.
enum BackupConflictStrategy: String, CaseIterable, Identifiable {
    case mostRecent = "MOST_RECENT"
    case overwrite = "OVERWRITE"
    case keepLocal = "KEEP_LOCAL"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .mostRecent:
            return String(localized: "Most Recent (Recommended)")
        case .overwrite:
            return String(localized: "Overwrite")
        case .keepLocal:
            return String(localized: "Keep Local")
        }
    }

    var subtitle: String {
        switch self {
        case .mostRecent:
            return String(localized: "Keeps the latest reading progress between backup and device.")
        case .overwrite:
            return String(localized: "Always replaces local progress with backup data.")
        case .keepLocal:
            return String(localized: "Retains local progress; only restores reading lists and preferences.")
        }
    }
}

struct BackupSettings: Codable, Equatable {
    var defaultRightToLeft: Bool
    var amoledTheme: Bool
    var pageSlideEnabled: Bool
    var pageFitMode: String
    var portraitSpread: Bool
    var landscapeSpread: Bool
    var libraryColumns: Int

    init(
        defaultRightToLeft: Bool = false,
        amoledTheme: Bool = false,
        pageSlideEnabled: Bool = true,
        pageFitMode: String = "FIT_PAGE",
        portraitSpread: Bool = false,
        landscapeSpread: Bool = false,
        libraryColumns: Int = 2
    ) {
        self.defaultRightToLeft = defaultRightToLeft
        self.amoledTheme = amoledTheme
        self.pageSlideEnabled = pageSlideEnabled
        self.pageFitMode = pageFitMode
        self.portraitSpread = portraitSpread
        self.landscapeSpread = landscapeSpread
        self.libraryColumns = libraryColumns
    }
}

struct BackupComic: Codable, Equatable {
    var title: String
    var series: String?
    var volume: String?
    var issueNumber: Double?
    var format: String
    var pageCount: Int
    var rightToLeft: Bool
    var lastPage: Int
    var lastSlot: Int
    var dateAdded: Int64
    var lastOpened: Int64

    init(
        title: String,
        series: String? = nil,
        volume: String? = nil,
        issueNumber: Double? = nil,
        format: String = "cbz",
        pageCount: Int = 0,
        rightToLeft: Bool = false,
        lastPage: Int = 0,
        lastSlot: Int = 0,
        dateAdded: Int64 = 0,
        lastOpened: Int64 = 0
    ) {
        self.title = title
        self.series = series
        self.volume = volume
        self.issueNumber = issueNumber
        self.format = format
        self.pageCount = pageCount
        self.rightToLeft = rightToLeft
        self.lastPage = lastPage
        self.lastSlot = lastSlot
        self.dateAdded = dateAdded
        self.lastOpened = lastOpened
    }
}

struct BackupReadingListItem: Codable, Equatable {
    var comicTitle: String
    var series: String?
    var issueNumber: Double?
    var sortOrder: Int

    init(
        comicTitle: String,
        series: String? = nil,
        issueNumber: Double? = nil,
        sortOrder: Int = 0
    ) {
        self.comicTitle = comicTitle
        self.series = series
        self.issueNumber = issueNumber
        self.sortOrder = sortOrder
    }
}

struct BackupReadingList: Codable, Equatable {
    var name: String
    var createdAt: Int64
    var items: [BackupReadingListItem]

    init(
        name: String,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        items: [BackupReadingListItem] = []
    ) {
        self.name = name
        self.createdAt = createdAt
        self.items = items
    }
}

struct BackupPayload: Codable, Equatable {
    var version: Int
    var exportedAt: Int64
    var appVersion: String
    var settings: BackupSettings?
    var comics: [BackupComic]
    var readingLists: [BackupReadingList]

    init(
        version: Int = 1,
        exportedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        appVersion: String = "0.3.4",
        settings: BackupSettings? = nil,
        comics: [BackupComic] = [],
        readingLists: [BackupReadingList] = []
    ) {
        self.version = version
        self.exportedAt = exportedAt
        self.appVersion = appVersion
        self.settings = settings
        self.comics = comics
        self.readingLists = readingLists
    }
}

struct BackupPreview: Equatable {
    var version: Int
    var exportedAt: Int64
    var appVersion: String
    var totalComicsInBackup: Int
    var matchingComicsCount: Int
    var readingListsCount: Int
    var hasSettings: Bool

    init(
        version: Int,
        exportedAt: Int64,
        appVersion: String,
        totalComicsInBackup: Int,
        matchingComicsCount: Int,
        readingListsCount: Int,
        hasSettings: Bool
    ) {
        self.version = version
        self.exportedAt = exportedAt
        self.appVersion = appVersion
        self.totalComicsInBackup = totalComicsInBackup
        self.matchingComicsCount = matchingComicsCount
        self.readingListsCount = readingListsCount
        self.hasSettings = hasSettings
    }
}
