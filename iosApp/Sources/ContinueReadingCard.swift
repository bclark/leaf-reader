import SwiftUI

/// Local copies resume immediately; server-only comics open their existing details flow.
struct ContinueReadingCard: View {
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.displayScale) private var displayScale
    private var coverPixels: Int { CoverResolution.pixels(width: wide ? 76 : 92, scale: displayScale) }
    @ObservedObject private var covers = CoverLoader.shared
    let item: LibraryContinueItem
    let client: JellyfinClient?
    let profileID: String?
    let wide: Bool
    var linkedCover: UIImage? = nil
    let open: () -> Void
    @State private var cover: UIImage?

    var body: some View {
        Button(action: open) {
            let layout = typeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
                : AnyLayout(HStackLayout(alignment: .top, spacing: 14))
            layout {
                Group {
                    if let cover = linkedCover ?? cover { LibraryCoverArtwork(image: cover) }
                    else { GeneratedCover(title: item.title, columns: 4) }
                }
                .frame(width: wide ? 76 : 92, height: wide ? 108 : 131)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    if !wide {
                        Text("Continue reading").textCase(.uppercase)
                            .leafControlFont(11, weight: 700).tracking(0.8).foregroundColor(Leaf.goldText)
                    }
                    Text(item.title)
                        .font(.custom("Anton-Regular", size: wide ? 20 : 22, relativeTo: .title3))
                        .foregroundColor(Leaf.text).lineLimit(typeSize.isAccessibilitySize ? nil : (wide ? 1 : 2))
                        .multilineTextAlignment(.leading)
                    if item.linked != nil {
                        Text("Files").leafControlFont(11, weight: 700).foregroundStyle(Leaf.goldText)
                    }
                    Group {
                        if let count = item.total { Text("Page \(item.page + 1) of \(count)") }
                        else { Text("Page \(item.page + 1)") }
                    }
                    .leafControlFont(13, weight: 500).foregroundColor(Leaf.secondaryText)
                    if let fraction = item.fraction {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Leaf.secondaryText.opacity(0.35))
                                Capsule().fill(Leaf.ochre).frame(width: geo.size.width * CGFloat(fraction))
                            }
                        }
                        .frame(height: 4).padding(.top, 4).accessibilityHidden(true)
                    }
                    Spacer(minLength: 8)
                    HStack(spacing: 8) {
                        Image(systemName: "book").font(.system(size: 17, weight: .semibold))
                        Text("Continue").leafControlFont(16, weight: 700)
                    }
                    .foregroundColor(Leaf.ink).frame(maxWidth: .infinity, minHeight: 44)
                    .background(Leaf.ochre, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .frame(minHeight: typeSize.isAccessibilitySize ? nil : (wide ? 108 : 131), alignment: .top)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(settings.groundSoft, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Leaf.text.opacity(0.12), lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityName)
        .accessibilityIdentifier("library.continue")
        .task(id: imageKey) {
            cover = nil
            if let url = item.url {
                let result = await CoverLoader.load(for: url, maxPixel: coverPixels)
                if !Task.isCancelled { cover = result.cover }
            } else if let book = item.book, let client, let data = try? await client.cover(book, maxWidth: coverPixels) {
                let image = await Task.detached(priority: .utility) { JellyfinBookRow.thumbnail(data, maxPixel: coverPixels) }.value
                if !Task.isCancelled { cover = image }
            }
        }
    }

    private var accessibilityName: String {
        let action = wide ? String(localized: "Continue") : String(localized: "Continue reading")
        let position = item.total.map { String(localized: "Page \(item.page + 1) of \($0)") }
            ?? String(localized: "Page \(item.page + 1)")
        let source = item.linked == nil ? "" : ", " + String(localized: "Files")
        return "\(action), \(item.title)\(source), \(position)"
    }

    private var imageKey: String {
        "\(coverPixels):\(profileID ?? ""):\(item.id):\(item.book?.imageTags?["Primary"] ?? ""):\(item.url.map { String(describing: covers.revisions(for: [$0])) } ?? "")"
    }
}
