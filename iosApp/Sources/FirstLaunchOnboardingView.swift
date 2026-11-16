import SwiftUI

struct FirstLaunchOnboardingView: View {
    @ObservedObject var onboarding: FirstLaunchOnboarding
    @EnvironmentObject private var settings: LeafSettings
    let busy: Bool
    let focusRequest: Int
    let chooseFile: () -> Void
    let connect: () -> Void
    private enum Action: Hashable { case file, connect }
    @AccessibilityFocusState private var focusedAction: Action?

    private var path: Binding<[FirstLaunchOnboarding.Step]> {
        Binding(get: { onboarding.step == .jellyfin ? [.jellyfin] : [] }, set: { path in
            if path.isEmpty { onboarding.showWelcome() }
        })
    }

    var body: some View {
        NavigationStack(path: path) {
            card(jellyfin: false)
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: FirstLaunchOnboarding.Step.self) { step in
                    if step == .jellyfin {
                        card(jellyfin: true)
                            .navigationTitle("Jellyfin")
                            .navigationBarTitleDisplayMode(.inline)
                            .toolbar(.visible, for: .navigationBar)
                    }
                }
        }
        .tint(Leaf.goldText)
        .onChange(of: focusRequest) { _ in restoreFocus() }
    }

    private func restoreFocus() {
        focusedAction = onboarding.step == .jellyfin ? .connect : .file
    }

    private func card(jellyfin: Bool) -> some View {
        ScrollView {
            VStack(spacing: 0) {
                HStack {
                    LeafWordmark()
                    Spacer()
                }
                .padding(16)
                .background(Leaf.leafPrimary)

                Image(jellyfin ? "OnboardingJellyfin" : "OnboardingLeafDew")
                    .resizable()
                    .scaledToFit()
                    .frame(width: jellyfin ? 65 : 126, height: jellyfin ? 65 : 119)
                    .frame(maxWidth: .infinity, minHeight: 125)
                    .background(LinearGradient(colors: [Leaf.leafSecondary.opacity(0.16), settings.ground],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 12) {
                    Text(LocalizedStringKey(jellyfin ? "Bring your Jellyfin library" : "Open your first comic"))
                        .font(.custom("Anton-Regular", size: 29, relativeTo: .title))
                        .foregroundStyle(Leaf.text)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier(jellyfin ? "onboarding.jellyfinTitle" : "onboarding.welcomeTitle")
                    Text(LocalizedStringKey(jellyfin
                         ? "Connect your server to browse your collection in Leaf. Download comics to read them offline."
                         : "Choose a CBZ, CBR, or PDF from your device. Once imported, it’s ready to read offline."))
                        .leafControlFont(16, weight: 500)
                        .foregroundStyle(Leaf.secondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 6)

                    Button(action: jellyfin ? connect : chooseFile) {
                        Text(LocalizedStringKey(jellyfin ? "Connect Jellyfin" : "Choose a file"))
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 4)
                            .foregroundStyle(Leaf.ink)
                            .background(Leaf.ochre, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(busy)
                    .opacity(busy ? 0.6 : 1)
                    .accessibilityIdentifier(jellyfin ? "onboarding.connect" : "onboarding.chooseFile")
                    .accessibilityFocused($focusedAction, equals: jellyfin ? .connect : .file)

                    Button {
                        if jellyfin { onboarding.finish() } else { onboarding.showJellyfin() }
                    } label: {
                        HStack {
                            Text(LocalizedStringKey(jellyfin ? "Use without Jellyfin" : "I use Jellyfin"))
                            if !jellyfin { Image(systemName: "arrow.right").accessibilityHidden(true) }
                        }
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                        .foregroundStyle(Leaf.text)
                        .background(settings.ground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Leaf.secondaryText.opacity(0.35)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier(jellyfin ? "onboarding.withoutJellyfin" : "onboarding.useJellyfin")

                    if !jellyfin {
                        Button { onboarding.finish() } label: {
                            Text("Maybe later")
                                .frame(maxWidth: .infinity, minHeight: 46)
                                .contentShape(Rectangle())
                        }
                            .buttonStyle(.plain)
                            .foregroundStyle(Leaf.secondaryText)
                            .accessibilityIdentifier("onboarding.later")
                    }
                    if busy { ProgressView("Importing…").frame(maxWidth: .infinity) }
                }
                .leafControlFont(15, weight: 700)
                .padding(22)
            }
            .background(settings.ground)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Leaf.secondaryText.opacity(0.25)))
            .shadow(color: Leaf.leafPrimary.opacity(0.12), radius: 16, y: 8)
            .frame(maxWidth: 460)
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .background(settings.ground.ignoresSafeArea())
    }
}
