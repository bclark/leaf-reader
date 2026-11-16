import SwiftUI

private struct TipPromptPolicyKey: EnvironmentKey {
    static let defaultValue: TipPromptPolicy? = nil
}
private struct TipPromptAllowedKey: EnvironmentKey {
    static let defaultValue = true
}
extension EnvironmentValues {
    var tipPromptPolicy: TipPromptPolicy? {
        get { self[TipPromptPolicyKey.self] }
        set { self[TipPromptPolicyKey.self] = newValue }
    }
    var tipPromptAllowed: Bool {
        get { self[TipPromptAllowedKey.self] }
        set { self[TipPromptAllowedKey.self] = newValue }
    }
}

@MainActor
extension TipPromptPolicy {
    /// A saved-history scan, opening details, and an already completed reread are not completion events.
    func readerDidClose(_ comic: OpenComic, canPresent: Bool) -> UUID? {
        guard !comic.wasCompletedAtOpen, ReadingProgress.isCompleted(comic.url) else { return nil }
        recordCompletion(itemID: comic.url.lastPathComponent)
        // The bounded counter can already be full after a cooldown. A new completion is
        // still an appropriate opportunity; it need not increment that counter again.
        return canPresent ? claimPresentation() : nil
    }
}

struct ContextualTipReaderPresentation: ViewModifier {
    @Binding var item: OpenComic?
    let namespace: Namespace.ID
    let onDismiss: () -> Void
    @ObservedObject var policy: TipPromptPolicy
    @Environment(\.scenePhase) private var phase
    @Environment(\.tipPromptAllowed) private var allowed
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var opened: OpenComic?
    private struct Offer: Identifiable { let id: UUID }
    @State private var offer: Offer?
    @State private var claimedID: UUID?
    @State private var showJarAfterPrompt = false
    @State private var showJar = false

    func body(content: Content) -> some View {
        content.leafReaderCover(item: $item, namespace: namespace, onDismiss: readerDismissed)
            .onAppear { captureOpening() }
            .onChange(of: item?.id) { _ in captureOpening() }
            .sheet(item: $offer, onDismiss: promptDismissed) { _ in
                ContextualTipPrompt(tip: {
                    showJarAfterPrompt = true
                    offer = nil
                }, notNow: { offer = nil }, never: {
                    showJarAfterPrompt = false
                    policy.optOut()
                    offer = nil
                })
                .presentationDetents(typeSize.isAccessibilitySize ? [.large] : [.medium, .large])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $showJar) { TipJarView() }
            .onChange(of: policy.presentationID) { id in
                if let current = offer?.id, current != id { offer = nil }
            }
            .onChange(of: phase) { if $0 != .active { cancelOffer() } }
            .onChange(of: allowed) { if !$0 { cancelOffer() } }
            .onDisappear { cancelOffer() }
    }

    private func captureOpening() { if let item { opened = item } }

    private func readerDismissed() {
        let finished = opened
        opened = nil
        onDismiss()
        guard let finished else { return }
        if let id = policy.readerDidClose(finished,
            canPresent: phase == .active && allowed && item == nil && offer == nil && !showJar) {
            claimedID = id
            offer = Offer(id: id)
        }
    }

    private func promptDismissed() {
        if let claimedID { policy.dismiss(claimedID) }
        claimedID = nil
        guard showJarAfterPrompt else { return }
        showJarAfterPrompt = false
        if phase == .active && allowed && item == nil { showJar = true }
    }

    private func cancelOffer() {
        showJarAfterPrompt = false
        if let claimedID { policy.dismiss(claimedID) }
        claimedID = nil
        offer = nil
    }
}

private struct ContextualTipPrompt: View {
    @EnvironmentObject private var settings: LeafSettings
    let tip: () -> Void
    let notNow: () -> Void
    let never: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Enjoying Leaf?")
                    .font(.custom("Anton-Regular", size: 30, relativeTo: .title))
                    .foregroundStyle(Leaf.text).accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("tipPrompt.title")
                Text("Leave an optional tip to support Leaf’s development. All features remain free. Tips are one-time purchases and unlock nothing.")
                    .font(.custom("Archivo", size: 17, relativeTo: .body))
                    .foregroundStyle(Leaf.secondaryText)
                VStack(spacing: 10) {
                    Button(action: tip) {
                        Text("Tip").leafControlFont(17, weight: 700)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .foregroundStyle(Leaf.ink)
                            .background(Leaf.ochre, in: RoundedRectangle(cornerRadius: 12))
                    }.accessibilityIdentifier("tipPrompt.tip")
                    Button(action: notNow) {
                        Text("Not now").leafControlFont(17, weight: 600)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .foregroundStyle(Leaf.text)
                            .background(settings.groundSoft, in: RoundedRectangle(cornerRadius: 12))
                    }.accessibilityIdentifier("tipPrompt.notNow")
                    Button(action: never) {
                        Text("Don't ask again").leafControlFont(15, weight: 500)
                            .frame(maxWidth: .infinity, minHeight: 48).contentShape(Rectangle())
                            .foregroundStyle(Leaf.secondaryText)
                    }.accessibilityIdentifier("tipPrompt.never")
                }.buttonStyle(.plain)
            }
            .frame(maxWidth: 540, alignment: .leading).padding(24)
            .frame(maxWidth: .infinity)
        }
        .background(settings.ground.ignoresSafeArea())
        .leafAppearance(settings.theme)
    }
}
