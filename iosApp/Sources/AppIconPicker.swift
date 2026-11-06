import SwiftUI

struct AppIconPicker: View {
    @State private var selection = UIApplication.shared.alternateIconName ?? ""
    @State private var changing = false
    @State private var errorMessage: String?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("App Icon").font(.archivo(14, weight: 700)).foregroundColor(Leaf.text)
            Picker("App Icon", selection: Binding(
                get: { selection },
                set: { changeIcon(to: $0) }
            )) {
                Text("Automatic").tag("")
                Text("Light").tag("AppIconLight")
                Text("Dark").tag("AppIconDark")
            }
            .pickerStyle(.segmented)
            .disabled(changing || !UIApplication.shared.supportsAlternateIcons)
            Text("Home Screen appearance and tint settings can also affect your icon.")
                .font(.archivo(12)).foregroundColor(Leaf.secondaryText)
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { selection = UIApplication.shared.alternateIconName ?? "" }
        }
        .alert("Could Not Change Icon", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK") { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func changeIcon(to name: String) {
        guard !changing, UIApplication.shared.supportsAlternateIcons else { return }
        changing = true
        UIApplication.shared.setAlternateIconName(name.isEmpty ? nil : name) { error in
            let message = error?.localizedDescription
            Task { @MainActor in
                selection = UIApplication.shared.alternateIconName ?? ""
                errorMessage = message
                changing = false
            }
        }
    }
}
