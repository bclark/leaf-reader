import SwiftUI

/// Detail screen for a custom Reading List, matching Android's reading list sheet/dialog.
/// Supports adding comics, removing comics, reordering comics, and launching issues.
struct ReadingListDetailView: View {
    let listId: String
    @ObservedObject var library: LibraryStore
    let onOpenComic: (URL) -> Void
    let onDismiss: () -> Void

    @EnvironmentObject private var settings: LeafSettings
    @State private var showAddComicsSheet = false
    @State private var showRenameAlert = false
    @State private var showDeleteAlert = false
    @State private var renameInput = ""
    @State private var selectedToAdd: Set<URL> = []

    private var currentList: ReadingListRecord? {
        library.readingLists.first { $0.id == listId }
    }

    private var listComics: [URL] {
        guard let list = currentList else { return [] }
        let comicMap = Dictionary(uniqueKeysWithValues: library.comics.map { ($0.lastPathComponent, $0) })
        return list.itemFilenames.compactMap { comicMap[$0] }
    }

    var body: some View {
        ZStack {
            settings.ground.ignoresSafeArea()
            Halftone(color: Leaf.crimson, alpha: 0.05).ignoresSafeArea()

            VStack(spacing: 0) {
                appBar
                GeometryReader { geo in
                    content(for: geo.size.width)
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showAddComicsSheet) {
            addComicsSheet
        }
        .alert("Rename Reading List", isPresented: $showRenameAlert) {
            TextField("List name", text: $renameInput)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                if !renameInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    library.renameReadingList(id: listId, newName: renameInput)
                }
            }
        }
        .alert("Delete Reading List?", isPresented: $showDeleteAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                library.deleteReadingList(id: listId)
                onDismiss()
            }
        } message: {
            Text("This deletes the list. Comics inside the list are NOT deleted.")
        }
    }

    private var appBar: some View {
        HStack(spacing: 14) {
            Button { onDismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Leaf.cream)
                    .frame(width: 38, height: 38)
                    .background(Leaf.cream.opacity(0.14))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 1) {
                Text(currentList?.name.uppercased() ?? "READING LIST")
                    .font(.anton(18))
                    .foregroundColor(Leaf.cream)
                    .lineLimit(1)
                Text("\(listComics.count) COMICS")
                    .font(.archivo(11, weight: 600))
                    .foregroundColor(Leaf.creamMuted)
            }

            Spacer()

            Menu {
                Button {
                    renameInput = currentList?.name ?? ""
                    showRenameAlert = true
                } label: {
                    Label("Rename List", systemImage: "pencil")
                }
                Button(role: .destructive) {
                    showDeleteAlert = true
                } label: {
                    Label("Delete List", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Leaf.cream)
                    .frame(width: 36, height: 36)
                    .background(Leaf.cream.opacity(0.14))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 12)
        .background(Leaf.leafPrimary.ignoresSafeArea(edges: .top))
    }

    private func content(for width: CGFloat) -> some View {
        let cols = max(2, min(settings.libraryColumns, width >= 600 ? 4 : (width >= 380 ? 3 : 2)))
        let spacing: CGFloat = cols >= 4 ? 12 : 16
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: spacing), count: cols)

        return ScrollView {
            VStack(spacing: 16) {
                HStack {
                    OchreBadge(text: "CUSTOM LIST")
                    Spacer()
                    Button {
                        selectedToAdd.removeAll()
                        showAddComicsSheet = true
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                                .font(.system(size: 11, weight: .bold))
                            Text("ADD COMICS")
                                .font(.anton(11))
                        }
                        .foregroundColor(Leaf.ink)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Leaf.ochre)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                    }
                    .buttonStyle(.plain)
                }

                if listComics.isEmpty {
                    VStack(spacing: 8) {
                        Text("This reading list is empty.")
                            .font(.archivo(13))
                            .foregroundColor(Leaf.creamMuted)
                            .padding(.top, 24)
                    }
                } else {
                    LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                        ForEach(listComics, id: \.self) { url in
                            Button {
                                onOpenComic(url)
                            } label: {
                                SeriesIssueCard(
                                    url: url,
                                    metadata: library.metadata[url.lastPathComponent],
                                    progress: ReadingProgress.get(url),
                                    columns: cols
                                )
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button {
                                    onOpenComic(url)
                                } label: {
                                    Label("Read", systemImage: "book.open")
                                }
                                Button(role: .destructive) {
                                    library.removeComicFromReadingList(listId: listId, url: url)
                                } label: {
                                    Label("Remove from List", systemImage: "minus.circle")
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)
            .padding(.bottom, 48)
        }
    }

    private var addComicsSheet: some View {
        NavigationStack {
            let availableComics = library.comics.filter { !listComics.contains($0) }
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    if availableComics.isEmpty {
                        Text("All library comics are already in this list.")
                            .font(.archivo(13))
                            .foregroundColor(Leaf.creamMuted)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List(availableComics, id: \.self) { url in
                            let isSelected = selectedToAdd.contains(url)
                            Button {
                                if isSelected { selectedToAdd.remove(url) }
                                else { selectedToAdd.insert(url) }
                            } label: {
                                HStack {
                                    Text(url.deletingPathExtension().lastPathComponent)
                                        .font(.archivo(13, weight: 600))
                                        .foregroundColor(Leaf.cream)
                                    Spacer()
                                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                        .font(.system(size: 18))
                                        .foregroundColor(isSelected ? Leaf.ochre : Leaf.creamMuted)
                                }
                            }
                            .listRowBackground(Leaf.deepInkSurface)
                        }
                        .listStyle(.plain)
                    }
                }
            }
            .navigationTitle("Add to \(currentList?.name ?? "List")")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showAddComicsSheet = false }
                        .foregroundColor(Leaf.ochre)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add (\(selectedToAdd.count))") {
                        library.addComicsToReadingList(listId: listId, urls: Array(selectedToAdd))
                        showAddComicsSheet = false
                    }
                    .foregroundColor(selectedToAdd.isEmpty ? Leaf.creamMuted : Leaf.ochre)
                    .disabled(selectedToAdd.isEmpty)
                }
            }
        }
    }
}
