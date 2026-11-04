import SwiftUI
import LeafShared

/// Contiguous reading unit for display: single page or side-by-side spread.
struct DisplaySpread: Equatable {
    let firstPage: Int
    let secondPage: Int?
    let firstImage: UIImage?
    let secondImage: UIImage?
    let isSpread: Bool

    var isReady: Bool {
        if isSpread {
            return firstImage != nil && secondImage != nil
        }
        return firstImage != nil
    }

    static func == (lhs: DisplaySpread, rhs: DisplaySpread) -> Bool {
        lhs.firstPage == rhs.firstPage &&
        lhs.secondPage == rhs.secondPage &&
        lhs.isSpread == rhs.isSpread &&
        lhs.isReady == rhs.isReady
    }
}

/// Whole-page CBZ/CBR reader with Leaf chrome: branded header (back + title + status chips),
/// two-page spread support, pinch-to-zoom (1x-5x), pan when zoomed or fit-to-width,
/// responsive tap zones, smooth interactive page drag flipping, and bottom scrubber.
struct ReaderView: View {
    let comicURL: URL

    private enum LoadState {
        case loading
        case failed(String)
        case ready(PageLoader)
    }

    private static let ioQueue = DispatchQueue(label: "leaf.reader.io", qos: .userInitiated)

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: LeafSettings
    @State private var state: LoadState = .loading
    @State private var page = 0
    @State private var pageCount = 1

    // Display spreads: current page/spread and preloaded adjacent spreads for instant transitions
    @State private var currentSpread: DisplaySpread?
    @State private var nextSpread: DisplaySpread?
    @State private var prevSpread: DisplaySpread?

    // Interactive page turn / slide state
    @State private var slideOffset: CGFloat = 0
    @State private var isTurning = false

    @State private var rightToLeft = false
    @State private var showChrome = true
    @State private var loadToken = 0
    @State private var widePages: Set<Int> = []
    @State private var sizesKnownThrough = 0

    // Manual zoom/pan (pinch to zoom, drag to pan when zoomed)
    @State private var zoom: CGFloat = 1
    @State private var steadyZoom: CGFloat = 1
    @State private var pan: CGSize = .zero
    @State private var steadyPan: CGSize = .zero

    // Page scrubber state
    @State private var scrub: Double = 0
    @State private var scrubbing = false

    private var title: String { comicURL.deletingPathExtension().lastPathComponent }
    private var isReady: Bool { if case .ready = state { return true }; return false }

    private var currentLoader: PageLoader? {
        if case .ready(let l) = state { return l }
        return nil
    }

    private var isSpread: Bool {
        currentSpread?.isSpread == true && currentSpread?.secondImage != nil
    }

    private func isSpreadActive(for size: CGSize) -> Bool {
        let isLandscape = size.width > size.height
        return CameraTransformKt.resolveSpreadLayout(
            isLandscape: isLandscape,
            portraitSpread: settings.portraitSpread,
            landscapeSpread: settings.landscapeSpread
        )
    }

    private func resolveSpread(for targetPage: Int, in loader: PageLoader, spreadActive: Bool) -> ReaderSpread? {
        guard targetPage >= 0, targetPage < loader.pageCount else { return nil }
        if !spreadActive || targetPage == 0 {
            return ReaderSpread(first: Int32(targetPage), second: nil)
        }
        while sizesKnownThrough < min(targetPage + 1, loader.pageCount - 1) {
            let index = sizesKnownThrough + 1
            let size = loader.pageSize(index)
            if size.width > size.height && size.height > 0 {
                widePages.insert(index)
            }
            sizesKnownThrough = index
        }
        var kotlinSet = Set<KotlinInt>()
        for p in widePages { kotlinSet.insert(KotlinInt(int: Int32(p))) }
        return CameraTransformKt.resolveReaderSpread(
            page: Int32(targetPage),
            pageCount: Int32(loader.pageCount),
            widePages: kotlinSet
        )
    }

