import SwiftUI

/// Presentation only: never infers a remote percentage from ticks or changes saved progress.
enum LibraryReadingState: Equatable {
    case unread, unknown, started, progress(Double), completed

    static func local(_ value: Progress?) -> Self {
        guard let value else { return .unread }
        if value.isCompleted { return .completed }
        return value.fraction > 0 && value.fraction < 1 ? .progress(value.fraction) : .started
    }

    static func remote(_ value: JellyfinBook.CatalogueUserData?) -> Self {
        guard let value else { return .unknown }
        if value.Played == true { return .completed }
        if let percent = value.PlayedPercentage, percent.isFinite, percent > 0, percent < 100 {
            return .progress(percent / 100)
        }
        if (value.PlaybackPositionTicks ?? 0) > 0 || value.PlayedPercentage == 100 { return .started }
        return value.Played == false ? .unread : .unknown
    }

    /// A completed front cover does not establish that its whole collection is finished.
    static func remoteCollection(_ states: [Self]) -> Self {
        let fractions = states.compactMap { state -> Double? in
            switch state {
            case .unread: return 0
            case .completed: return 1
            case .progress(let fraction): return fraction
            case .started, .unknown: return nil
            }
        }
        let fraction = !states.isEmpty && fractions.count == states.count
            ? fractions.reduce(0, +) / Double(states.count) : nil
        return collection(states, fraction: fraction)
    }

    static func collection(_ states: [Self], fraction: Double? = nil) -> Self {
        guard !states.isEmpty else { return .unknown }
        if states.allSatisfy({ $0 == .completed }) { return .completed }
        if !states.contains(.unknown), let fraction, fraction.isFinite, fraction > 0, fraction < 1 {
            return .progress(fraction)
        }
        if states.contains(where: { state in
            switch state { case .completed, .started, .progress: return true; default: return false }
        }) { return .started }
        return states.allSatisfy { $0 == .unread } ? .unread : .unknown
    }

    var label: String {
        switch self {
        case .unread: return String(localized: "UNREAD")
        case .unknown: return String(localized: "Reading status unknown")
        case .started, .progress: return String(localized: "In progress")
        case .completed: return String(localized: "Finished")
        }
    }
}

struct LibraryAppearanceControls: View {
    @EnvironmentObject private var settings: LeafSettings
    var body: some View {
        Group {
            Toggle(isOn: $settings.libraryShowTitles) {
                Text("Show titles").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle()).onTapGesture { settings.libraryShowTitles.toggle() }
            }.accessibilityIdentifier("library.appearance.titles")
            Toggle(isOn: $settings.libraryShowDetails) {
                Text("Show source and file details").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle()).onTapGesture { settings.libraryShowDetails.toggle() }
            }.accessibilityIdentifier("library.appearance.details")
            Toggle(isOn: $settings.libraryShowReadingIndicators) {
                Text("Show reading indicators").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle()).onTapGesture { settings.libraryShowReadingIndicators.toggle() }
            }.accessibilityIdentifier("library.appearance.reading")
        }
        .leafControlFont(14, weight: 600)
        .frame(minHeight: 44)
        .tint(Leaf.leafPrimary)
    }
}

struct LibraryReadingIndicator: View {
    @EnvironmentObject private var settings: LeafSettings
    let state: LibraryReadingState
    var body: some View {
        Group {
            if settings.libraryShowReadingIndicators {
                switch state {
                case .completed, .started:
                    Image(systemName: state == .completed ? "checkmark" : "bookmark.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Leaf.ink).padding(6)
                        .background(Leaf.ochre, in: Circle()).padding(6)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                case .progress(let fraction):
                    GeometryReader { geometry in
                        Rectangle().fill(Leaf.ochre)
                            .frame(width: geometry.size.width * fraction)
                    }
                    .frame(height: 3).background(Leaf.ink.opacity(0.65))
                    .frame(maxHeight: .infinity, alignment: .bottom)
                case .unread, .unknown: EmptyView()
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
