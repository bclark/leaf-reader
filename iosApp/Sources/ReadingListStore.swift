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
        let list = ReadingListRecord(name: trimmed.isEmpty ? "Reading List" : trimmed, itemFilenames: itemFilenames)
        lock.lock()
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
            for f in filenames {
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

    public static func removeComic(from listId: String, filename: String) {
        lock.lock()
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == listId }) {
            lists[idx].itemFilenames.removeAll { $0 == filename }
            inMemoryLists = lists
            saveLocked(lists)
        }
        lock.unlock()
    }

    /// Moves a comic up (`direction < 0`) or down (`direction > 0`) in the reading list's sequential order.
    public static func moveComic(in listId: String, filename: String, direction: Int) {
        guard direction != 0 else { return }
        lock.lock()
        var lists = inMemoryLists ?? loadLocked()
        if let idx = lists.firstIndex(where: { $0.id == listId }) {
            var items = lists[idx].itemFilenames
            if let itemIdx = items.firstIndex(of: filename) {
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
