import SwiftUI

/// App-wide UI settings, persisted in UserDefaults — the iOS counterpart to Android's AppSettings.
final class ChikaSettings: ObservableObject {
    private static let amoledKey = "chika.amoledTheme"
    private static let pageSlideKey = "chika.pageSlideEnabled"
    private static let libraryColumnsKey = "chika.libraryColumns"
    private let defaults = UserDefaults.standard

    @Published var amoled: Bool {
        didSet { defaults.set(amoled, forKey: Self.amoledKey) }
    }

    /// When true, reading mode is Page Flip (full page slide); when false, Panel by Panel (guided reading).
    @Published var pageSlideEnabled: Bool {
        didSet { defaults.set(pageSlideEnabled, forKey: Self.pageSlideKey) }
    }

    /// Number of comic cover columns on the library screen (2, 3, 4, 6).
    @Published var libraryColumns: Int {
        didSet { defaults.set(libraryColumns, forKey: Self.libraryColumnsKey) }
    }

    init() {
        amoled = defaults.bool(forKey: Self.amoledKey)
        // Default pageSlideEnabled to true if not set yet (matching Android's default)
        if defaults.object(forKey: Self.pageSlideKey) != nil {
            pageSlideEnabled = defaults.bool(forKey: Self.pageSlideKey)
        } else {
            pageSlideEnabled = true
        }
        let cols = defaults.integer(forKey: Self.libraryColumnsKey)
        libraryColumns = cols > 0 ? cols : 2
    }

    /// Full-screen ground: true black in AMOLED, brand ink otherwise.
    var ground: Color { amoled ? .black : Chika.ink }

    /// Soft raised surface (rows, chips): near-black in AMOLED, brand ink-soft otherwise.
    var groundSoft: Color { amoled ? Color(hex: 0x101010) : Chika.inkSoft }
}
