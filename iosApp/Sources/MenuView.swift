import SwiftUI

/// The Leaf menu / about screen — the SwiftUI port of Android's MenuScreen.kt. Holds the AMOLED
/// theme toggle, reading mode switch, grid columns setting, and the about section.
struct MenuView: View {
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dismiss) private var dismiss

    private static let repoURL = URL(string: "https://github.com/bclark/leaf-reader")!
    private static let upstreamURL = URL(string: "https://github.com/batunii/chika")!
    private static let privacyURL = URL(string: "https://github.com/bclark/leaf-reader/blob/main/PRIVACY.md")!
    private static let licensesURL = URL(string: "https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_NOTICES.md")!

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "0.3.2"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }

    var body: some View {
        ZStack {
            settings.ground.ignoresSafeArea()

            VStack(spacing: 0) {
                // App Bar: leafPrimary anchors the brand at the top of the screen
                VStack(spacing: 0) {
                    HStack(spacing: 14) {
                        Button { dismiss() } label: {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Leaf.cream)
                                .frame(width: 38, height: 38)
                                .background(Leaf.cream.opacity(0.14))
                                .clipShape(Circle())
                        }
                        OchreBadge(text: "MENU")
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 14)
                }
                .background(Leaf.leafPrimary.ignoresSafeArea(edges: .top))

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Spacer().frame(height: 24)

                        // ---- Theme -------------------------------------------------------
                        SectionLabel("THEME")
                        Spacer().frame(height: 10)
                        ToggleRow(
                            title: "AMOLED Black",
                            subtitle: "True-black background — easy on OLED screens and battery.",
                            isOn: $settings.amoled
                        )

                        Spacer().frame(height: 32)

                        // ---- Reading -----------------------------------------------------
                        SectionLabel("READING")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Reading Mode")
                                .font(.archivo(14, weight: 700))
                                .foregroundColor(Leaf.cream)
                            Text(settings.pageSlideEnabled
                                 ? "Turn full pages with a smooth slide transition."
                                 : "Guided reading that automatically zooms and steps through each detected panel.")
                                .font(.archivo(12, weight: 400))
                                .foregroundColor(Leaf.creamMuted)

                            HStack(spacing: 2) {
                                Button {
                                    settings.pageSlideEnabled = true
                                } label: {
                                    Text("PAGE FLIP")
                                        .font(.archivo(12, weight: 700))
                                        .foregroundColor(settings.pageSlideEnabled ? Leaf.ink : Leaf.creamMuted)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(settings.pageSlideEnabled ? Leaf.ochre : Color.clear)
                                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                                }
                                .buttonStyle(.plain)

                                Button {
                                    settings.pageSlideEnabled = false
                                } label: {
                                    Text("PANEL BY PANEL")
                                        .font(.archivo(12, weight: 700))
                                        .foregroundColor(!settings.pageSlideEnabled ? Leaf.ink : Leaf.creamMuted)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(!settings.pageSlideEnabled ? Leaf.ochre : Color.clear)
                                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(2)
                            .background(Leaf.ink)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .padding(.top, 4)
                        }
                        .padding(16)
                        .background(Leaf.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        Spacer().frame(height: 32)

                        // ---- Library -----------------------------------------------------
                        SectionLabel("LIBRARY")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Grid Columns")
                                .font(.archivo(14, weight: 700))
                                .foregroundColor(Leaf.cream)
                            Text("Choose how many comic covers appear per row on the library screen.")
                                .font(.archivo(12, weight: 400))
                                .foregroundColor(Leaf.creamMuted)

                            HStack(spacing: 2) {
                                ForEach([2, 3, 4, 6], id: \.self) { count in
                                    let isSelected = settings.libraryColumns == count
                                    Button {
                                        settings.libraryColumns = count
                                    } label: {
                                        Text("\(count) COL")
                                            .font(.archivo(12, weight: 700))
                                            .foregroundColor(isSelected ? Leaf.ink : Leaf.creamMuted)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(isSelected ? Leaf.ochre : Color.clear)
                                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(2)
                            .background(Leaf.ink)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .padding(.top, 4)
                        }
                        .padding(16)
                        .background(Leaf.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        Spacer().frame(height: 32)

                        // ---- About -------------------------------------------------------
                        SectionLabel("ABOUT")
                        Spacer().frame(height: 16)
                        LeafWordmark()
                        Spacer().frame(height: 12)
                        OchreBadge(text: "VERSION \(version)")
                        Spacer().frame(height: 14)
                        Text("A comic reader for iOS")
                            .font(.archivo(14, weight: 600)).foregroundColor(Leaf.cream)
                        Spacer().frame(height: 14)
                        Text("Leaf is an offline comic reader with on-device ML panel detection, interactive page flip, and multi-format archives.")
                            .font(.archivo(13)).lineSpacing(4).foregroundColor(Leaf.cream)
                        Spacer().frame(height: 14)
                        Link(destination: Self.repoURL) {
                            Text("Source: github.com/bclark/leaf-reader")
                                .font(.archivo(11, weight: 600)).foregroundColor(Leaf.ochre)
                                .padding(.top, 2)
                        }
                        Spacer().frame(height: 10)
                        Text("Forked with appreciation from Chika by Chakra.")
                            .font(.archivo(12)).foregroundColor(Leaf.creamMuted)
                        Spacer().frame(height: 6)
                        Link(destination: Self.upstreamURL) {
                            Text("Upstream: github.com/batunii/chika")
                                .font(.archivo(11, weight: 600)).foregroundColor(Leaf.creamMuted)
                                .padding(.top, 2)
                        }

                        Spacer().frame(height: 18)
                        Link(destination: Self.privacyURL) {
                            HStack {
                                Text("Privacy policy — Leaf collects no data.")
                                    .font(.archivo(12)).foregroundColor(Leaf.cream)
                                Spacer()
                                Image(systemName: "arrow.up.right")
                                    .font(.system(size: 11, weight: .bold)).foregroundColor(Leaf.creamMuted)
                            }
                            .padding(12)
                            .background(Leaf.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                        }
                        Spacer().frame(height: 8)
                        Link(destination: Self.licensesURL) {
                            HStack {
                                Text("Open-source licenses & notices.")
                                    .font(.archivo(12)).foregroundColor(Leaf.cream)
                                Spacer()
                                Image(systemName: "arrow.up.right")
                                    .font(.system(size: 11, weight: .bold)).foregroundColor(Leaf.creamMuted)
                            }
                            .padding(12)
                            .background(Leaf.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                        }

                        Spacer().frame(height: 24)

                        // ---- Support -----------------------------------------------------
                        Text("Leaf is free and made with care. If it brings you joy, you can support upstream development.")
                            .font(.archivo(12)).lineSpacing(3).foregroundColor(Leaf.creamMuted)
                        Spacer().frame(height: 14)
                        Link(destination: Self.repoURL) {
                            HStack(spacing: 10) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 18)).foregroundColor(Leaf.cream)
                                Text("SUPPORT UPSTREAM")
                                    .font(.anton(15)).tracking(0.5).foregroundColor(Leaf.cream)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Leaf.leafPrimary)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .comicShadow(offset: 4, color: .black.opacity(0.6), corner: 4)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 28)
                }
            }
        }
        .navigationBarHidden(true)
    }
}

/// Uppercase ochre section kicker (Archivo, wide tracking) — matches the Android SectionLabel.
private struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased())
            .font(.archivo(9, weight: 800)).tracking(2.5).foregroundColor(Leaf.ochre)
    }
}

