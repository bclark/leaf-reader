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
    @State private var showingTips = false
    @State private var toastMessage: String?
    @State private var errorMessage: String?
    @State private var showingErrorAlert = false

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
                        SectionLabel("APPEARANCE")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 16) {
                            Picker("App Theme", selection: $settings.theme) {
                                ForEach(AppTheme.allCases) { mode in
                                    Text(mode.title).tag(mode)
                                }
                            }
                            .pickerStyle(.segmented)
                            if settings.theme != .light {
                                ToggleRow(
                                    title: "AMOLED Black",
                                    subtitle: "Use true black when the app is in dark mode.",
                                    isOn: $settings.amoled
                                )
                            }
                            AppIconPicker()
                        }

                        Spacer().frame(height: 32)

                        // ---- Reading -----------------------------------------------------
                        SectionLabel("READING")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 16) {
                            // Page Fit
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Page Fit")
                                    .font(.archivo(14, weight: 700))
                                    .foregroundColor(Leaf.text)
                                Text(settings.pageFitMode == .fitWidth
                                     ? String(localized: "Fit page to screen width for easier reading of small text and speech bubbles.")
                                     : String(localized: "Fit the entire page on screen."))
                                    .font(.archivo(12, weight: 400))
                                    .foregroundColor(Leaf.secondaryText)

                                HStack(spacing: 2) {
                                    Button {
                                        settings.pageFitMode = .fitPage
                                    } label: {
                                        Text("FIT TO PAGE")
                                            .font(.archivo(12, weight: 700))
                                            .foregroundColor(settings.pageFitMode == .fitPage ? Leaf.ink : Leaf.secondaryText)
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
                                            .foregroundColor(settings.pageFitMode == .fitWidth ? Leaf.ink : Leaf.secondaryText)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(settings.pageFitMode == .fitWidth ? Leaf.ochre : Color.clear)
                                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(2)
                                .background(settings.ground)
                                .clipShape(RoundedCornerShape(cornerRadius: 4))
                                .padding(.top, 4)
                            }

                            // Portrait layout
                            LayoutChoiceView(title: "Portrait layout", isSpread: $settings.portraitSpread)

                            // Landscape layout
                            LayoutChoiceView(title: "Landscape layout", isSpread: $settings.landscapeSpread)

                            Text("The cover and wide pages stay single in either layout.")
                                .font(.archivo(11, weight: 400))
                                .foregroundColor(Leaf.secondaryText)
                        }
                        .padding(16)
                        .background(settings.groundSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        Spacer().frame(height: 32)

                        // ---- Library -----------------------------------------------------
                        Button("SMB Drive Settings") { showingSmbSettings = true }
                            .font(.archivo(14, weight: 700))
                            .foregroundColor(Leaf.goldText)
                            .padding(.bottom, 24)

                        SectionLabel("LIBRARY")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Grid Columns")
                                .font(.archivo(14, weight: 700))
                                .foregroundColor(Leaf.text)
                            Text("Choose how many comic covers appear per row on the library screen.")
                                .font(.archivo(12, weight: 400))
                                .foregroundColor(Leaf.secondaryText)

                            HStack(spacing: 2) {
                                ForEach([2, 3, 4, 6], id: \.self) { count in
                                    let isSelected = settings.libraryColumns == count
                                    Button {
                                        settings.libraryColumns = count
                                    } label: {
                                        Text("\(count) COL")
                                            .font(.archivo(12, weight: 700))
                                            .foregroundColor(isSelected ? Leaf.ink : Leaf.secondaryText)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(isSelected ? Leaf.ochre : Color.clear)
                                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(2)
                            .background(settings.ground)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .padding(.top, 4)
                        }
                        .padding(16)
                        .background(settings.groundSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        Spacer().frame(height: 32)

                        // ---- Backup & Restore --------------------------------------------
                        SectionLabel("BACKUP & RESTORE")
                        Spacer().frame(height: 10)
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Cross-Platform Backup")
                                .font(.archivo(14, weight: 700))
                                .foregroundColor(Leaf.text)
                            Text("Export or restore your reading progress, metadata, custom reading lists, and app preferences. Backups are cross-platform compatible between iOS and Android.")
                                .font(.archivo(12, weight: 400))
                                .lineSpacing(3)
                                .foregroundColor(Leaf.secondaryText)

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
                                    .foregroundColor(Leaf.text)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(settings.ground)
                                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                                    .comicShadow(offset: 2, color: .black.opacity(0.5), corner: 4)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.top, 4)
                        }
                        .padding(16)
                        .background(settings.groundSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        Spacer().frame(height: 32)

                        // ---- About -------------------------------------------------------
                        SectionLabel("ABOUT")
                        Spacer().frame(height: 16)
                        LeafWordmark(color: Leaf.text)
                        Spacer().frame(height: 12)
                        OchreBadge(text: "VERSION \(version)")
                        Spacer().frame(height: 14)
                        Text("A comic reader for iOS")
                            .font(.archivo(14, weight: 600)).foregroundColor(Leaf.text)
                        Spacer().frame(height: 14)
                        Text("Leaf is an offline comic reader with interactive page turns, reading lists, and support for CBZ, CBR, and PDF files.")
                            .font(.archivo(13)).lineSpacing(4).foregroundColor(Leaf.text)
                        Spacer().frame(height: 14)
                        Link(destination: Self.repoURL) {
                            Text("Source and support (English): github.com/bclark/leaf-reader")
                                .font(.archivo(11, weight: 600)).foregroundColor(Leaf.goldText)
                                .padding(.top, 2)
                        }
                        Spacer().frame(height: 10)
                        Text("Forked with appreciation from Chika by Chakra.")
                            .font(.archivo(12)).foregroundColor(Leaf.secondaryText)
                        Spacer().frame(height: 6)
                        Link(destination: Self.upstreamURL) {
                            Text("Upstream: github.com/batunii/chika")
                                .font(.archivo(11, weight: 600)).foregroundColor(Leaf.secondaryText)
                                .padding(.top, 2)
                        }

                        Spacer().frame(height: 18)
                        Link(destination: Self.privacyURL) {
                            HStack {
                                Text("Privacy policy (English) — Leaf collects no data.")
                                    .font(.archivo(12)).foregroundColor(Leaf.text)
                                Spacer()
                                Image(systemName: "arrow.up.right")
                                    .font(.system(size: 11, weight: .bold)).foregroundColor(Leaf.secondaryText)
                            }
                            .padding(12)
                            .background(settings.groundSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                        }
                        Spacer().frame(height: 8)
                        Link(destination: Self.licensesURL) {
                            HStack {
                                Text("Open-source licenses & notices (English).")
                                    .font(.archivo(12)).foregroundColor(Leaf.text)
                                Spacer()
                                Image(systemName: "arrow.up.right")
                                    .font(.system(size: 11, weight: .bold)).foregroundColor(Leaf.secondaryText)
                            }
                            .padding(12)
                            .background(settings.groundSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                        }

                        Spacer().frame(height: 24)

                        // ---- Support Leaf ------------------------------------------------
                        SectionLabel("SUPPORT LEAF")
                        Spacer().frame(height: 10)
                        Text("Enjoying Leaf? You can leave an optional tip to support development. All features remain free.")
                            .font(.archivo(12, weight: 500))
                            .lineSpacing(4)
                            .foregroundColor(Leaf.text)
                        Spacer().frame(height: 12)
                        Button {
                            showingTips = true
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Leaf.cream)
                                Text("LEAVE A TIP")
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
                        .accessibilityLabel("Leave a tip")
                        .accessibilityHint("Choose an optional one-time purchase. All features remain free.")

                        Spacer().frame(height: 24)

                        // ---- Support Upstream --------------------------------------------
                        Text("Support the upstream creator of Chika:")
                            .font(.archivo(12, weight: 500))
                            .lineSpacing(3)
                            .foregroundColor(Leaf.secondaryText)
                        Spacer().frame(height: 12)
                        Link(destination: Self.upstreamURL) {
                            HStack(spacing: 10) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Leaf.goldText)
                                Text("SUPPORT ORIGINAL PROJECT")
                                    .font(.anton(15))
                                    .tracking(0.5)
                                    .foregroundColor(Leaf.text)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(settings.groundSoft)
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
                                .foregroundColor(Leaf.goldText)
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
                            .foregroundColor(Leaf.goldText)
                        Text(toast)
                            .font(.archivo(13, weight: 600))
                            .foregroundColor(Leaf.text)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(settings.groundSoft)
                    .clipShape(Capsule())
                    .comicShadow(offset: 4, color: .black.opacity(0.6), corner: 20)
                    .padding(.top, 60)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(100)
            }
        }
        .leafAppearance(settings.theme)
        .navigationBarHidden(true)
        .fileExporter(
            isPresented: $showingExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: defaultBackupFilename
        ) { result in
            switch result {
            case .success:
                showToast(String(localized: "✓ Backup exported successfully"))
            case .failure(let error):
                errorMessage = String(localized: "Export failed: \(error.localizedDescription)")
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
                    errorMessage = String(localized: "Could not access selected file.")
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
                errorMessage = String(localized: "Failed to open file: \(error.localizedDescription)")
                showingErrorAlert = true
            }
        }
        .sheet(isPresented: $showingTips) { TipJarView() }
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
                        showToast(String(localized: "✓ Restored \(result.restoredComics) comics · \(result.restoredLists) lists"))
                    },
                    onCancel: {
                        showingRestoreSheet = false
                    }
                )
                .environmentObject(settings)
                .leafAppearance(settings.theme)
            }
        }
        .alert("Backup Error", isPresented: $showingErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? String(localized: "An unknown error occurred."))
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
                            .foregroundColor(Leaf.text)
                            .frame(width: 32, height: 32)
                            .background(Leaf.text.opacity(0.12))
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
                                .foregroundColor(Leaf.goldText)
                            Text("Exported \(formattedExportDate)")
                                .font(.archivo(12, weight: 600))
                                .foregroundColor(Leaf.text)
                            Spacer()
                            Text("v\(preview.appVersion)")
                                .font(.archivo(11, weight: 600))
                                .foregroundColor(Leaf.secondaryText)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(settings.groundSoft)
                                .clipShape(RoundedCornerShape(cornerRadius: 3))
                        }
                        .padding(12)
                        .background(settings.groundSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        // Backup Contents Summary
                        VStack(alignment: .leading, spacing: 10) {
                            Text("BACKUP CONTENTS")
                                .font(.anton(13))
                                .tracking(0.5)
                                .foregroundColor(Leaf.goldText)

                            VStack(alignment: .leading, spacing: 8) {
                                HStack(spacing: 8) {
                                    Image(systemName: "books.vertical.fill")
                                        .font(.system(size: 13))
                                        .foregroundColor(Leaf.secondaryText)
                                        .frame(width: 20)
                                    Text("\(preview.totalComicsInBackup) comics in backup")
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.text)
                                    if preview.matchingComicsCount > 0 {
                                        Text("(\(preview.matchingComicsCount) match library)")
                                            .font(.archivo(12, weight: 400))
                                            .foregroundColor(Leaf.goldText)
                                    }
                                }

                                HStack(spacing: 8) {
                                    Image(systemName: "list.bullet.rectangle.fill")
                                        .font(.system(size: 13))
                                        .foregroundColor(Leaf.secondaryText)
                                        .frame(width: 20)
                                    Text("\(preview.readingListsCount) reading lists")
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.text)
                                }

                                HStack(spacing: 8) {
                                    Image(systemName: "slider.horizontal.3")
                                        .font(.system(size: 13))
                                        .foregroundColor(Leaf.secondaryText)
                                        .frame(width: 20)
                                    Text(preview.hasSettings ? String(localized: "Includes app preferences") : String(localized: "No preferences included"))
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.text)
                                }
                            }
                        }
                        .padding(16)
                        .background(settings.groundSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))

                        // Strategy Selector
                        VStack(alignment: .leading, spacing: 10) {
                            Text("CONFLICT RESOLUTION")
                                .font(.anton(13))
                                .tracking(0.5)
                                .foregroundColor(Leaf.goldText)

                            VStack(spacing: 8) {
                                ForEach(BackupConflictStrategy.allCases) { strategy in
                                    Button {
                                        selectedStrategy = strategy
                                    } label: {
                                        HStack(alignment: .top, spacing: 12) {
                                            Image(systemName: selectedStrategy == strategy ? "record.circle.fill" : "circle")
                                                .font(.system(size: 16))
                                                .foregroundColor(selectedStrategy == strategy ? Leaf.ochre : Leaf.secondaryText)
                                                .padding(.top, 2)
                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(strategy.title)
                                                    .font(.archivo(13, weight: 700))
                                                    .foregroundColor(Leaf.text)
                                                Text(strategy.subtitle)
                                                    .font(.archivo(11, weight: 400))
                                                    .foregroundColor(Leaf.secondaryText)
                                            }
                                            Spacer()
                                        }
                                        .padding(12)
                                        .background(selectedStrategy == strategy ? settings.ground : settings.groundSoft)
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
                                    .foregroundColor(Leaf.secondaryText)
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
    let text: LocalizedStringKey
    init(_ text: LocalizedStringKey) { self.text = text }
    var body: some View {
        Text(text)
            .font(.archivo(9, weight: 800)).tracking(2.5).foregroundColor(Leaf.goldText)
    }
}

/// A titled settings row with a trailing switch. Tapping anywhere on the row toggles it, and the
/// surface follows the resolved appearance and AMOLED preference.
private struct ToggleRow: View {
    @EnvironmentObject private var settings: LeafSettings
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.archivo(14, weight: 700)).foregroundColor(Leaf.text)
                Text(subtitle)
                    .font(.archivo(10.5, weight: 500)).lineSpacing(3).foregroundColor(Leaf.secondaryText)
            }
            Spacer(minLength: 0)
            LeafSwitch(isOn: $isOn)
        }
        .padding(.init(top: 12, leading: 16, bottom: 12, trailing: 10))
        .background(settings.groundSoft)
        .clipShape(RoundedCornerShape(cornerRadius: 6))
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.easeInOut(duration: 0.15)) { isOn.toggle() } }
        .accessibilityRepresentation {
            Toggle(title, isOn: $isOn)
                .accessibilityHint(Text(subtitle))
        }
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
                .overlay(Capsule().stroke(isOn ? Leaf.ochre : Leaf.secondaryText, lineWidth: 2))
            Circle()
                .fill(isOn ? Leaf.ink : Leaf.secondaryText)
                .padding(3)
        }
        .frame(width: 46, height: 28)
        .onTapGesture { withAnimation(.easeInOut(duration: 0.15)) { isOn.toggle() } }
    }
}

/// Two-option layout selector (Single page vs Two-page spread), matching Android's LayoutChoice.
private struct LayoutChoiceView: View {
    @EnvironmentObject private var settings: LeafSettings
    let title: LocalizedStringKey
    @Binding var isSpread: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.archivo(14, weight: 700))
                .foregroundColor(Leaf.text)
            HStack(spacing: 2) {
                Button {
                    isSpread = false
                } label: {
                    Text("Single page")
                        .font(.archivo(12, weight: 700))
                        .foregroundColor(!isSpread ? Leaf.ink : Leaf.secondaryText)
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
                        .foregroundColor(isSpread ? Leaf.ink : Leaf.secondaryText)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(isSpread ? Leaf.ochre : Color.clear)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                }
                .buttonStyle(.plain)
            }
            .padding(2)
            .background(settings.ground)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .padding(.top, 4)
        }
    }
}
