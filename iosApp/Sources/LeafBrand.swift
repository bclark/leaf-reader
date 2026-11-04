import SwiftUI

// MARK: - Signature texture & motifs (ported from LeafBrand.kt)

/// Low-opacity halftone dot wash drawn behind content. Keep alpha subtle (0.05–0.22).
struct Halftone: View {
    var color: Color = Leaf.ink
    var alpha: Double = 0.06
    var spacing: CGFloat = 9
    var radius: CGFloat = 1.6

    var body: some View {
        Canvas { ctx, size in
            let dot = Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
            var y: CGFloat = 0
            while y < size.height + spacing {
                var x: CGFloat = 0
                while x < size.width + spacing {
                    ctx.fill(dot.offsetBy(dx: x, dy: y), with: .color(color.opacity(alpha)))
                    x += spacing
                }
                y += spacing
            }
        }
        .allowsHitTesting(false)
    }
}

extension View {
    /// Halftone dot wash layered behind the view.
    func halftone(color: Color = Leaf.ink, alpha: Double = 0.06) -> some View {
        background(Halftone(color: color, alpha: alpha))
    }

    /// Hard offset comic shadow (no blur) peeking bottom-right.
    func comicShadow(offset: CGFloat = 5, color: Color = .black.opacity(0.7), corner: CGFloat = 0) -> some View {
        background(
            RoundedRectangle(cornerRadius: corner)
                .fill(color)
                .offset(x: offset, y: offset)
        )
    }
}

/// The 20-point comic action-burst (same control points as the Android StarburstShape).
struct StarburstShape: Shape {
    private static let pts: [CGFloat] = [
        0.50, 0.00, 0.60, 0.16, 0.78, 0.09, 0.74, 0.28, 0.95, 0.30,
        0.80, 0.46, 0.98, 0.62, 0.77, 0.64, 0.80, 0.86, 0.60, 0.74,
        0.52, 0.96, 0.42, 0.75, 0.22, 0.88, 0.23, 0.65, 0.02, 0.64,
        0.18, 0.46, 0.04, 0.30, 0.25, 0.28, 0.21, 0.09, 0.40, 0.16,
    ]
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let pts = Self.pts
        p.move(to: CGPoint(x: pts[0] * rect.width, y: pts[1] * rect.height))
        var i = 2
        while i < pts.count {
            p.addLine(to: CGPoint(x: pts[i] * rect.width, y: pts[i + 1] * rect.height))
            i += 2
        }
        p.closeSubpath()
        return p
    }
}

/// Upright leaf shape (straight up and down).
struct LeafShape: Shape {
    private static let pts: [CGFloat] = [
        0.441, 0.047, 0.432, 0.100, 0.400, 0.166, 0.244, 0.391,
        0.217, 0.482, 0.213, 0.555, 0.225, 0.623, 0.258, 0.699,
        0.393, 0.836, 0.451, 0.949, 0.455, 0.932, 0.432, 0.875,
        0.451, 0.814, 0.523, 0.752, 0.723, 0.641, 0.771, 0.576,
        0.783, 0.490, 0.770, 0.439, 0.730, 0.375,
    ]
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let pts = Self.pts
        p.move(to: CGPoint(x: pts[0] * rect.width, y: pts[1] * rect.height))
        var i = 2
        while i < pts.count {
            p.addLine(to: CGPoint(x: pts[i] * rect.width, y: pts[i + 1] * rect.height))
            i += 2
        }
        p.closeSubpath()
        return p
    }
}

/// Four L-shaped reticle corner brackets, drawn as an overlay.
struct Reticle: View {
    var color: Color = Leaf.cream
    var inset: CGFloat = 8
    var length: CGFloat = 14
    var stroke: CGFloat = 2.5

    var body: some View {
        Canvas { ctx, size in
            let i = inset, l = length, w = size.width, h = size.height
            func bracket(_ a: CGPoint, _ b: CGPoint) {
                var path = Path(); path.move(to: a); path.addLine(to: b)
                ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: stroke, lineCap: .square))
            }
            bracket(CGPoint(x: i, y: i + l), CGPoint(x: i, y: i)); bracket(CGPoint(x: i, y: i), CGPoint(x: i + l, y: i))
            bracket(CGPoint(x: w - i - l, y: i), CGPoint(x: w - i, y: i)); bracket(CGPoint(x: w - i, y: i), CGPoint(x: w - i, y: i + l))
            bracket(CGPoint(x: i, y: h - i - l), CGPoint(x: i, y: h - i)); bracket(CGPoint(x: i, y: h - i), CGPoint(x: i + l, y: h - i))
            bracket(CGPoint(x: w - i - l, y: h - i), CGPoint(x: w - i, y: h - i)); bracket(CGPoint(x: w - i, y: h - i), CGPoint(x: w - i, y: h - i - l))
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Logo system

/// The Leaf mark with a hard drop shadow.
struct LeafMark: View {
    var size: CGFloat
    var body: some View {
        Image("LeafMark")
            .resizable()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22))
            .comicShadow(offset: 3, color: .black.opacity(0.6), corner: size * 0.22)
    }
}