    var body: some View {
        ZStack {
            settings.ground.ignoresSafeArea()
            switch state {
            case .loading:
                ProgressView().tint(Leaf.ochre)
            case .failed(let message):
                VStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle").foregroundColor(Leaf.ochre)
                    Text(message).font(.archivo(14)).foregroundColor(Leaf.cream)
                        .multilineTextAlignment(.center)
                }
                .padding()
            case .ready(let loader):
                GeometryReader { screen in
                    readerBody(loader, size: screen.size)
                }
                .ignoresSafeArea()
            }
        }
        .overlay(alignment: .top) {
            if showChrome && isReady {
                topBar.transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .overlay(alignment: .bottom) {
            if showChrome && isReady && pageCount > 1 {
                bottomBar.transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .navigationBarHidden(true)
        .statusBarHidden(!showChrome)
        .task { load() }
        .onDisappear {
            persist()
            ReadingProgress.flushSync()
        }
    }

    private func load() {
        do {
            let archive = try CbzArchive(url: comicURL)
            let loader = PageLoader(archive: archive)
            guard loader.pageCount > 0 else { state = .failed("No image pages found in this archive"); return }
            pageCount = loader.pageCount
            ReadingProgress.resyncTotalIfNeeded(comicURL, actualTotal: pageCount)
            rightToLeft = ReadingProgress.readingDirection(comicURL)
            if let r = LeafDebug.env("LEAF_DEBUG_RTL") { rightToLeft = (r == "1") }
            if let saved = ReadingProgress.get(comicURL) {
                page = min(max(saved.page, 0), pageCount - 1)
            }
            if let p = LeafDebug.env("LEAF_DEBUG_PAGE"), let pi = Int(p) {
                page = min(max(pi, 0), pageCount - 1)
            }
            state = .ready(loader)
            ReadingProgress.markOpened(comicURL)
            loadCurrentAndAdjacent(loader: loader, targetPage: page)
        } catch {
            state = .failed("Could not open archive: \(error.localizedDescription)")
        }
    }

    private func resetZoom() {
        zoom = 1; steadyZoom = 1; pan = .zero; steadyPan = .zero
    }

    private func persist() {
        ReadingProgress.set(comicURL, page: page, step: -1, total: pageCount, rtl: rightToLeft)
    }

    private func syncLoadSpread(loader: PageLoader, spread: ReaderSpread?) -> DisplaySpread? {
        guard let spread = spread else { return nil }
        let first = Int(spread.first)
        let second = spread.second != nil ? Int(spread.second!.int32Value) : nil
        let img1 = loader.loadPage(first)
        let img2 = second != nil ? loader.loadPage(second!) : nil
        return DisplaySpread(
            firstPage: first,
            secondPage: second,
            firstImage: img1,
            secondImage: img2,
            isSpread: second != nil
        )
    }

    private func loadSpreadImages(
        loader: PageLoader,
        spread: ReaderSpread?,
        completion: @escaping (DisplaySpread?) -> Void
    ) {
        guard let spread = spread else {
            completion(nil)
            return
        }
        let first = Int(spread.first)
        let second = spread.second != nil ? Int(spread.second!.int32Value) : nil
        let isSpread = second != nil

        Self.ioQueue.async {
            let img1 = loader.loadPage(first)
            let img2 = second != nil ? loader.loadPage(second!) : nil
            let ds = DisplaySpread(
                firstPage: first,
                secondPage: second,
                firstImage: img1,
                secondImage: img2,
                isSpread: isSpread
            )
            DispatchQueue.main.async {
                completion(ds)
            }
        }
    }

    private func loadCurrentAndAdjacent(loader: PageLoader, targetPage: Int) {
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        guard let currS = resolveSpread(for: targetPage, in: loader, spreadActive: spreadActive) else { return }
        let currFirst = Int(currS.first)

        resetZoom()
        persist()

        // Reuse preloaded next or prev if matching
        if let next = nextSpread, next.firstPage == currFirst, next.isReady {
            currentSpread = next
        } else if let prev = prevSpread, prev.firstPage == currFirst, prev.isReady {
            currentSpread = prev
        } else {
            currentSpread = syncLoadSpread(loader: loader, spread: currS)
        }

        let nextTarget = Int(currS.after)
        let nextS = (nextTarget < loader.pageCount) ? resolveSpread(for: nextTarget, in: loader, spreadActive: spreadActive) : nil

        let prevTarget = currFirst - 1
        let prevS = (prevTarget >= 0) ? resolveSpread(for: prevTarget, in: loader, spreadActive: spreadActive) : nil

        loadToken &+= 1
        let token = loadToken

        loadSpreadImages(loader: loader, spread: nextS) { ds in
            guard token == self.loadToken else { return }
            self.nextSpread = ds
        }
        loadSpreadImages(loader: loader, spread: prevS) { ds in
            guard token == self.loadToken else { return }
            self.prevSpread = ds
        }
    }

    @ViewBuilder
    private func renderSpread(
        firstImage: UIImage,
        secondImage: UIImage,
        panX: CGFloat,
        panY: CGFloat,
        zoom: CGFloat,
        container: CGSize
    ) -> some View {
        let leftImg = !rightToLeft ? firstImage : secondImage
        let rightImg = !rightToLeft ? secondImage : firstImage
        let leftW = Int32(leftImg.cgImage?.width ?? Int(leftImg.size.width))
        let leftH = Int32(leftImg.cgImage?.height ?? Int(leftImg.size.height))
        let rightW = Int32(rightImg.cgImage?.width ?? Int(rightImg.size.width))
        let rightH = Int32(rightImg.cgImage?.height ?? Int(rightImg.size.height))

        let spreadDraw = CameraTransformKt.computeSpreadDraw(
            page1W: leftW, page1H: leftH,
            page2W: rightW, page2H: rightH,
            containerW: Float(container.width),
            containerH: Float(container.height),
            fill: 0.98,
            fitMode: settings.pageFitMode
        )

        // Left page
        Image(uiImage: leftImg)
            .resizable()
            .frame(width: CGFloat(leftW), height: CGFloat(leftH))
            .modifier(SpreadPageFraming(
                pageLeft: CGFloat(spreadDraw.left1),
                pageTop: CGFloat(spreadDraw.top1),
                scaledWidth: CGFloat(spreadDraw.width1),
                scaledHeight: CGFloat(spreadDraw.height1),
                zoom: zoom,
                panX: panX,
                panY: panY,
                container: container
            ))

        // Right page
        Image(uiImage: rightImg)
            .resizable()
            .frame(width: CGFloat(rightW), height: CGFloat(rightH))
            .modifier(SpreadPageFraming(
                pageLeft: CGFloat(spreadDraw.left2),
                pageTop: CGFloat(spreadDraw.top2),
                scaledWidth: CGFloat(spreadDraw.width2),
                scaledHeight: CGFloat(spreadDraw.height2),
                zoom: zoom,
                panX: panX,
                panY: panY,
                container: container
            ))

        // Center book spine shadow
        let cx = container.width / 2
        let spineX = cx + (CGFloat(spreadDraw.left2) - cx) * zoom + panX
        let spineH = CGFloat(spreadDraw.totalHeight) * zoom
        let spineTop = (container.height / 2) + (CGFloat(spreadDraw.boundTop) - (container.height / 2)) * zoom + panY
        Rectangle()
            .fill(
                LinearGradient(
                    colors: [.black.opacity(0.35), .clear],
                    startPoint: .center,
                    endPoint: .leading
                )
            )
            .frame(width: 8, height: spineH)
            .position(x: spineX, y: spineTop + spineH / 2)
            .allowsHitTesting(false)
    }

    @ViewBuilder
    private func renderSinglePage(
        image: UIImage,
        panX: CGFloat,
        panY: CGFloat,
        zoom: CGFloat,
        container: CGSize
    ) -> some View {
        let w = Int32(image.cgImage?.width ?? Int(image.size.width))
        let h = Int32(image.cgImage?.height ?? Int(image.size.height))
        Image(uiImage: image)
            .resizable()
            .frame(width: CGFloat(w), height: CGFloat(h))
            .modifier(CameraFraming(
                camL: 0, camT: 0, camR: 1, camB: 1,
                zoom: zoom,
                panX: panX,
                panY: panY,
                container: container,
                fitMode: settings.pageFitMode
            ))
    }

    @ViewBuilder
    private func renderDisplaySpread(
        _ spread: DisplaySpread,
        panX: CGFloat,
        panY: CGFloat,
        zoom: CGFloat,
        container: CGSize
    ) -> some View {
        if spread.isSpread, let first = spread.firstImage, let second = spread.secondImage {
            renderSpread(
                firstImage: first,
                secondImage: second,
                panX: panX,
                panY: panY,
                zoom: zoom,
                container: container
            )
        } else if let first = spread.firstImage {
            renderSinglePage(
                image: first,
                panX: panX,
                panY: panY,
                zoom: zoom,
                container: container
            )
        }
    }

    @ViewBuilder
    private func readerBody(_ loader: PageLoader, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            // Current page / spread
            if let current = currentSpread {
                renderDisplaySpread(
                    current,
                    panX: (zoom <= 1.01) ? slideOffset : pan.width,
                    panY: pan.height,
                    zoom: zoom,
                    container: size
                )
            }

            // Incoming adjacent page/spread sliding in when actively dragging or animating turn
            if slideOffset != 0 || isTurning {
                let isNegative = slideOffset < 0
                let incomingSpread = isNegative
                    ? (!rightToLeft ? nextSpread : prevSpread)
                    : (!rightToLeft ? prevSpread : nextSpread)
                let incomingPanX = isNegative ? (size.width + slideOffset) : (-size.width + slideOffset)

                if let incoming = incomingSpread {
                    renderDisplaySpread(
                        incoming,
                        panX: incomingPanX,
                        panY: 0,
                        zoom: 1,
                        container: size
                    )
                }

                // Shadow separator along the sliding seam
                let dividerX = isNegative ? (size.width + slideOffset) : slideOffset
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [.black.opacity(0.40), .clear],
                            startPoint: isNegative ? .leading : .trailing,
                            endPoint: isNegative ? .trailing : .leading
                        )
                    )
                    .frame(width: 24, height: size.height)
                    .position(x: isNegative ? (dividerX + 12) : (dividerX - 12), y: size.height / 2)
                    .allowsHitTesting(false)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .overlay {
            if currentSpread == nil {
                ProgressView().tint(Leaf.ochre)
            }
        }
        // Unified gesture layer: pinch-to-zoom, pan when zoomed, tap zones, and drag-to-flip
        .overlay {
            ReaderGestures(
                size: size,
                onTap: { location, sz in
                    tapZone(x: location.x, width: sz.width, in: loader)
                },
                onDoubleTap: {
                    withAnimation(.easeOut(duration: 0.22)) {
                        resetZoom()
                    }
                },
                onPinchChanged: { scale in
                    if !isTurning {
                        zoom = min(max(steadyZoom * scale, 1), 5)
                    }
                },
                onPinchEnded: {
                    if zoom <= 1.01 {
                        resetZoom()
                    } else {
                        steadyZoom = zoom
                    }
                },
                onPanChanged: { t in
                    if zoom <= 1.01 {
                        guard !isTurning else { return }
                        let spreadActive = isSpreadActive(for: size)
                        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
                        let canAdvance = !rightToLeft ? (Int(s?.after ?? Int32(page + 1)) < loader.pageCount) : (Int(s?.first ?? Int32(page)) > 0)
                        let canGoBack = !rightToLeft ? (Int(s?.first ?? Int32(page)) > 0) : (Int(s?.after ?? Int32(page + 1)) < loader.pageCount)

                        let isDraggingToAdvance = !rightToLeft ? (t.width < 0) : (t.width > 0)
                        let isDraggingToGoBack = !rightToLeft ? (t.width > 0) : (t.width < 0)

                        if (isDraggingToAdvance && !canAdvance) || (isDraggingToGoBack && !canGoBack) {
                            slideOffset = t.width * 0.25
                        } else {
                            slideOffset = t.width
                        }

                        if settings.pageFitMode == .fitWidth {
                            let raw = CGSize(width: 0, height: steadyPan.height + t.height)
                            pan = clampPan(raw, viewSize: size)
                        }
                    } else {
                        let raw = CGSize(width: steadyPan.width + t.width, height: steadyPan.height + t.height)
                        pan = clampPan(raw, viewSize: size)
                    }
                },
                onPanEnded: { translation, velocity, maxTouches in
                    handlePanEnd(translation: translation, velocity: velocity, maxTouches: maxTouches, in: loader, size: size)
                }
            )
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
        }
    }

    private var pageStatus: String {
        guard let loader = currentLoader else { return "Page \(page + 1)/\(pageCount)" }
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
        if let sec = s?.second, isSpread {
            return "Pages \(s!.first + 1)-\(sec.int32Value + 1)/\(pageCount)"
        } else {
            return "Page \(page + 1)/\(pageCount)"
        }
    }

    private var topBar: some View {
        HStack(alignment: .center, spacing: 10) {
            Button { dismiss() } label: {
                Image(systemName: "arrow.left")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Leaf.cream)
                    .frame(width: 38, height: 38)
                    .background(Leaf.cream.opacity(0.12))
                    .clipShape(Circle())
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.archivo(14, weight: 800)).foregroundColor(Leaf.cream).lineLimit(1)
                Text(pageStatus).font(.archivo(10)).tracking(1.4).foregroundColor(Leaf.creamMuted)
            }
            Spacer()

            SpreadChip(spreadEnabled: isSpreadActive(for: UIScreen.main.bounds.size)) {
                let screen = UIScreen.main.bounds.size
                let isLandscape = screen.width > screen.height
                if isLandscape {
                    settings.landscapeSpread.toggle()
                } else {
                    settings.portraitSpread.toggle()
                }
                if case .ready(let l) = state {
                    loadCurrentAndAdjacent(loader: l, targetPage: page)
                }
            }

            FitModeChip(fitMode: settings.pageFitMode) {
                settings.pageFitMode = (settings.pageFitMode == .fitPage ? .fitWidth : .fitPage)
                resetZoom()
            }

            DirectionChip(rightToLeft: rightToLeft) {
                rightToLeft.toggle()
                persist()
                if case .ready(let l) = state {
                    loadCurrentAndAdjacent(loader: l, targetPage: page)
                }
            }
        }
        .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 18)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: [Leaf.leafPrimary.opacity(0.98), Leaf.leafPrimary.opacity(0.88), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea(edges: [.top, .horizontal])
        )
    }

    private var bottomBar: some View {
        VStack(spacing: 8) {
            HStack {
                Spacer()
                PageCoin(page: (scrubbing ? Int(scrub.rounded()) : page) + 1, total: pageCount)
            }
            ComicScrubberSlider(
                value: $scrub,
                range: 0...Double(max(pageCount - 1, 1)),
                onEditingChanged: { editing in
                    if editing {
                        scrubbing = true
                    } else {
                        scrubbing = false
                        let targetPage = Int(scrub.rounded())
                        page = targetPage
                        slideOffset = 0
                        if case .ready(let l) = state {
                            loadCurrentAndAdjacent(loader: l, targetPage: targetPage)
                        }
                    }
                }
            )
        }
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: [.clear, Leaf.inkSoft.opacity(0.98)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea(edges: [.bottom, .horizontal])
        )
        .onChange(of: page) { if !scrubbing { scrub = Double($0) } }
    }

    // Tap zones: left third turns back (or next if RTL), right third turns forward (or back if RTL),
    // center third toggles chrome. If zoomed in, tap toggles chrome anywhere.
    private func tapZone(x: CGFloat, width: CGFloat, in loader: PageLoader) {
        guard !isTurning else { return }
        if zoom > 1.05 {
            withAnimation(.easeInOut(duration: 0.2)) { showChrome.toggle() }
            return
        }
        let leftBound = width / 3
        let rightBound = width * 2 / 3
        if x < leftBound {
            performTurn(forward: rightToLeft, in: loader, width: width)
        } else if x > rightBound {
            performTurn(forward: !rightToLeft, in: loader, width: width)
        } else {
            withAnimation(.easeInOut(duration: 0.2)) { showChrome.toggle() }
        }
    }

    private func performTurn(forward: Bool, in loader: PageLoader, width: CGFloat) {
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)

        let target: Int
        if forward {
            target = Int(s?.after ?? Int32(page + 1))
            guard target < loader.pageCount else { return }
        } else {
            let prevDoc = Int(s?.first ?? Int32(page)) - 1
            guard prevDoc >= 0 else { return }
            let prevS = resolveSpread(for: prevDoc, in: loader, spreadActive: spreadActive)
            target = Int(prevS?.first ?? Int32(prevDoc))
        }

        // Ensure target spread is loaded before starting transition
        let targetSpreadS = resolveSpread(for: target, in: loader, spreadActive: spreadActive)
        if forward {
            if nextSpread == nil || nextSpread?.firstPage != target || !nextSpread!.isReady {
                nextSpread = syncLoadSpread(loader: loader, spread: targetSpreadS)
            }
        } else {
            if prevSpread == nil || prevSpread?.firstPage != target || !prevSpread!.isReady {
                prevSpread = syncLoadSpread(loader: loader, spread: targetSpreadS)
            }
        }

        isTurning = true
        let targetOffset: CGFloat
        if !rightToLeft {
            targetOffset = forward ? -width : width
        } else {
            targetOffset = forward ? width : -width
        }

        withAnimation(.easeOut(duration: 0.22)) {
            slideOffset = targetOffset
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
            let incoming = forward ? self.nextSpread : self.prevSpread
            if let incoming, incoming.isReady {
                self.currentSpread = incoming
            }
            self.page = target
            self.slideOffset = 0
            self.isTurning = false
            self.resetZoom()
            self.persist()

            // Preload adjacent in background
            let newNextTarget = Int(targetSpreadS?.after ?? Int32(target + 1))
            let newNextS = (newNextTarget < loader.pageCount) ? self.resolveSpread(for: newNextTarget, in: loader, spreadActive: spreadActive) : nil
            let newPrevTarget = target - 1
            let newPrevS = (newPrevTarget >= 0) ? self.resolveSpread(for: newPrevTarget, in: loader, spreadActive: spreadActive) : nil

            self.loadSpreadImages(loader: loader, spread: newNextS) { ds in
                if self.page == target { self.nextSpread = ds }
            }
            self.loadSpreadImages(loader: loader, spread: newPrevS) { ds in
                if self.page == target { self.prevSpread = ds }
            }
        }
    }

    private func handlePanEnd(translation: CGSize, velocity: CGSize, maxTouches: Int, in loader: PageLoader, size: CGSize) {
        if zoom <= 1.01 {
            guard !isTurning else { return }
            let dragX = translation.width
            let velX = velocity.width
            let W = size.width

            let spreadActive = isSpreadActive(for: size)
            let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
            let canAdvance = !rightToLeft ? (Int(s?.after ?? Int32(page + 1)) < loader.pageCount) : (Int(s?.first ?? Int32(page)) > 0)
            let canGoBack = !rightToLeft ? (Int(s?.first ?? Int32(page)) > 0) : (Int(s?.after ?? Int32(page + 1)) < loader.pageCount)

            let forwardDragDist = !rightToLeft ? -dragX : dragX
            let forwardVel = !rightToLeft ? -velX : velX
            let settleForward = (forwardDragDist > W * 0.35 || forwardVel > 500) && forwardVel > -400

            let backDragDist = !rightToLeft ? dragX : -dragX
            let backVel = !rightToLeft ? velX : -velX
            let settleBackward = (backDragDist > W * 0.35 || backVel > 500) && backVel > -400

            if settleForward && canAdvance {
                performTurn(forward: true, in: loader, width: W)
            } else if settleBackward && canGoBack {
                performTurn(forward: false, in: loader, width: W)
            } else {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                    slideOffset = 0
                }
            }
        } else {
            steadyPan = pan
        }
    }

    private func clampPan(_ raw: CGSize, viewSize: CGSize) -> CGSize {
        let cw = viewSize.width, ch = viewSize.height
        if isSpread, let current = currentSpread, let leftImg = current.firstImage, let rightImg = current.secondImage {
            let leftW = Int32(leftImg.cgImage?.width ?? Int(leftImg.size.width))
            let leftH = Int32(leftImg.cgImage?.height ?? Int(leftImg.size.height))
            let rightW = Int32(rightImg.cgImage?.width ?? Int(rightImg.size.width))
            let rightH = Int32(rightImg.cgImage?.height ?? Int(rightImg.size.height))
            let spread = CameraTransformKt.computeSpreadDraw(
                page1W: leftW, page1H: leftH,
                page2W: rightW, page2H: rightH,
                containerW: Float(cw),
                containerH: Float(ch),
                fill: 0.98,
                fitMode: settings.pageFitMode
            )
            let totalW = CGFloat(spread.totalWidth) * zoom
            let totalH = CGFloat(spread.totalHeight) * zoom
            let baseLeft = cw / 2 + (CGFloat(spread.boundLeft) - cw / 2) * zoom
            let baseTop = ch / 2 + (CGFloat(spread.boundTop) - cw / 2) * zoom
            let x: CGFloat = totalW <= cw ? 0 : min(max(raw.width, cw - totalW - baseLeft), -baseLeft)
            let y: CGFloat = totalH <= ch ? 0 : min(max(raw.height, ch - totalH - baseTop), -baseTop)
            return CGSize(width: x, height: y)
        } else if let current = currentSpread, let img = current.firstImage {
            let w = Int32(img.cgImage?.width ?? Int(img.size.width))
            let h = Int32(img.cgImage?.height ?? Int(img.size.height))
            let draw = CameraTransformKt.computePageDraw(
                camera: Panel.companion.FULL_PAGE,
                bitmapW: w,
                bitmapH: h,
                containerW: Float(cw),
                containerH: Float(ch),
                fill: 0.98,
                fitMode: settings.pageFitMode
            )
            let scaledW = CGFloat(draw.scaledWidth) * zoom
            let scaledH = CGFloat(draw.scaledHeight) * zoom
            let baseLeft = cw / 2 + (CGFloat(draw.left) - cw / 2) * zoom
            let baseTop = ch / 2 + (CGFloat(draw.top) - ch / 2) * zoom
            let x: CGFloat = scaledW <= cw ? 0 : min(max(raw.width, cw - scaledW - baseLeft), -baseLeft)
            let y: CGFloat = scaledH <= ch ? 0 : min(max(raw.height, ch - scaledH - baseTop), -baseTop)
            return CGSize(width: x, height: y)
        }
        return .zero
    }
}

