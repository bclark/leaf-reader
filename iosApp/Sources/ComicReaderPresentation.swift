import SwiftUI

/// Capture the opening state once so progress updates cannot change an in-flight transition.
struct OpenComic: Identifiable {
    let url: URL
    let opensAtCover: Bool
    let wasCompletedAtOpen: Bool
    let cover: UIImage?
    var id: String { url.absoluteString }

    @MainActor init(url: URL) {
        self.url = url
        let progress = ReadingProgress.get(url)
        opensAtCover = (progress?.page ?? 0) == 0
        wasCompletedAtOpen = progress?.isCompleted == true
        cover = opensAtCover ? CoverLoader.cachedCover(for: url) : nil
    }
}

extension View {
    func comicReaderPresentation(
        item: Binding<OpenComic?>,
        namespace: Namespace.ID,
        onDismiss: @escaping () -> Void
    ) -> some View {
        modifier(ComicReaderPolicyBridge(item: item, namespace: namespace, onDismiss: onDismiss))
    }

    func leafReaderCover(item: Binding<OpenComic?>, namespace: Namespace.ID,
                         onDismiss: @escaping () -> Void) -> some View {
        fullScreenCover(item: item, onDismiss: onDismiss) { comic in
            ReaderView(comicURL: comic.url, openingCover: comic.cover)
                .modifier(ComicOpeningTransition(comic: comic, namespace: namespace))
                // The reader's UIKit surface owns downward dismissal and pinch gestures.
                .interactiveDismissDisabled()
        }
    }

    func linkedReaderPresentation(item: Binding<LinkedComic?>, store: LinkedFilesStore?,
                                  onDismiss: @escaping () -> Void) -> some View {
        modifier(LinkedReaderPresentation(store: store, item: item, onDismiss: onDismiss))
    }

    @ViewBuilder
    func comicCoverSource(url: URL, namespace: Namespace.ID?) -> some View {
        if #available(iOS 18.0, *), let namespace {
            matchedTransitionSource(id: url, in: namespace)
        } else {
            self
        }
    }
}

private struct LinkedReaderPresentation: ViewModifier {
    let store: LinkedFilesStore?
    @Binding var item: LinkedComic?
    let onDismiss: () -> Void

    func body(content: Content) -> some View {
        content.fullScreenCover(item: $item, onDismiss: onDismiss) { comic in
            if let store {
                ReaderView(comicURL: comic.progressURL, linkedFile: comic, onLocateLinkedFile: { url in
                    try await store.link(url, replacing: comic.id)
                }, onLinkedFileOpened: { store.refreshCoverIfNeeded(for: $0) })
                .interactiveDismissDisabled()
            }
        }
    }
}

private struct ComicOpeningTransition: ViewModifier {
    let comic: OpenComic
    let namespace: Namespace.ID
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), comic.opensAtCover, !reduceMotion {
            content.navigationTransition(.zoom(sourceID: comic.url, in: namespace))
        } else {
            content
        }
    }
}

private struct ComicReaderPolicyBridge: ViewModifier {
    @Environment(\.tipPromptPolicy) private var policy
    let item: Binding<OpenComic?>
    let namespace: Namespace.ID
    let onDismiss: () -> Void

    @ViewBuilder func body(content: Content) -> some View {
        if let policy {
            content.modifier(ContextualTipReaderPresentation(item: item, namespace: namespace,
                onDismiss: onDismiss, policy: policy))
        } else {
            content.leafReaderCover(item: item, namespace: namespace, onDismiss: onDismiss)
        }
    }
}