/// "LEAF" lockup (cream Anton with Leaf icon).
struct LeafWordmark: View {
    var body: some View {
        HStack(spacing: 8) {
            Image("LeafMark")
                .resizable()
                .frame(width: 32, height: 32)
                .clipShape(RoundedRectangle(cornerRadius: 6))
            Text("LEAF")
                .font(.anton(28))
                .foregroundColor(Leaf.cream)
        }
    }
}

/// The rotated ochre "kicker" banner (e.g. YOUR LIBRARY) with ink frame + hard shadow.
struct OchreBadge: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.anton(15))
            .tracking(0.6)
            .foregroundColor(Leaf.ink)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Leaf.ochre)
            .overlay(RoundedRectangle(cornerRadius: 3).stroke(Leaf.ink, lineWidth: 2.5))
            .clipShape(RoundedRectangle(cornerRadius: 3))
            .comicShadow(offset: 3, color: .black.opacity(0.6), corner: 3)
            .rotationEffect(.degrees(-1.5))
    }
}

/// Custom book-themed status icon for "Mark All as Read" (open book with green checkmark badge)
/// vs "Mark All as Unread" (closed book with rose minus/reset badge), matching Android's BookStatusBadgeIcon.
struct BookStatusBadgeIcon: View {
    let isAllCompleted: Bool
    static let completedGreen = Color(hex: 0x4ADE80)
    static let unreadRose = Color(hex: 0xA15555)

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Image(systemName: isAllCompleted ? "book.closed.fill" : "book.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(Leaf.cream)
                .frame(width: 22, height: 22, alignment: .topLeading)

            Circle()
                .fill(isAllCompleted ? Self.unreadRose : Self.completedGreen)
                .frame(width: 13, height: 13)
                .overlay(
                    Circle().stroke(Leaf.deepInkSurface, lineWidth: 1.5)
                )
                .overlay(
                    Image(systemName: isAllCompleted ? "minus" : "checkmark")
                        .font(.system(size: 7, weight: .black))
                        .foregroundColor(isAllCompleted ? Leaf.cream : Leaf.ink)
                )
        }
        .frame(width: 24, height: 24)
        .accessibilityLabel(isAllCompleted ? "Mark all as unread" : "Mark all as read")
    }
}

/// Dog-eared page-curl bookmark badge shape.
struct PageCurlBadgeShape: Shape {
    var corner: CGFloat = 10
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - corner, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + corner))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

/// Folded corner triangle shape.
struct PageCurlFoldShape: Shape {
    var corner: CGFloat = 10
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.maxX - corner, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + corner))
        p.addLine(to: CGPoint(x: rect.maxX - corner, y: rect.minY + corner))
        p.closeSubpath()
        return p
    }
}

/// Dog-eared page-curl bookmark badge showing current page and total page count horizontally.
struct PageCoin: View {
    let page: Int
    let total: Int
    var body: some View {
        HStack(spacing: 6) {
            Text("\(page)")
                .font(.archivo(15, weight: 500))
                .foregroundColor(Leaf.ink)
            Text("/")
                .font(.archivo(13, weight: 400))
                .foregroundColor(Leaf.ink.opacity(0.55))
            Text("\(total)")
                .font(.archivo(14, weight: 400))
                .foregroundColor(Leaf.ink.opacity(0.85))
        }
        .padding(.leading, 11)
        .padding(.trailing, 15)
        .padding(.vertical, 4)
        .background(
            ZStack {
                PageCurlBadgeShape(corner: 10).fill(Color.black.opacity(0.4)).offset(x: 2.5, y: 2.5)
                PageCurlBadgeShape(corner: 10).fill(Leaf.ochre)
                PageCurlFoldShape(corner: 10).fill(Color(red: 0.71, green: 0.57, blue: 0.16))
                PageCurlBadgeShape(corner: 10).stroke(Leaf.ink, lineWidth: 2)
                PageCurlFoldShape(corner: 10).stroke(Leaf.ink, lineWidth: 1.5)
            }
        )
    }
}
