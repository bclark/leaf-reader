import SwiftUI
import ImageIO

struct JellyfinBrowserView: View {
    @EnvironmentObject private var settings: LeafSettings
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @StateObject var model = JellyfinBrowserModel()
    @ObservedObject var catalogue: JellyfinCatalogueModel
    var onConnected: (@MainActor () -> Void)? = nil
    @State private var server = ""
    @State private var username = ""
    @State private var password = ""
    @State private var allowHTTP = false
    @State private var didLoadConnectionForm = false
    @State private var openComic: OpenComic?
    @State private var selectedBook: JellyfinBook?
    @State private var showingDetails = false
    @Namespace private var readerNamespace

    var body: some View {
        NavigationStack {
            List {
                if model.connection == nil || model.needsSignIn {
                    Section("Jellyfin connection") {
                        TextField("Server URL", text: $server)
                            .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                        TextField("Username", text: $username)
                            .textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                        SecureField("Password", text: $password).textContentType(.password)
                        Toggle("Allow unencrypted local HTTP", isOn: $allowHTTP)
                        Text("HTTP exposes your password, token, and comics to others on the network. Use only with a trusted local server.")
                            .font(.footnote).foregroundStyle(.secondary)
                        Button("Sign in") {
                            model.signIn(url: server, username: username, password: password, allowHTTP: allowHTTP,
                                onSuccess: {
                                    password = ""
                                    onConnected?()
                                })
                        }
                        .disabled(model.busy || username.isEmpty || password.isEmpty)
                    }.disabled(model.busy)
                } else {
                    Section {
                        Text(model.connection?.username ?? "")
                        Text(model.connection?.baseURL.absoluteString ?? "").font(.footnote).foregroundStyle(.secondary)
                        Text("Downloads stay in your library for offline reading. Use Sync reading position on a downloaded comic to exchange progress with Jellyfin.")
                            .font(.footnote)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Last catalogue refresh").font(.caption).foregroundStyle(.secondary)
                            if let date = catalogue.lastCatalogueRefresh(for: model.connection) {
                                Text(date, format: .dateTime.year().month(.abbreviated).day().hour().minute())
                                    .font(.footnote)
                            } else {
                                Text("Not yet refreshed").font(.footnote)
                            }
                        }
                        .accessibilityElement(children: .combine)
                        Button("Disconnect", role: .destructive) { Task { await model.disconnect() } }.disabled(model.busy)
                    }
                    Section(model.folders.last?.name ?? String(localized: "Jellyfin libraries")) {
                        HStack {
                            Button("Up") { model.up() }.disabled(model.folders.isEmpty || model.busy)
                            Spacer()
                            Button("Refresh") { model.refresh() }.disabled(model.busy)
                        }
                        ForEach(model.items) { item in
                            JellyfinBookRow(item: item, client: model.client,
                                profileID: model.connection?.id ?? "", isLibrary: model.folders.isEmpty,
                                downloaded: model.downloaded[item.id] != nil,
                                isDisabled: model.busy,
                                activity: model.activeItemID == item.id ? model.activity : nil,
                                transferred: model.transferred, total: model.total,
                                cancel: { model.cancel() }) {
                                if item.isFolder == true || model.folders.isEmpty {
                                    model.enter(item)
                                } else {
                                    selectedBook = item
                                    showingDetails = true
                                }
                            }
                        }
                        if model.hasMore { Button("Load more") { model.more() }.disabled(model.busy) }
                        if model.items.isEmpty && !model.busy {
                            Text("No items in this folder.").foregroundStyle(.secondary)
                        }
                    }
                }
                if model.busy && model.activeItemID == nil {
                    Section {
                        JellyfinActivityView(activity: model.activity, transferred: model.transferred,
                            total: model.total, cancel: { password = ""; model.cancel() })
                    }
                }
                if let message = model.message {
                    Section {
                        Text(message)
                        if !model.needsSignIn { Button("Retry") { model.refresh() }.disabled(model.busy) }
                    }
                }
                if model.needsSignIn, model.connection != nil {
                    Button("Forget saved connection", role: .destructive) { Task { await model.disconnect() } }.disabled(model.busy)
                }
            }
            .scrollContentBackground(.hidden)
            .background(settings.ground)
            .tint(Leaf.goldText)
            .navigationTitle("Jellyfin")
            .navigationDestination(isPresented: $showingDetails) {
                if let selectedBook {
                    JellyfinDetailView(item: selectedBook, model: model)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        password = ""; model.cancel()
                        Task { await model.waitForIdle(); dismiss() }
                    }
                }
            }
            .interactiveDismissDisabled(model.busy)
            .task {
                // Restore once so reappearing does not overwrite an in-progress sign-in.
                if !didLoadConnectionForm {
                    server = model.connection?.baseURL.absoluteString ?? ""
                    username = model.connection?.username ?? ""
                    allowHTTP = model.connection?.allowsHTTP ?? false
                    didLoadConnectionForm = true
                    await model.restoreDownloads(library: library)
                    model.refresh()
                }
            }
            .onChange(of: model.connection?.id) { id in
                // Keep consent while editing; disconnecting starts a fresh connection.
                if id == nil { allowHTTP = false }
                Task { await model.restoreDownloads(library: library) }
            }
            .onChange(of: scenePhase) { phase in if phase == .background { password = ""; model.cancel() } }
            .onDisappear { password = ""; model.cancel() }
            .comicReaderPresentation(item: $openComic, namespace: readerNamespace) { library.refresh() }
        }
        .leafAppearance(settings.theme)
    }
}

