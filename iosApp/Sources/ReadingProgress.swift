import Foundation
import UIKit

/// Per-comic reading progress (resume point + total), persisted in UserDefaults keyed by the
/// comic's filename — the iOS counterpart to Android's Room-backed resume (page *and* panel).
struct Progress: Codable, Equatable {
    var page: Int
    var step: Int      // -1 = whole page, else panel index
    var total: Int     // page count, for the library progress readout
    // Reading direction for THIS comic. Optional so older saved data (which lacked the field)
    // still decodes; nil means "never chosen for this comic" → fall back to the global default.
    var rtl: Bool?

    var isCompleted: Bool {
        guard total > 0 else { return false }
        return total == 1 ? page >= 1 : page >= total - 1
    }

    var fraction: Double {
        if isCompleted { return 1.0 }
        guard total > 1 else { return 0 }
        return min(max(Double(page) / Double(total - 1), 0), 1)
    }

    var percent: Int { Int((fraction * 100).rounded()) }
}

/// App-wide reading preferences. The default reading direction is applied to any comic the user
/// hasn't explicitly toggled yet — the global half of "remembered per comic + a global default",
/// matching Android's behaviour.
enum ReadingPrefs {
    private static let directionKey = "leaf.readingDirection.default.rtl"
    private static let fillKey = "leaf.zoomFill"
    private static let legacyDirectionKey = "chika.readingDirection.default.rtl"
    private static let legacyFillKey = "chika.zoomFill"
    private static let defaults = UserDefaults.standard

    static var defaultRightToLeft: Bool {
        get {
            if defaults.object(forKey: directionKey) != nil {
                return defaults.bool(forKey: directionKey)
            }
            return defaults.bool(forKey: legacyDirectionKey)
        }
        set { defaults.set(newValue, forKey: directionKey) }
    }

    /// How tightly a framed panel fills the screen (1.0 = edge-to-edge, lower = more padding).
    /// 0.98 is the cross-platform default; the reader lets the user cycle this for taste.
    static let fillLevels: [Float] = [0.86, 0.92, 0.98, 1.0]
    static var zoomFill: Float {
        get {
            let v = defaults.float(forKey: fillKey)
            if v != 0 { return v }
            let legacyV = defaults.float(forKey: legacyFillKey)
            return legacyV == 0 ? 0.98 : legacyV
        }
        set { defaults.set(newValue, forKey: fillKey) }
    }
}

enum ReadingProgress {
    private static let key = "leaf.progress.v1"
    private static let openedKey = "leaf.lastOpened.v1"
    private static let legacyKey = "chika.progress.v1"
    private static let legacyOpenedKey = "chika.lastOpened.v1"
    private static let defaults = UserDefaults.standard

    private static let lock = NSLock()
    private static let ioQueue = DispatchQueue(label: "leaf.readingprogress.io", qos: .utility)
    private static var inMemoryProgress: [String: Progress]?
    private static var inMemoryOpened: [String: Double]?
    private static var saveWorkItem: DispatchWorkItem?

    static func initializeLifecycleObservers() {
        _ = lifecycleObserverToken
    }

