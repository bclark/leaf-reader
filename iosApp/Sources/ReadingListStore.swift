import Foundation

/// A custom user-curated reading list with an ordered list of comic filenames.
public struct ReadingListRecord: Identifiable, Codable, Equatable {
    public var id: String
    public var name: String
    public var itemFilenames: [String]
    public var createdAt: Double

    public init(
        id: String = UUID().uuidString,
        name: String,
        itemFilenames: [String] = [],
        createdAt: Double = Date().timeIntervalSince1970
    ) {
        self.id = id
        self.name = name
        self.itemFilenames = itemFilenames
        self.createdAt = createdAt
    }
}

/// Store for reading lists on iOS, matching Android's ReadingListDao functionality.
public enum ReadingListStore {
    private static let key = "leaf.readingLists.v1"
    private static let defaults = UserDefaults.standard
    private static let lock = NSLock()
    private static var inMemoryLists: [ReadingListRecord]?

    public static func all() -> [ReadingListRecord] {
        lock.lock()
        defer { lock.unlock() }
        if let mem = inMemoryLists { return mem }
        return loadLocked()
    }

    public static func get(id: String) -> ReadingListRecord? {
        all().first { $0.id == id }
    }

    @discardableResult
    public static func createList(name: String, itemFilenames: [String] = []) -> ReadingListRecord {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        lock.lock()
        let links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
        var seen = Set<String>()
        let members = itemFilenames.map { filename in links.first(where: { $0.value == filename })?.key ?? filename }
            .filter { seen.insert($0).inserted }
        let list = ReadingListRecord(name: trimmed.isEmpty ? String(localized: "Reading List") : trimmed, itemFilenames: members)
        var lists = inMemoryLists ?? loadLocked()
        lists.append(list)
        inMemoryLists = lists
        saveLocked(lists)
        lock.unlock()
        return list
    }

    public static func renameList(id: String, newName: String) {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        lock.lock()
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == id }) {
            lists[idx].name = trimmed
            inMemoryLists = lists
            saveLocked(lists)
        }
        lock.unlock()
    }

    public static func deleteList(id: String) {
        lock.lock()
        var lists = inMemoryLists ?? loadLocked()
        lists.removeAll { $0.id == id }
        inMemoryLists = lists
        saveLocked(lists)
        lock.unlock()
    }

    public static func addComics(to listId: String, filenames: [String]) {
        lock.lock()
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == listId }) {
            var existing = lists[idx].itemFilenames
            let links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
            for filename in filenames {
                let f = links.first(where: { $0.value == filename })?.key ?? filename
                if !existing.contains(f) {
                    existing.append(f)
                }
            }
            lists[idx].itemFilenames = existing
            inMemoryLists = lists
            saveLocked(lists)
        }
        lock.unlock()
    }

    /// Attach a downloaded copy without making a second list member or changing its position.
    static func linkRemote(_ reference: JellyfinListReference, filename: String) {
        lock.lock()
        defer { lock.unlock() }
        var links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
        if links[reference.token] != filename {
            links[reference.token] = filename
            defaults.set(links, forKey: "leaf.readingLists.remoteCopies.v1")
        }
        var lists = inMemoryLists ?? loadLocked()
        let original = lists
        for index in lists.indices {
            let values = lists[index].itemFilenames.map { $0 == filename ? reference.token : $0 }
            var seen = Set<String>()
            lists[index].itemFilenames = values.filter { seen.insert($0).inserted }
        }
        if lists != original {
            inMemoryLists = lists
            saveLocked(lists)
        }
    }

    /// Forget the removed copy while retaining the account-scoped list member.
    static func unlinkLocalCopy(filename: String) {
        lock.lock()
        defer { lock.unlock() }
        let links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
        defaults.set(links.filter { $0.value != filename }, forKey: "leaf.readingLists.remoteCopies.v1")
    }

    /// Use the same durable identity in the picker, store and list operations.
    static func memberKey(for filename: String) -> String {
        lock.lock()
        defer { lock.unlock() }
        let links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
        return links.first(where: { $0.value == filename })?.key ?? filename
    }

    static func localFilename(for member: String) -> String? {
        guard LinkedComic.listID(member) == nil else { return nil }
        guard JellyfinListReference(token: member) != nil else { return member }
        lock.lock()
        defer { lock.unlock() }
        return defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1")?[member] as? String
    }

    /// Unknown progress remains a candidate; never skip an unavailable member in list order.
    static func nextMember(in members: [String], completed: (String) -> Bool?) -> String? {
        members.first { completed($0) != true } ?? members.first
    }

    public static func removeComic(from listId: String, filename: String) {
        lock.lock()
        let links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
        let member = links.first(where: { $0.value == filename })?.key ?? filename
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == listId }) {
            lists[idx].itemFilenames.removeAll { $0 == member }
            inMemoryLists = lists
            saveLocked(lists)
        }
        lock.unlock()
    }

    /// Moves a comic up (`direction < 0`) or down (`direction > 0`) in the reading list's sequential order.
    public static func moveComic(in listId: String, filename: String, direction: Int) {
        guard direction != 0 else { return }
        lock.lock()
        let links = defaults.dictionary(forKey: "leaf.readingLists.remoteCopies.v1") as? [String: String] ?? [:]
        let member = links.first(where: { $0.value == filename })?.key ?? filename
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == listId }) {
            var items = lists[idx].itemFilenames
            if let itemIdx = items.firstIndex(of: member) {
                let targetIdx = itemIdx + (direction < 0 ? -1 : 1)
                if items.indices.contains(targetIdx) {
                    items.swapAt(itemIdx, targetIdx)
                    lists[idx].itemFilenames = items
                    inMemoryLists = lists
                    saveLocked(lists)
                }
            }
        }
        lock.unlock()
    }

    public static func reorderComics(in listId: String, filenames: [String]) {
        lock.lock()
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == listId }) {
            lists[idx].itemFilenames = filenames
            inMemoryLists = lists
            saveLocked(lists)
        }
        lock.unlock()
    }

    private static func loadLocked() -> [ReadingListRecord] {
        if let data = defaults.data(forKey: key),
           let decoded = try? JSONDecoder().decode([ReadingListRecord].self, from: data) {
            inMemoryLists = decoded
            return decoded
        }
        inMemoryLists = []
        return []
    }

    private static func saveLocked(_ lists: [ReadingListRecord]) {
        if let data = try? JSONEncoder().encode(lists) {
            defaults.set(data, forKey: key)
        }
    }
}
