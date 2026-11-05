import SwiftUI
import UniformTypeIdentifiers

/// The Leaf menu / about screen — the SwiftUI port of Android's MenuScreen.kt. Holds the AMOLED
/// theme toggle, reading mode switch, grid columns setting, and the about section.
struct MenuView: View {
    @EnvironmentObject private var settings: LeafSettings
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss

    @State private var showingSmbSettings = false
    @State private var showingExporter = false
    @State private var exportDocument = BackupJSONDocument()
    @State private var showingImporter = false
    @State private var pendingBackupPayload: BackupPayload?
    @State private var pendingPreview: BackupPreview?
    @State private var showingRestoreSheet = false
    @State private var selectedStrategy: BackupConflictStrategy = .mostRecent
    @State private var toastMessage: String?
    @State private var errorMessage: String?
    @State private var showingErrorAlert = false

    private static let repoURL = URL(string: "https://github.com/bclark/leaf-reader")!
    private static let upstreamURL = URL(string: "https://github.com/batunii/chika")!
    private static let privacyURL = URL(string: "https://github.com/bclark/leaf-reader/blob/main/PRIVACY.md")!
    private static let licensesURL = URL(string: "https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_NOTICES.md")!
    private static let kofiURL = URL(string: "https://ko-fi.com/bryanclark")!

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "0.3.2"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }

    private var defaultBackupFilename: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return "leaf_backup_\(formatter.string(from: Date()))"
    }

    var body: some View {
        ZStack {
            settings.ground.ignoresSafeArea()
                .sheet(isPresented: $showingSmbSettings) { SmbSettingsView() }

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
                        VStack(alignment: .leading, spacing: 16) {
                            // Page Fit
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Page Fit")
                                    .font(.archivo(14, weight: 700))
                                    .foregroundColor(Leaf.cream)
                                Text(settings.pageFitMode == .fitWidth
                                     ? "Fit page to screen width for easier reading of small text and speech bubbles."
                                     : "Fit the entire page on screen.")
                                    .font(.archivo(12, weight: 400))
                                    .foregroundColor(Leaf.creamMuted)

                                HStack(spacing: 2) {
                                    Button {
                                        settings.pageFitMode = .fitPage
                                    } label: {
                                        Text("FIT TO PAGE")
                                            .font(.archivo(12, weight: 700))
                                            .foregroundColor(settings.pageFitMode == .fitPage ? Leaf.ink : Leaf.creamMuted)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(settings.pageFitMode == .fitPage ? Leaf.ochre : Color.clear)
                                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                                    }
                                    .buttonStyle(.plain)

                                    Button {
                                        settings.pageFitMode = .fitWidth
                                    } label: {
                                        Text("FIT TO WIDTH")
                                            .font(.archivo(12, weight: 700))
                                            .foregroundColor(settings.pageFitMode == .fitWidth ? Leaf.ink : Leaf.creamMuted)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(settings.pageFitMode == .fitWidth ? Leaf.ochre : Color.clear)
                                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(2)
                                .background(Leaf.ink)
                                .clipShape(RoundedCornerShape(cornerRadius: 4))
                                .padding(.top, 4)
                            }

                            // Portrait layout
                            LayoutChoiceView(title: "Portrait layout", isSpread: $settings.portraitSpread)

                            // Landscape layout
                            LayoutChoiceView(title: "Landscape layout", isSpread: $settings.landscapeSpread)

                            Text("The cover and wide pages stay single in either layout.")
                                .font(.archivo(11, weight: 400))
                                .foregroundColor(Leaf.creamMuted)
                        }
                        .padding(16)
                        .background(Leaf.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        Spacer().frame(height: 32)

                        // ---- Library -----------------------------------------------------
                        Button("SMB Drive Settings") { showingSmbSettings = true }
                            .font(.archivo(14, weight: 700))
                            .foregroundColor(Leaf.ochre)
                            .padding(.bottom, 24)

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

                        // ---- Backup & Restore --------------------------------------------
                        SectionLabel("BACKUP & RESTORE")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Cross-Platform Backup")
                                .font(.archivo(14, weight: 700))
                                .foregroundColor(Leaf.cream)
                            Text("Export or restore your reading progress, metadata, custom reading lists, and app preferences. Backups are cross-platform compatible between iOS and Android.")
                                .font(.archivo(12, weight: 400))
                                .lineSpacing(3)
                                .foregroundColor(Leaf.creamMuted)

                            HStack(spacing: 12) {
                                Button {
                                    startExport()
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "square.and.arrow.up")
                                            .font(.system(size: 13, weight: .bold))
                                        Text("EXPORT BACKUP")
                                            .font(.archivo(12, weight: 700))
                                    }
                                    .foregroundColor(Leaf.ink)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Leaf.ochre)
                                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                                    .comicShadow(offset: 2, color: .black.opacity(0.5), corner: 4)
                                }
                                .buttonStyle(.plain)

                                Button {
                                    showingImporter = true
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "square.and.arrow.down")
                                            .font(.system(size: 13, weight: .bold))
                                        Text("RESTORE BACKUP")
                                            .font(.archivo(12, weight: 700))
                                    }
                                    .foregroundColor(Leaf.cream)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Leaf.ink)
                                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                                    .comicShadow(offset: 2, color: .black.opacity(0.5), corner: 4)
                                }
                                .buttonStyle(.plain)
                            }
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

                        // ---- Support Leaf ------------------------------------------------
                        SectionLabel("SUPPORT LEAF")
                        Spacer().frame(height: 10)
                        Text("Enjoying Leaf? You can leave an optional tip to support development. All features remain free.")
                            .font(.archivo(12, weight: 500))
                            .lineSpacing(4)
                            .foregroundColor(Leaf.cream)
                        Spacer().frame(height: 12)
                        Button {
                            UIApplication.shared.open(Self.kofiURL)
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Leaf.cream)
                                Text("TIP ON KO-FI")
                                    .font(.anton(15))
                                    .tracking(0.5)
                                    .foregroundColor(Leaf.cream)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Leaf.crimson)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .comicShadow(offset: 4, color: .black.opacity(0.6), corner: 4)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Tip on Ko-fi")
                        .accessibilityHint("Opens ko-fi.com/bryanclark in external web browser")

                        Spacer().frame(height: 6)
                        Link(destination: Self.kofiURL) {
                            Text("ko-fi.com/bryanclark")
                                .font(.archivo(11, weight: 600))
                                .foregroundColor(Leaf.ochre)
                        }

                        Spacer().frame(height: 24)

                        // ---- Support Upstream --------------------------------------------
                        Text("Support the upstream creator of Chika:")
                            .font(.archivo(12, weight: 500))
                            .lineSpacing(3)
                            .foregroundColor(Leaf.creamMuted)
                        Spacer().frame(height: 12)
                        Link(destination: Self.upstreamURL) {
                            HStack(spacing: 10) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Leaf.ochre)
                                Text("SUPPORT ORIGINAL PROJECT")
                                    .font(.anton(15))
                                    .tracking(0.5)
                                    .foregroundColor(Leaf.cream)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Leaf.deepInkSurface)
                            .overlay(
                                RoundedRectangle(cornerRadius: 4)
                                    .stroke(Leaf.ochre, lineWidth: 1.5)
                            )
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .comicShadow(offset: 4, color: .black.opacity(0.6), corner: 4)
                        }
                        Spacer().frame(height: 6)
                        Link(destination: Self.upstreamURL) {
                            Text("github.com/batunii/chika")
                                .font(.archivo(11, weight: 600))
                                .foregroundColor(Leaf.ochre)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 28)
                }
            }

            // Floating Toast Message
            if let toast = toastMessage {
                VStack {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(Leaf.ochre)
                        Text(toast)
                            .font(.archivo(13, weight: 600))
                            .foregroundColor(Leaf.cream)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Leaf.inkSoft)
                    .clipShape(Capsule())
                    .comicShadow(offset: 4, color: .black.opacity(0.6), corner: 20)
                    .padding(.top, 60)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(100)
            }
        }
        .navigationBarHidden(true)
        .fileExporter(
            isPresented: $showingExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: defaultBackupFilename
        ) { result in
            switch result {
            case .success:
                showToast("✓ Backup exported successfully")
            case .failure(let error):
                errorMessage = "Export failed: \(error.localizedDescription)"
                showingErrorAlert = true
            }
        }
        .fileImporter(
            isPresented: $showingImporter,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else {
                    errorMessage = "Could not access selected file."
                    showingErrorAlert = true
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                do {
                    let jsonString = try String(contentsOf: url, encoding: .utf8)
                    let payload = try BackupManager.parseAndValidate(jsonString: jsonString)
                    let preview = BackupManager.createPreview(
                        payload: payload,
                        localComics: library.comics,
                        metadata: library.metadata
                    )
                    self.pendingBackupPayload = payload
                    self.pendingPreview = preview
                    self.selectedStrategy = .mostRecent
                    self.showingRestoreSheet = true
                } catch {
                    errorMessage = error.localizedDescription
                    showingErrorAlert = true
                }
            case .failure(let error):
                errorMessage = "Failed to open file: \(error.localizedDescription)"
                showingErrorAlert = true
            }
        }
        .sheet(isPresented: $showingRestoreSheet) {
            if let preview = pendingPreview, let payload = pendingBackupPayload {
                RestorePreviewSheet(
                    preview: preview,
                    payload: payload,
                    selectedStrategy: $selectedStrategy,
                    onApply: {
                        var meta = library.metadata
                        let result = BackupManager.applyBackup(
                            payload: payload,
                            strategy: selectedStrategy,
                            localComics: library.comics,
                            metadata: &meta,
                            settings: settings
                        )
                        library.refresh()
                        showingRestoreSheet = false
                        showToast("✓ Restored \(result.restoredComics) comics · \(result.restoredLists) lists")
                    },
                    onCancel: {
                        showingRestoreSheet = false
                    }
                )
                .environmentObject(settings)
            }
        }
        .alert("Backup Error", isPresented: $showingErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "An unknown error occurred.")
        }
    }

    private func startExport() {
        let json = BackupManager.exportToJson(
            comics: library.comics,
            metadata: library.metadata,
            readingLists: library.readingLists,
            settings: settings,
            appVersion: version
        )
        exportDocument = BackupJSONDocument(json: json)
        showingExporter = true
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            withAnimation {
                if toastMessage == msg {
                    toastMessage = nil
                }
            }
        }
    }
}

