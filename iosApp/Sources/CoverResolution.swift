import SwiftUI

/// Quantized Retina pixel budget: resizing a shelf upgrades artwork without per-point reloads.
enum CoverResolution {
    static let maximum = 2048

    static func pixels(width: CGFloat, scale: CGFloat) -> Int {
        guard width.isFinite, scale.isFinite, width > 0, scale > 0 else { return 0 }
        let required = min(width / 0.7 * scale, CGFloat(maximum))
        return min(maximum, max(256, Int(ceil(required / 256)) * 256))
    }
}

private struct LibraryCoverResolution: ViewModifier {
    @Environment(\.displayScale) private var scale
    @Binding var pixels: Int

    func body(content: Content) -> some View {
        content.onGeometryChange(for: Int.self) { geometry in
            CoverResolution.pixels(width: geometry.size.width, scale: scale)
        } action: { pixels = $0 }
    }
}

extension View {
    func libraryCoverResolution(_ pixels: Binding<Int>) -> some View {
        modifier(LibraryCoverResolution(pixels: pixels))
    }
}

struct StackCoverRequest: Hashable {
    let keys: [StackCoverKey]
    let pixels: Int
}
