import SwiftUI

/// View selection and the unread predicate are independent for the entire Library journey.
struct LibraryBrowseState {
    enum ViewMode: String, CaseIterable, Identifiable {
        case series, lists, all, offline
        var id: String { rawValue }
        var title: LocalizedStringKey {
            switch self {
            case .all: return "All"
            case .series: return "Series"
            case .lists: return "Lists"
            case .offline: return "Offline"
            }
        }
        /// Plain string for UIKit segment titles; same catalog keys as `title`.
        var label: String {
            switch self {
            case .all: return String(localized: "All")
            case .series: return String(localized: "Series")
            case .lists: return String(localized: "Lists")
            case .offline: return String(localized: "Offline")
            }
        }
        var filter: LibraryFilter {
            switch self {
            case .all, .offline: return .all
            case .series: return .series
            case .lists: return .readingLists
            }
        }
    }

    var view: ViewMode = .series
    var unreadOnly = false
    private(set) var hasChosenView = false
    private var handledLaunchFailure = false

    mutating func select(_ view: ViewMode) {
        self.view = view
        hasChosenView = true
    }

    mutating func handleUnreachableLaunch() {
        guard !handledLaunchFailure else { return }
        handledLaunchFailure = true
        if !hasChosenView { view = .offline }
    }

    mutating func finishLaunchCheck() {
        handledLaunchFailure = true
    }

    @MainActor
    static func readableCopies(_ urls: [URL]) async throws -> Set<URL> {
        var readable: Set<URL> = []
        for url in urls {
            try Task.checkCancellation()
            let result = await CoverLoader.load(for: url)
            try Task.checkCancellation()
            if result.pageCount > 0, result.cover != nil { readable.insert(url) }
        }
        return readable
    }

    static func includes(completed: Bool?, unreadOnly: Bool) -> Bool {
        !unreadOnly || completed == false
    }

    static func matches(_ query: String, values: [String]) -> Bool {
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return query.isEmpty || values.contains { $0.localizedCaseInsensitiveContains(query) }
    }
}

struct LibraryViewSelector: View {
    @Binding var selection: LibraryBrowseState.ViewMode
    var onBrand = false
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Group {
            if typeSize >= .xxxLarge {
                Picker("Library view", selection: $selection) { choices }
                    .pickerStyle(.menu)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("library.views")
            } else {
                LibrarySegmentedControl(selection: $selection, typeSize: typeSize, onBrand: onBrand)
            }
        }
        .tint(Leaf.goldText)
        .frame(minHeight: 44)
    }

    private var choices: some View {
        ForEach(LibraryBrowseState.ViewMode.allCases) { view in
            Text(view.title).tag(view)
        }
    }
}

/// The system segmented control, kept for its native semantics, with the approved green
/// selection on a raised track. Styled per instance so other segmented pickers are untouched.
private struct LibrarySegmentedControl: UIViewRepresentable {
    @Binding var selection: LibraryBrowseState.ViewMode
    /// Read so a text-size change re-runs `updateUIView` and rescales the titles.
    let typeSize: DynamicTypeSize
    let onBrand: Bool

    private static let height: CGFloat = 48
    private static let modes = LibraryBrowseState.ViewMode.allCases

    final class Coordinator: NSObject {
        var selection: Binding<LibraryBrowseState.ViewMode>
        init(selection: Binding<LibraryBrowseState.ViewMode>) { self.selection = selection }
        @objc func changed(_ control: UISegmentedControl) {
            let index = control.selectedSegmentIndex
            guard LibrarySegmentedControl.modes.indices.contains(index) else { return }
            selection.wrappedValue = LibrarySegmentedControl.modes[index]
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(selection: $selection) }

    func makeUIView(context: Context) -> UISegmentedControl {
        let control = UISegmentedControl(items: Self.modes.map(\.label))
        control.accessibilityIdentifier = "library.views"
        control.selectedSegmentTintColor = UIColor { traits in
            // Forest on the light track, sage on the dark one; paper text passes 4.5:1 on both.
            UIColor(Self.hex(traits.userInterfaceStyle == .dark ? 0x4A7C59 : 0x1C3A27))
        }
        control.backgroundColor = UIColor(Leaf.raisedSurface)
        control.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .valueChanged)
        return control
    }

