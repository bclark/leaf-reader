import SwiftUI
import LeafShared

struct SmbSettingsView: View {
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var profile = SmbProfile.load() ?? SmbProfile()
    @State private var password = ""
    @State private var busy = false
    @State private var message: String?
    @State private var task: Task<Void, Never>?
    var onSaved: () -> Void = {}

    var body: some View {
        NavigationStack {
            Form {
                Section("Connection") {
                    TextField("Server (optional :port)", text: $profile.host)
                        .keyboardType(.URL)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Share / optional base folder")
                            .font(.subheadline)
                            .fixedSize(horizontal: false, vertical: true)
                        TextField("", text: $profile.share)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityLabel(Text("Share / optional base folder"))
                    }
                    TextField("Username (optional DOMAIN\\user)", text: $profile.username)
                    SecureField("Password", text: $password)
                }
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .disabled(busy)
                Section {
                    Button("Test Connection") { test() }.disabled(busy)
                    Button("Save Connection") { save() }.disabled(busy)
                    Button("Forget Connection", role: .destructive) { forget() }.disabled(busy)
                } footer: {
                    Text("Leaf connects only when you test or browse. Imported comics stay available offline. Passwords are stored in this device’s Keychain.")
                }
                if busy { ProgressView("Checking connection…") }
                if let message {
                    Section {
                        Text(message)
                        Button("Open App Settings") {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(settings.ground)
            .tint(Leaf.goldText)
            .navigationTitle("SMB Drive")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() }.disabled(busy) } }
            .interactiveDismissDisabled(busy)
            .onAppear {
                // Keychain can outlive an uninstall; do not reuse an orphaned secret for a new server.
                guard SmbProfile.load() != nil else { return }
                do { password = try SmbKeychainCredentials().password(profileID: SmbProfile.credentialID) ?? "" }
                catch { message = String(localized: "Could not read the saved password. Unlock your device and retry.") }
            }
            .onDisappear { task?.cancel(); password = "" }
            .onChange(of: scenePhase) { phase in if phase == .background { task?.cancel() } }
        }
        .leafAppearance(settings.theme)
    }

    private func save() {
        do {
            try profile.save(password: password)
            onSaved()
            dismiss()
        } catch { message = String(localized: "Could not save. Check the server, port, share and account fields, and unlock your device.") }
    }

    private func forget() {
        do {
            try SmbProfile.forget()
            profile = SmbProfile()
            password = ""
            onSaved()
            message = String(localized: "Connection and saved password removed.")
        } catch { message = String(localized: "Could not remove the saved password. Unlock your device and retry.") }
    }

    private func test() {
        let snapshot = profile
        let secret = password
        busy = true
        message = nil
        task = Task { @MainActor in
            defer { busy = false; task = nil }
            do {
                let config = try snapshot.configuration()
                let connection = try snapshot.connection()
                // Unsaved form credentials stay in memory and never overwrite the saved account.
                let credentials = SmbTestCredentials(secret: secret)
                let transport = SmbTransport(credentials: credentials,
                    factory: { try NativeSmbSession(connection: $0, password: $1) })
                _ = try await transport.list(connection, path: config.basePath)
                message = String(localized: "Connected. The configured folder is accessible.")
            } catch is CancellationError { message = String(localized: "Connection test cancelled.") }
            catch { message = SmbTransportError.map(error).localizedDescription }
        }
    }
}

private struct SmbTestCredentials: SmbCredentialStore {
    let secret: String
    func password(profileID: String) throws -> String? { secret }
    func save(password: String, profileID: String) throws { throw SmbTransportError.io }
    func delete(profileID: String) throws { throw SmbTransportError.io }
}
