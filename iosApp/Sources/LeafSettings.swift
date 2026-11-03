import SwiftUI
import LeafShared

/// App-wide UI settings, persisted in UserDefaults — the iOS counterpart to Android's AppSettings.
final class LeafSettings: ObservableObject {
    private static let amoledKey = "leaf.amoledTheme"
    private static let pageSlideKey = "leaf.pageSlideEnabled"
    private static let pageFitModeKey = "leaf.pageFitMode"
    private static let portraitSpreadKey = "leaf.portraitSpread"
    private static let landscapeSpreadKey = "leaf.landscapeSpread"
    private static let libraryColumnsKey = "leaf.libraryColumns"
    private static let legacyAmoledKey = "chika.amoledTheme"
    private static let legacyPageSlideKey = "chika.pageSlideEnabled"
    private static let legacyLibraryColumnsKey = "chika.libraryColumns"
    private let defaults = UserDefaults.standard

    @Published var amoled: Bool {
        didSet { defaults.set(amoled, forKey: Self.amoledKey) }
    }

    /// When true, reading mode is Page Flip (full page slide); when false, Panel by Panel (guided reading).
    @Published var pageSlideEnabled: Bool {
        didSet { defaults.set(pageSlideEnabled, forKey: Self.pageSlideKey) }
    }

    /// Preferred whole-page framing: fit-to-page (contain, default) or fit-to-width.
    @Published var pageFitMode: PageFitMode {
        didSet { defaults.set(pageFitMode == .fitWidth ? "FIT_WIDTH" : "FIT_PAGE", forKey: Self.pageFitModeKey) }
    }

    /// Independent portrait two-page spread choice.
    @Published var portraitSpread: Bool {
        didSet { defaults.set(portraitSpread, forKey: Self.portraitSpreadKey) }
    }

    /// Two-page spread choice for landscape orientation.
    @Published var landscapeSpread: Bool {
        didSet { defaults.set(landscapeSpread, forKey: Self.landscapeSpreadKey) }
    }

    /// Number of comic cover columns on the library screen (2, 3, 4, 6).
    @Published var libraryColumns: Int {
        didSet { defaults.set(libraryColumns, forKey: Self.libraryColumnsKey) }
    }

    init() {
        if defaults.object(forKey: Self.amoledKey) != nil {
            amoled = defaults.bool(forKey: Self.amoledKey)
        } else {
            amoled = defaults.bool(forKey: Self.legacyAmoledKey)
        }

        // Default pageSlideEnabled to true if not set yet (matching Android's default)
        if defaults.object(forKey: Self.pageSlideKey) != nil {
            pageSlideEnabled = defaults.bool(forKey: Self.pageSlideKey)
        } else if defaults.object(forKey: Self.legacyPageSlideKey) != nil {
            pageSlideEnabled = defaults.bool(forKey: Self.legacyPageSlideKey)
        } else {
            pageSlideEnabled = true
        }

        if let fitStr = defaults.string(forKey: Self.pageFitModeKey), fitStr == "FIT_WIDTH" {
            pageFitMode = .fitWidth
        } else {
            pageFitMode = .fitPage
        }

        portraitSpread = defaults.bool(forKey: Self.portraitSpreadKey)
        landscapeSpread = defaults.bool(forKey: Self.landscapeSpreadKey)

        if defaults.object(forKey: Self.libraryColumnsKey) != nil {
            let cols = defaults.integer(forKey: Self.libraryColumnsKey)
            libraryColumns = cols > 0 ? cols : 2
        } else if defaults.object(forKey: Self.legacyLibraryColumnsKey) != nil {
            let cols = defaults.integer(forKey: Self.legacyLibraryColumnsKey)
            libraryColumns = cols > 0 ? cols : 2
        } else {
            // Adaptive default: wide displays (Duo / iPad / landscape) look best with 3 or 4 columns
            let screen = UIScreen.main.bounds
            let isWide = max(screen.width, screen.height) >= 800 || min(screen.width, screen.height) >= 600
            libraryColumns = isWide ? 4 : 2
        }
    }

    /// Full-screen ground: true black in AMOLED, brand ink otherwise.
    var ground: Color { amoled ? .black : Leaf.ink }

    /// Soft raised surface (rows, chips): near-black in AMOLED, brand ink-soft otherwise.
    var groundSoft: Color { amoled ? Color(hex: 0x101010) : Leaf.inkSoft }
}