// MARK: - Backup Document & Restore Preview Sheet

struct BackupJSONDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var json: String

    init(json: String = "") {
        self.json = json
    }

    init(configuration: ReadConfiguration) throws {
        if let data = configuration.file.regularFileContents,
           let str = String(data: data, encoding: .utf8) {
            json = str
        } else {
            json = ""
        }
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let data = json.data(using: .utf8) ?? Data()
        return FileWrapper(regularFileWithContents: data)
    }
}

private struct RestorePreviewSheet: View {
    let preview: BackupPreview
    let payload: BackupPayload
    @Binding var selectedStrategy: BackupConflictStrategy
    let onApply: () -> Void
    let onCancel: () -> Void
    @EnvironmentObject private var settings: LeafSettings

    private var formattedExportDate: String {
        let date = Date(timeIntervalSince1970: Double(preview.exportedAt) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    var body: some View {
        ZStack {
            settings.ground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Sheet Header
                HStack {
                    OchreBadge(text: "RESTORE BACKUP (v\(preview.version))")
                    Spacer()
                    Button {
                        onCancel()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Leaf.cream)
                            .frame(width: 32, height: 32)
                            .background(Leaf.cream.opacity(0.12))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 14)

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        // Timestamp & App Version
                        HStack(spacing: 8) {
                            Image(systemName: "clock")
                                .font(.system(size: 12))
                                .foregroundColor(Leaf.ochre)
                            Text("Exported \(formattedExportDate)")
                                .font(.archivo(12, weight: 600))
                                .foregroundColor(Leaf.cream)
                            Spacer()
                            Text("v\(preview.appVersion)")
                                .font(.archivo(11, weight: 600))
                                .foregroundColor(Leaf.creamMuted)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Leaf.inkSoft)
                                .clipShape(RoundedCornerShape(cornerRadius: 3))
                        }
                        .padding(12)
                        .background(Leaf.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        // Backup Contents Summary
                        VStack(alignment: .leading, spacing: 10) {
                            Text("BACKUP CONTENTS")
                                .font(.anton(13))
                                .tracking(0.5)
                                .foregroundColor(Leaf.ochre)

                            VStack(alignment: .leading, spacing: 8) {
                                HStack(spacing: 8) {
                                    Image(systemName: "books.vertical.fill")
                                        .font(.system(size: 13))
                                        .foregroundColor(Leaf.creamMuted)
                                        .frame(width: 20)
                                    Text("\(preview.totalComicsInBackup) comics in backup")
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.cream)
                                    if preview.matchingComicsCount > 0 {
                                        Text("(\(preview.matchingComicsCount) match library)")
                                            .font(.archivo(12, weight: 400))
                                            .foregroundColor(Leaf.ochre)
                                    }
                                }

                                HStack(spacing: 8) {
                                    Image(systemName: "list.bullet.rectangle.fill")
                                        .font(.system(size: 13))
                                        .foregroundColor(Leaf.creamMuted)
                                        .frame(width: 20)
                                    Text("\(preview.readingListsCount) reading lists")
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.cream)
                                }

                                HStack(spacing: 8) {
                                    Image(systemName: "slider.horizontal.3")
                                        .font(.system(size: 13))
                                        .foregroundColor(Leaf.creamMuted)
                                        .frame(width: 20)
                                    Text(preview.hasSettings ? "Includes app preferences" : "No preferences included")
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.cream)
                                }
                            }
                        }
                        .padding(16)
                        .background(Leaf.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        // Strategy Selector
                        VStack(alignment: .leading, spacing: 10) {
                            Text("CONFLICT RESOLUTION")
                                .font(.anton(13))
                                .tracking(0.5)
                                .foregroundColor(Leaf.ochre)

                            VStack(spacing: 8) {
                                ForEach(BackupConflictStrategy.allCases) { strategy in
                                    Button {
                                        selectedStrategy = strategy
                                    } label: {
                                        HStack(alignment: .top, spacing: 12) {
                                            Image(systemName: selectedStrategy == strategy ? "record.circle.fill" : "circle")
                                                .font(.system(size: 16))
                                                .foregroundColor(selectedStrategy == strategy ? Leaf.ochre : Leaf.creamMuted)
                                                .padding(.top, 2)
                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(strategy.title)
                                                    .font(.archivo(13, weight: 700))
                                                    .foregroundColor(Leaf.cream)
                                                Text(strategy.subtitle)
                                                    .font(.archivo(11, weight: 400))
                                                    .foregroundColor(Leaf.creamMuted)
                                            }
                                            Spacer()
                                        }
                                        .padding(12)
                                        .background(selectedStrategy == strategy ? Leaf.ink.opacity(0.8) : Leaf.inkSoft)
                                        .clipShape(RoundedCornerShape(cornerRadius: 6))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 6)
                                                .stroke(selectedStrategy == strategy ? Leaf.ochre : Color.clear, lineWidth: 1.5)
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        // Action Buttons
                        VStack(spacing: 10) {
                            Button {
                                onApply()
                            } label: {
                                Text("APPLY RESTORE")
                                    .font(.anton(15))
                                    .tracking(0.5)
                                    .foregroundColor(Leaf.ink)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Leaf.ochre)
                                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                                    .comicShadow(offset: 4, color: .black.opacity(0.6), corner: 4)
                            }
                            .buttonStyle(.plain)

                            Button {
                                onCancel()
                            } label: {
                                Text("CANCEL")
                                    .font(.archivo(13, weight: 700))
                                    .foregroundColor(Leaf.creamMuted)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.top, 8)
                    }
                    .padding(20)
                }
            }
        }
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

/// Two-option layout selector (Single page vs Two-page spread), matching Android's LayoutChoice.
private struct LayoutChoiceView: View {
    let title: String
    @Binding var isSpread: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.archivo(14, weight: 700))
                .foregroundColor(Leaf.cream)
            HStack(spacing: 2) {
                Button {
                    isSpread = false
                } label: {
                    Text("Single page")
                        .font(.archivo(12, weight: 700))
                        .foregroundColor(!isSpread ? Leaf.ink : Leaf.creamMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(!isSpread ? Leaf.ochre : Color.clear)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                }
                .buttonStyle(.plain)

                Button {
                    isSpread = true
                } label: {
                    Text("Two-page spread")
                        .font(.archivo(12, weight: 700))
                        .foregroundColor(isSpread ? Leaf.ink : Leaf.creamMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(isSpread ? Leaf.ochre : Color.clear)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                }
                .buttonStyle(.plain)
            }
            .padding(2)
            .background(Leaf.ink)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .padding(.top, 4)
        }
    }
}