/// Frames a single page inside the container according to camera and fitMode.
private struct CameraFraming: GeometryEffect {
    var camL: CGFloat, camT: CGFloat, camR: CGFloat, camB: CGFloat
    var zoom: CGFloat
    var panX: CGFloat, panY: CGFloat
    var container: CGSize
    var fitMode: PageFitMode

    var animatableData: AnimatablePair<
        AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>>,
        AnimatablePair<CGFloat, AnimatablePair<CGFloat, CGFloat>>
    > {
        get {
            AnimatablePair(
                AnimatablePair(AnimatablePair(camL, camT), AnimatablePair(camR, camB)),
                AnimatablePair(zoom, AnimatablePair(panX, panY)))
        }
        set {
            camL = newValue.first.first.first
            camT = newValue.first.first.second
            camR = newValue.first.second.first
            camB = newValue.first.second.second
            zoom = newValue.second.first
            panX = newValue.second.second.first
            panY = newValue.second.second.second
        }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let draw = CameraTransformKt.computePageDraw(
            camera: Panel(left: Float(camL), top: Float(camT), right: Float(camR), bottom: Float(camB)),
            bitmapW: Int32(max(size.width, 1)),
            bitmapH: Int32(max(size.height, 1)),
            containerW: Float(container.width),
            containerH: Float(container.height),
            fill: 0.98,
            fitMode: fitMode
        )
        let s = CGFloat(draw.scaledWidth) / max(size.width, 1)
        let cx = container.width / 2, cy = container.height / 2
        let left = cx + (CGFloat(draw.left) - cx) * zoom + panX
        let top = cy + (CGFloat(draw.top) - cy) * zoom + panY
        return ProjectionTransform(
            CGAffineTransform(translationX: left, y: top).scaledBy(x: s * zoom, y: s * zoom))
    }
}