struct JellyfinActivityView: View {
    let activity: String
    let transferred: Int64
    let total: Int64
    let cancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(activity).font(.subheadline)
            if total > 0 {
                ProgressView(value: min(1, Double(transferred) / Double(total)))
            } else {
                ProgressView()
            }
            if transferred > 0 {
                Text(ByteCountFormatter.string(fromByteCount: transferred, countStyle: .file))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Button("Cancel", role: .cancel, action: cancel)
                .buttonStyle(.borderless)
                .frame(minHeight: 44)
        }
    }
}

struct JellyfinBookRow: View {
    let item: JellyfinBook
    let client: JellyfinClient?
    let profileID: String
    let isLibrary: Bool
    let downloaded: Bool
    let isDisabled: Bool
    let activity: String?
    let transferred: Int64
    let total: Int64
    let cancel: () -> Void
    let action: () -> Void
    @State private var cover: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: action) {
                HStack(spacing: 12) {
                    Group {
                        if let cover { Image(uiImage: cover).resizable().scaledToFit() }
                        else { Image(systemName: item.isFolder == true || isLibrary ? "folder" : "book.closed") }
                    }
                    .frame(width: 44, height: 60).accessibilityHidden(true)
                    VStack(alignment: .leading) {
                        Text(item.name).foregroundStyle(Leaf.text)
                        if activity == nil && item.isFolder != true && !isLibrary {
                            Text(downloaded ? "Read offline" : "Details")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .frame(minHeight: 60)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
            if let activity {
                JellyfinActivityView(activity: activity, transferred: transferred, total: total, cancel: cancel)
            }
        }
        .task(id: profileID + item.id + (item.imageTags?["Primary"] ?? "")) {
            cover = nil
            guard let client, let data = try? await client.cover(item), !Task.isCancelled else { return }
            let thumbnail = await Task.detached(priority: .utility) { Self.thumbnail(data) }.value
            guard !Task.isCancelled else { return }
            cover = thumbnail
        }
    }
    nonisolated static func thumbnail(_ data: Data, maxPixel: Int = 160) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? NSNumber,
              let height = properties[kCGImagePropertyPixelHeight] as? NSNumber,
              width.doubleValue > 0, height.doubleValue > 0,
              width.doubleValue * height.doubleValue <= JellyfinImportLimits.pixels,
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: min(CoverResolution.maximum, max(160, maxPixel))
              ] as CFDictionary) else { return nil }
        return UIImage(cgImage: frontCoverImage(image))
    }
}