/// A titled settings row with a trailing switch. Tapping anywhere on the row toggles it, and the
/// ground stays ink-soft even in AMOLED (matching Android's hardcoded InkSoft row).
private struct ToggleRow: View {
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.archivo(14, weight: 700)).foregroundColor(Leaf.cream)
                Text(subtitle)
                    .font(.archivo(10.5, weight: 500)).lineSpacing(3).foregroundColor(Leaf.creamMuted)
            }
            Spacer(minLength: 0)
            LeafSwitch(isOn: $isOn)
        }
        .padding(.init(top: 12, leading: 16, bottom: 12, trailing: 10))
        .background(Leaf.inkSoft)
        .clipShape(RoundedCornerShape(cornerRadius: 6))
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.easeInOut(duration: 0.15)) { isOn.toggle() } }
    }
}

/// Brand-styled switch: ochre track + ink thumb when on, ink track + muted-cream thumb when off,
/// matching Android's SwitchDefaults colors (stock SwiftUI Toggle can't recolor the thumb).
private struct LeafSwitch: View {
    @Binding var isOn: Bool

    var body: some View {
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule()
                .fill(isOn ? Leaf.ochre : Leaf.ink)
                .overlay(Capsule().stroke(isOn ? Leaf.ochre : Leaf.creamMuted, lineWidth: 2))
            Circle()
                .fill(isOn ? Leaf.ink : Leaf.creamMuted)
                .padding(3)
        }
        .frame(width: 46, height: 28)
        .onTapGesture { withAnimation(.easeInOut(duration: 0.15)) { isOn.toggle() } }
    }
}
