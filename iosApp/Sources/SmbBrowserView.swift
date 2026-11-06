import SwiftUI
import LeafShared

struct SmbBrowserView: View {
    @EnvironmentObject private var settings: LeafSettings
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model = SmbBrowserModel()
    @State private var showingSettings = false
    @State private var collisionChoice = 0

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(model.state.path.isEmpty ? "Drive folder" : model.state.path)
                        .font(.archivo(15, weight: 700))
                    HStack {
                        Button("Up") { model.up() }.disabled(model.state.path.isEmpty || model.busy)
                        Spacer()
                        Button("Refresh") { model.navigate(model.state.path) }.disabled(model.busy)
                    }
                }
                Section {
                    HStack {
                        Button("Select All") { model.selectAll() }
                        Spacer()
                        Button("Clear") { model.clearSelection() }
                    }
                    Picker("Existing comics", selection: $collisionChoice) {
                        Text("Keep Both").tag(0)
                        Text("Replace").tag(1)
                        Text("Skip").tag(2)
                    }
                    Button("Import Selected (\(model.state.selectedPaths.count))") {
                        model.importSelected(library: library,
                            collision: collisionChoice == 0 ? .keepBoth : (collisionChoice == 1 ? .replace : .cancel))
                    }
                    .disabled(model.state.selectedPaths.isEmpty)
                }
                .disabled(model.busy)
                if let message = model.message {
                    Section {
                        Text(message)
                        Button("Retry") { model.retry() }.disabled(model.busy)
                        Button("Open App Settings") {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                    }
                }
                if model.busy {
                    Section {
                        if let fraction = model.fraction { ProgressView(value: fraction) }
                        else { ProgressView() }
                        Text(model.cancelling ? "Cancelling and cleaning up…" : (model.activity.isEmpty ? "Loading folder…" : model.activity))
                        if !model.progressDetail.isEmpty { Text(model.progressDetail).font(.caption) }
                        Button("Cancel", role: .cancel) { model.cancel() }.disabled(model.cancelling)
                    }
                }
                Section {
                    ForEach(model.state.entries, id: \.path) { entry in
                        Button {
                            if entry.isDirectory { model.navigate(entry.path) }
                            else { model.toggle(entry) }
                        } label: {
                            HStack {
                                Image(systemName: entry.isDirectory ? "folder" : (model.state.selectedPaths.contains(entry.path) ? "checkmark.circle.fill" : "circle"))
                                Text(entry.name).foregroundStyle(Leaf.text)
                                Spacer()
                                if entry.isDirectory { Image(systemName: "chevron.right") }
                            }
                            .padding(.vertical, 4)
                        }
                        .disabled(model.busy)
                        .accessibilityAddTraits(model.state.selectedPaths.contains(entry.path) ? .isSelected : [])
                    }
                    if model.state.entries.isEmpty && !model.busy {
                        Text("No supported comics or folders to display.").foregroundStyle(.secondary)
                    }
                }
                if let summary = model.summary {
                    Section("Import Summary") {
                        Text("\(summary.imported) imported · \(summary.failures.count) failed · \(summary.unprocessed) unprocessed")
                        ForEach(summary.failures) { failure in
                            VStack(alignment: .leading) {
                                Text(failure.name)
                                Text(failure.message).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(settings.ground)
            .tint(Leaf.goldText)
            .navigationTitle("SMB Drive")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") {
                    model.cancel()
                    Task { await model.waitForIdle(); dismiss() }
                }.disabled(model.cancelling) }
                ToolbarItem(placement: .primaryAction) { Button("Connection") { showingSettings = true }.disabled(model.busy) }
            }
            .sheet(isPresented: $showingSettings, onDismiss: { model.configure(SmbProfile.load()) }) {
                SmbSettingsView()
            }
            .task {
                let profile = SmbProfile.load()
                model.configure(profile)
                if profile == nil { showingSettings = true }
            }
            .interactiveDismissDisabled(model.busy)
            .onDisappear { model.cancel() }
            .onChange(of: scenePhase) { phase in if phase == .background { model.cancel() } }
        }
        .leafAppearance(settings.theme)
    }
}
