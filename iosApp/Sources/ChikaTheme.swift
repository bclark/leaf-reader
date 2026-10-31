import SwiftUI
import UIKit
import CoreText

/// Leaf / Chika brand palette — kept in sync with the Android theme (Color.kt).
enum Chika {
    // Leaf Brand Primary & Secondary
    static let leafPrimary = Color(hex: 0x1C3A27)      // Forest Primary
    static let leafPrimaryDark = Color(hex: 0x122519)  // Deep Forest
    static let leafSecondary = Color(hex: 0x4A7C59)    // Sage Secondary
    static let leafAccent = Color(hex: 0xD4AF37)       // Accent Gold

    // Interface and Support
    static let comicRose = Color(hex: 0x935B6D)        // Warm accent / label
    static let paperWhite = Color(hex: 0xF9F9FB)       // Light BG / Text
    static let deepInk = Color(hex: 0x14191C)          // Dark Mode BG
    static let deepInkSurface = Color(hex: 0x1E2529)   // Cards / bars in Dark Mode

    // Compatibility aliases for existing design components
    static let ink = deepInk
    static let inkSoft = deepInkSurface
    static let crimson = leafPrimary
    static let crimsonBright = leafSecondary
    static let maroon = leafPrimaryDark
    static let maroonDeep = Color(hex: 0x0E1D13)
    static let cream = paperWhite
    static let paper = paperWhite
    static let ochre = leafAccent
    static let ochreBright = Color(hex: 0xE5C158)
    static let creamMuted = paperWhite.opacity(0.55)
}

/// Brand type: Anton (display — wordmark, titles, page numbers) and Archivo (UI labels, body).
/// Archivo ships as a single variable TTF (PS name "Archivo-SemiBold") with a `wght` axis 100–900.
extension Font {
    static func anton(_ size: CGFloat) -> Font { .custom("Anton-Regular", fixedSize: size) }

    /// Archivo at a specific weight (100–900) by driving the variable font's `wght` axis directly —
    /// SwiftUI's `.weight()` can't pick a variable-font instance (it would synthesize a fake bold),
    /// so Android's real ExtraBold/Bold/Medium weights are matched via the CoreText variation axis.
    /// Defaults to 600 (SemiBold), the baseline UI weight. If the axis can't be applied the font
    /// stays at its SemiBold base — never dropping to the system font.
    static func archivo(_ size: CGFloat, weight: CGFloat = 600) -> Font {
        let wghtAxis = UInt32(0x77676874) // 'wght'
        let descriptor = UIFontDescriptor(fontAttributes: [
            .name: "Archivo-SemiBold",
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [wghtAxis: weight],
        ])
        return Font(UIFont(descriptor: descriptor, size: size))
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// Archivo kicker: uppercase, wide tracking — the tag/label style used across the UI.
struct KickerText: View {
    let text: String
    var size: CGFloat = 11
    var color: Color = Chika.creamMuted
    init(_ text: String, size: CGFloat = 11, color: Color = Chika.creamMuted) {
        self.text = text; self.size = size; self.color = color
    }
    var body: some View {
        Text(text.uppercased())
            .font(.archivo(size))
            .tracking(2)
            .foregroundColor(color)
    }
}
