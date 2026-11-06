import SwiftUI

/// Capture the opening state once so progress updates cannot change an in-flight transition.
struct OpenComic: Identifiable {
    let url: URL
    let opensAtCover: Bool
    let cover: UIImage?
    var id: String { url.absoluteString }

    init(url: URL) {
        self.url = url
        opensAtCover = (ReadingProgress.get(url)?.page ?? 0) == 0
        cover = opensAtCover ? CoverLoader.cachedCover(for: url) : nil
    }
}

extension View {
    func comicReaderPresentation(
        item: Binding<OpenComic?>,
        namespace: Namespace.ID,
        onDismiss: @escaping () -> Void
    ) -> some View {
        fullScreenCover(item: item, onDismiss: onDismiss) { comic in
            ReaderView(comicURL: comic.url, openingCover: comic.cover)
                .modifier(ComicOpeningTransition(comic: comic, namespace: namespace))
                // The reader's UIKit surface owns downward dismissal and pinch gestures.
                .interactiveDismissDisabled()
        }
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