/// Frames one page of a two-page spread side-by-side inside the container.
private struct SpreadPageFraming: GeometryEffect {
    var pageLeft: CGFloat
    var pageTop: CGFloat
    var scaledWidth: CGFloat
    var scaledHeight: CGFloat
    var zoom: CGFloat
    var panX: CGFloat, panY: CGFloat
    var container: CGSize

    func effectValue(size: CGSize) -> ProjectionTransform {
        let s = (scaledWidth / max(size.width, 1)) * zoom
        let sy = (scaledHeight / max(size.height, 1)) * zoom
        let cx = container.width / 2, cy = container.height / 2
        let left = cx + (pageLeft - cx) * zoom + panX
        let top = cy + (pageTop - cy) * zoom + panY
        return ProjectionTransform(
            CGAffineTransform(translationX: left, y: top).scaledBy(x: s, y: sy))
    }
}

/// Reading-direction control showing its current state (LTR/RTL).
struct DirectionChip: View {
    let rightToLeft: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
                Image(systemName: "arrow.left.arrow.right")
                    .font(.system(size: 15, weight: .semibold)).foregroundColor(Leaf.cream)
                Text(rightToLeft ? "RTL" : "LTR")
                    .font(.archivo(11, weight: 700)).tracking(1).foregroundColor(Leaf.cream)
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Leaf.cream.opacity(0.12))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Quick control for the current orientation's layout preference (SPREAD vs SINGLE).
struct SpreadChip: View {
    let spreadEnabled: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
                Image(systemName: "book.closed")
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(Leaf.cream)
                Text(spreadEnabled ? "SPREAD" : "SINGLE")
                    .font(.archivo(11, weight: 700)).tracking(1).foregroundColor(Leaf.cream)
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Leaf.cream.opacity(0.12))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Mode control that toggles between fit-to-page and fit-to-width (PAGE vs WIDTH).
struct FitModeChip: View {
    let fitMode: PageFitMode
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
                Image(systemName: fitMode == .fitWidth ? "arrow.left.and.right" : "aspectratio")
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(Leaf.cream)
                Text(fitMode == .fitWidth ? "WIDTH" : "PAGE")
                    .font(.archivo(11, weight: 700)).tracking(1).foregroundColor(Leaf.cream)
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Leaf.cream.opacity(0.12))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Custom comic scrubber slider styled with Leaf colors.
struct ComicScrubberSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let onEditingChanged: (Bool) -> Void

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let total = max(range.upperBound - range.lowerBound, 1)
            let fraction = max(0, min(1, (value - range.lowerBound) / total))

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Leaf.cream.opacity(0.20))
                    .frame(height: 4)

                Capsule()
                    .fill(Leaf.ochre)
                    .frame(width: max(w * CGFloat(fraction), 0), height: 4)

                Circle()
                    .fill(Leaf.ochre)
                    .frame(width: 16, height: 16)
                    .overlay(Circle().stroke(Leaf.ink, lineWidth: 2))
                    .comicShadow(offset: 1.5, color: .black.opacity(0.4), corner: 8)
                    .offset(x: max(0, min(w - 16, w * CGFloat(fraction) - 8)))
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        onEditingChanged(true)
                        let newFraction = max(0, min(1, gesture.location.x / w))
                        value = range.lowerBound + Double(newFraction) * total
                    }
                    .onEnded { _ in
                        onEditingChanged(false)
                    }
            )
        }
        .frame(height: 28)
    }
}
