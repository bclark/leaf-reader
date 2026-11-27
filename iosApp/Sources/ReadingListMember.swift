import Foundation

/// One ordered member, whether its archive is local, remote, or temporarily unavailable.
struct ReadingListMember: Identifiable {
    let id: String
    let url: URL?
    let book: JellyfinBook?
    let title: String
    var linked: LinkedComic? = nil

    var completed: Bool? {
        if let url = url ?? linked?.progressURL { return ReadingProgress.isCompleted(url) }
        return book?.userData?.Played
    }

    static func resolve(_ tokens: [String], local: [URL], metadata: [String: ComicMetadataRecord],
                        books: [JellyfinBook], connection: JellyfinConnection?, linked: [LinkedComic] = []) -> [Self] {
        let files = Dictionary(local.map { ($0.lastPathComponent, $0) }, uniquingKeysWith: { first, _ in first })
        let remote = Dictionary(books.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let links = Dictionary(linked.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return tokens.map { token in
            let comic = LinkedComic.listID(token).flatMap { links[$0] }
            let url = ReadingListStore.localFilename(for: token).flatMap { files[$0] }
            let reference = JellyfinListReference(token: token)
            let book = reference.flatMap { $0.belongs(to: connection) ? remote[$0.itemID] : nil }
            let title = url.map { metadata[$0.lastPathComponent]?.title ?? $0.deletingPathExtension().lastPathComponent }
                ?? comic?.title ?? book?.name ?? String(localized: "Unavailable comic")
            return Self(id: token, url: url, book: book, title: title, linked: comic)
        }
    }

    static func choices(local: [URL], metadata: [String: ComicMetadataRecord], books: [JellyfinBook],
                        connection: JellyfinConnection?, excluding: [String], query: String, linked: [LinkedComic] = []) -> [Self] {
        let tokens = local.map { ReadingListStore.memberKey(for: $0.lastPathComponent) } + linked.map(\.listToken)
            + (connection.map { connection in books.map { JellyfinListReference(connection: connection, itemID: $0.id).token } } ?? [])
        let existing = Set(excluding.map { ReadingListStore.memberKey(for: $0) })
        var seen = existing
        return resolve(tokens.filter { seen.insert($0).inserted }, local: local, metadata: metadata,
                       books: books, connection: connection, linked: linked).filter { member in
            LibraryBrowseState.matches(query, values: [member.title, member.book?.seriesName ?? "",
                member.linked?.shelfMetadata.series ?? "",
                member.url.flatMap { metadata[$0.lastPathComponent]?.series } ?? ""])
        }
    }
}