    func updateUIView(_ control: UISegmentedControl, context: Context) {
        context.coordinator.selection = $selection
        control.backgroundColor = onBrand ? UIColor(Color(hex: 0x122519)) : UIColor(Leaf.raisedSurface)
        control.selectedSegmentTintColor = onBrand ? UIColor(Color(hex: 0x4A7C59)) : UIColor { traits in
            UIColor(Self.hex(traits.userInterfaceStyle == .dark ? 0x4A7C59 : 0x1C3A27))
        }
        let index = Self.modes.firstIndex(of: selection) ?? 0
        if control.selectedSegmentIndex != index { control.selectedSegmentIndex = index }
        control.setTitleTextAttributes([
            .foregroundColor: UIColor(onBrand ? Leaf.paperWhite : Leaf.text), .font: Self.font(weight: 500),
        ], for: .normal)
        control.setTitleTextAttributes([
            .foregroundColor: UIColor(Leaf.paperWhite), .font: Self.font(weight: 700),
        ], for: .selected)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UISegmentedControl, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? uiView.intrinsicContentSize.width, height: Self.height)
    }

    private static func hex(_ value: UInt32) -> Color { Color(hex: value) }

    private static func font(weight: CGFloat) -> UIFont {
        let wghtAxis = UInt32(0x77676874) // 'wght'
        let descriptor = UIFontDescriptor(fontAttributes: [
            .name: "Archivo-SemiBold",
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [wghtAxis: weight],
        ])
        return UIFontMetrics(forTextStyle: .subheadline).scaledFont(for: UIFont(descriptor: descriptor, size: 15))
    }
}

struct UnreadFilterButton: View {
    @Binding var isOn: Bool
    var onBrand = false

    var body: some View {
        Button { isOn.toggle() } label: {
            Image(systemName: "book.closed")
                .overlay(alignment: .topTrailing) {
                    Circle().fill(isOn ? Leaf.ink : (onBrand ? Leaf.paperWhite : Leaf.text))
                        .frame(width: 6, height: 6).offset(x: 4, y: -3)
                }
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(isOn ? Leaf.ink : (onBrand ? Leaf.paperWhite : Leaf.text))
                .frame(minWidth: 44, minHeight: 44)
                .background(isOn ? Leaf.ochre : Color.clear, in: RoundedRectangle(cornerRadius: 10))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Unread only")
        .accessibilityValue(isOn ? Text("On") : Text("Off"))
        .accessibilityAddTraits(isOn ? .isSelected : [])
        .accessibilityIdentifier("library.unread")
    }
}

struct UnreadFilterChip: View {
    @Binding var isOn: Bool
    var onClear: () -> Void = {}

    var body: some View {
        if isOn {
            HStack {
                Spacer(minLength: 0)
                Button {
                    isOn = false
                    onClear()
                } label: {
                    HStack(spacing: 8) {
                        Text("Unread only").font(.subheadline)
                        Image(systemName: "xmark").font(.caption.weight(.semibold))
                    }
                    .foregroundStyle(Leaf.ink)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 44)
                    .background(Leaf.ochre, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear Unread only")
                .accessibilityIdentifier("library.clearUnread")
            }
        }
    }
}

/// iOS 16 scroll restoration uses stable content identities, independently of view/filter state.
struct LibraryScrollPositions: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, last in last })
    }
}

extension View {
    func libraryScrollAnchor(_ id: String) -> some View {
        self.id(id).background(GeometryReader { geometry in
            Color.clear.preference(key: LibraryScrollPositions.self,
                value: [id: geometry.frame(in: .named("library.scroll"))])
        })
    }
}

/// First-run library with no comics and no Jellyfin connection.
struct EmptyLibraryView: View {
    @Environment(\.colorScheme) private var colorScheme
    let importing: Bool
    let add: () -> Void
    let connect: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            VStack(alignment: .leading, spacing: 10) {
                Image("LeafMark")
                    .resizable()
                    .frame(width: 72, height: 72)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityHidden(true)
                Text("Your comics, your way")
                    .textCase(.uppercase)
                    .font(.custom("Anton-Regular", size: 30, relativeTo: .title))
                    .foregroundColor(Leaf.text)
                    .padding(.top, 8)
                    .accessibilityAddTraits(.isHeader)
                Text("Connect your Jellyfin library, or read files stored on this device. You can connect later from Add Comics.")
                    .leafControlFont(17, weight: 400)
                    .foregroundColor(Leaf.secondaryText)
            }
            VStack(spacing: 12) {
                Button { if !importing { add() } } label: {
                    HStack(spacing: 10) {
                        if importing {
                            ProgressView().tint(Leaf.ink)
                        } else {
                            Image(systemName: "plus").font(.system(size: 17, weight: .bold))
                        }
                        Text("Add comics").leafControlFont(17, weight: 700)
                    }
                    .foregroundColor(Leaf.ink)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(Leaf.ochre, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .disabled(importing)
                .accessibilityIdentifier("library.empty.add")
                Button(action: connect) {
                    HStack(spacing: 10) {
                        Image(systemName: "server.rack").font(.system(size: 17, weight: .semibold))
                        Text("Connect Jellyfin").leafControlFont(17, weight: 700)
                    }
                    .foregroundColor(Leaf.paperWhite)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(colorScheme == .dark ? Leaf.leafSecondary : Leaf.leafPrimary,
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .accessibilityIdentifier("library.empty.connect")
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 40)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