    private static let lifecycleObserverToken: Void = {
        NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: nil
        ) { _ in
            flushSync()
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: nil
        ) { _ in
            flushSync()
        }
    }()

    private static func load() -> [String: Progress] {
        lock.lock(); defer { lock.unlock() }
        if let mem = inMemoryProgress { return mem }
        if let data = defaults.data(forKey: key),
           let map = try? JSONDecoder().decode([String: Progress].self, from: data) {
            inMemoryProgress = map
            return map
        }
        if let legacyData = defaults.data(forKey: legacyKey),
           let map = try? JSONDecoder().decode([String: Progress].self, from: legacyData) {
            inMemoryProgress = map
            return map
        }
        inMemoryProgress = [:]
        return [:]
    }

    /// Schedule an asynchronous serialized save, conflating rapid successive page turns.
    private static func scheduleSave() {
        lock.lock()
        let currentMap = inMemoryProgress ?? [:]
        saveWorkItem?.cancel()
        let item = DispatchWorkItem {
            if let data = try? JSONEncoder().encode(currentMap) {
                defaults.set(data, forKey: key)
            }
        }
        saveWorkItem = item
        lock.unlock()
        ioQueue.asyncAfter(deadline: .now() + 0.08, execute: item)
    }

    /// Force synchronous flush of any pending writes to disk on app exit or backgrounding.
    static func flushSync() {
        lock.lock()
        saveWorkItem?.cancel()
        saveWorkItem = nil
        let pMap = inMemoryProgress
        let oMap = inMemoryOpened
        lock.unlock()

        if let pMap, let data = try? JSONEncoder().encode(pMap) {
            defaults.set(data, forKey: key)
        }
        if let oMap, let data = try? JSONEncoder().encode(oMap) {
            defaults.set(data, forKey: openedKey)
        }
        defaults.synchronize()
    }

    static func get(_ url: URL) -> Progress? {
        load()[url.lastPathComponent]
    }

    static func isCompleted(_ url: URL) -> Bool {
        get(url)?.isCompleted ?? false
    }

    /// Effective reading direction for a comic: its saved choice if any, else the global default.
    static func readingDirection(_ url: URL) -> Bool {
        get(url)?.rtl ?? ReadingPrefs.defaultRightToLeft
    }

    /// Sets and prompts durable persistence for this comic's progress.
    static func set(_ url: URL, page: Int, step: Int, total: Int, rtl: Bool) {
        initializeLifecycleObservers()
        lock.lock()
        if inMemoryProgress == nil {
            _ = inMemoryProgressLocked()
        }
        inMemoryProgress?[url.lastPathComponent] = Progress(page: page, step: step, total: total, rtl: rtl)
        lock.unlock()
        scheduleSave()
    }

    /// Resynchronizes total page count and clamps progress if an archive changed.
    static func resyncTotalIfNeeded(_ url: URL, actualTotal: Int) {
        guard actualTotal > 0 else { return }
        lock.lock()
        if inMemoryProgress == nil { _ = inMemoryProgressLocked() }
        let filename = url.lastPathComponent
        if var current = inMemoryProgress?[filename] {
            if current.total != actualTotal || current.page >= actualTotal {
                let wasCompleted = current.isCompleted
                current.total = actualTotal
                if wasCompleted {
                    current.page = max(0, actualTotal - 1)
                } else {
                    current.page = min(max(current.page, 0), actualTotal - 1)
                }
                inMemoryProgress?[filename] = current
                lock.unlock()
                scheduleSave()
                return
            }
        }
        lock.unlock()
    }

    static func clear(_ url: URL) {
        lock.lock()
        if inMemoryProgress == nil { _ = inMemoryProgressLocked() }
        if inMemoryOpened == nil { _ = inMemoryOpenedLocked() }
        inMemoryProgress?.removeValue(forKey: url.lastPathComponent)
        inMemoryOpened?.removeValue(forKey: url.lastPathComponent)
        lock.unlock()
        flushSync()
    }

    // MARK: - Recency (most-recently-opened ordering, matching Android's `ORDER BY lastOpened DESC`)

    /// Records that a comic was just opened/imported, so the library can order by recency.
    static func markOpened(_ url: URL) {
        initializeLifecycleObservers()
        lock.lock()
        if inMemoryOpened == nil { _ = inMemoryOpenedLocked() }
        inMemoryOpened?[url.lastPathComponent] = Date().timeIntervalSince1970
        let currentMap = inMemoryOpened ?? [:]
        lock.unlock()
        ioQueue.async {
            if let data = try? JSONEncoder().encode(currentMap) {
                defaults.set(data, forKey: openedKey)
            }
        }
    }

    /// Sets explicit last-opened timestamp (e.g. when restoring from backup).
    static func setOpenedAt(_ url: URL, timestamp: Double) {
        initializeLifecycleObservers()
        lock.lock()
        if inMemoryOpened == nil { _ = inMemoryOpenedLocked() }
        inMemoryOpened?[url.lastPathComponent] = timestamp
        let currentMap = inMemoryOpened ?? [:]
        lock.unlock()
        ioQueue.async {
            if let data = try? JSONEncoder().encode(currentMap) {
                defaults.set(data, forKey: openedKey)
            }
        }
    }

    /// Last-opened timestamp for a comic (0 if never opened) — the library sort key.
    static func openedAt(_ url: URL) -> Double {
        lock.lock(); defer { lock.unlock() }
        if let mem = inMemoryOpened { return mem[url.lastPathComponent] ?? 0 }
        return inMemoryOpenedLocked()[url.lastPathComponent] ?? 0
    }

    private static func inMemoryProgressLocked() -> [String: Progress] {
        if let mem = inMemoryProgress { return mem }
        if let data = defaults.data(forKey: key),
           let map = try? JSONDecoder().decode([String: Progress].self, from: data) {
            inMemoryProgress = map
            return map
        }
        if let legacyData = defaults.data(forKey: legacyKey),
           let map = try? JSONDecoder().decode([String: Progress].self, from: legacyData) {
            inMemoryProgress = map
            return map
        }
        inMemoryProgress = [:]
        return [:]
    }

    private static func inMemoryOpenedLocked() -> [String: Double] {
        if let mem = inMemoryOpened { return mem }
        if let data = defaults.data(forKey: openedKey),
           let map = try? JSONDecoder().decode([String: Double].self, from: data) {
            inMemoryOpened = map
            return map
        }
        if let legacyData = defaults.data(forKey: legacyOpenedKey),
           let map = try? JSONDecoder().decode([String: Double].self, from: legacyData) {
            inMemoryOpened = map
            return map
        }
        inMemoryOpened = [:]
        return [:]
    }
}
