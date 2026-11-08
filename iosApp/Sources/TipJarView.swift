import SwiftUI

struct TipJarView: View {
    @EnvironmentObject private var tips: TipStore
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dismiss) private var dismiss
    @State private var confirmAnotherTip = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text("Leave an optional tip to support Leaf’s development. All features remain free. Tips are one-time purchases and unlock nothing.")
                        .font(.custom("Archivo", size: 17, relativeTo: .body))
                        .foregroundStyle(Leaf.text)

                    if let notice = tips.notice {
                        feedback(notice)
                            .font(.custom("Archivo", size: 17, relativeTo: .body))
                            .foregroundStyle(Leaf.text)
                            .accessibilityAddTraits(.updatesFrequently)
                    }

                    if tips.pendingProductID != nil {
                        Button("Check purchase status") { tips.recover() }
                        Button("Make another tip") { confirmAnotherTip = true }
                            .confirmationDialog(
                                "Your previous tip may still complete. Making another tip is a separate purchase.",
                                isPresented: $confirmAnotherTip,
                                titleVisibility: .visible
                            ) {
                                Button("Choose another tip") { tips.allowAnotherTip() }
                                Button("Keep waiting", role: .cancel) {}
                            }
                    }

                    switch tips.catalogState {
                    case .idle, .loading:
                        ProgressView("Loading tips…")
                    case .unavailable:
                        Text("Tips are unavailable right now. You can keep using Leaf for free.")
                            .foregroundStyle(Leaf.secondaryText)
                        Button("Retry") { Task { await tips.loadProducts() } }
                    case .ready:
                        ForEach(tips.products) { product in
                            Button {
                                tips.purchase(product)
                            } label: {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(verbatim: product.name)
                                        .font(.custom("Archivo", size: 20, relativeTo: .headline).weight(.semibold))
                                    Text(verbatim: product.price)
                                        .font(.custom("Archivo", size: 22, relativeTo: .title2).weight(.bold))
                                    Text(tips.purchasingID == product.id ? String(localized: "Purchasing…") : String(localized: "One-time tip"))
                                        .font(.custom("Archivo", size: 15, relativeTo: .subheadline))
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(20)
                                .foregroundStyle(Leaf.text)
                                .background(settings.groundSoft)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Leaf.ochre, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                            .disabled(tips.purchasingID != nil || tips.pendingProductID != nil)
                            .accessibilityElement(children: .combine)
                            .accessibilityIdentifier(product.id)
                        }
                    }
                }
                .frame(maxWidth: 540)
                .frame(maxWidth: .infinity)
                .padding(24)
            }
            .background(settings.ground)
            .navigationTitle("Support Leaf")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .tint(Leaf.goldText)
        .leafAppearance(settings.theme)
        .task { await tips.loadProducts() }
        .onDisappear { tips.clearNotice() }
    }

    @ViewBuilder
    private func feedback(_ notice: TipStore.Notice) -> some View {
        switch notice {
        case .thankYou:
            Text("Thank you for supporting Leaf!")
        case .pending:
            Text("Your tip is waiting for Apple’s approval or payment completion. You can close this screen and keep using Leaf.")
        case .unconfirmed:
            Text("Leaf couldn’t confirm the tip. Check your Apple purchase history before trying again. A payment may still complete.")
        case .restricted:
            Text("Purchases aren’t available on this device right now. You can keep using Leaf for free.")
        }
    }
}
