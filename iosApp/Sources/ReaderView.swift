import SwiftUI
import LeafShared

/// Panel-by-panel CBZ reader with Leaf chrome: a branded header (back + title + issue), reticle
/// brackets framing the page, the starburst page coin, and a "tap to step" hint — matching the
/// Android reader. Tapping the right side steps page → panel 1 → … → next page; the left side steps
/// back; panels come from the on-device Core ML detector via the shared decoder + planner, and the
/// camera is framed by the shared computePageDraw and computeSpreadDraw.
struct ReaderView: View {
    let comicURL: URL

    private enum LoadState {
        case loading
        case failed(String)
        case ready(PageLoader)
    }

    private static let detector = LiteRTPanelDetector()
    private static let ioQueue = DispatchQueue(label: "leaf.reader.io", qos: .userInitiated)
    private static let detectQueue = DispatchQueue(label: "leaf.reader.detect", qos: .userInitiated)

    // Flick tuning ported from Android's ReaderScreen: a page turn needs a genuine horizontal flick
    // (velocity, not distance), biased against vertical drags, so a slow reposition doesn't turn.
    private static let flickVelocityPxS: CGFloat = 700  // Android FLICK_VELOCITY_PX_S
    private static let swipeHorizontalBias: CGFloat = 1.2 // Android SWIPE_HORIZONTAL_BIAS
    // Sentinel restoreStep meaning "land on this page's whole-page outro" (used by backward turns);
    // resolved to regions.count once the page's regions are known.
    private static let outroSlot = Int.max
    // Android's camera motion: tween(360, FastOutSlowInEasing) between framed views.
    // FastOutSlowIn == cubic-bezier(0.4, 0, 0.2, 1).
    private static let cameraTween = Animation.timingCurve(0.4, 0, 0.2, 1, duration: 0.36)

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: LeafSettings
    @State private var state: LoadState = .loading
    @State private var page = 0
    @State private var pageCount = 1
    @State private var image: UIImage?
    @State private var secondImage: UIImage?
    @State private var nextImage: UIImage?
    @State private var prevImage: UIImage?
    @State private var slideOffset: CGFloat = 0
    @State private var isTurning = false
    @State private var regions: [Panel] = [Panel.companion.FULL_PAGE]
    @State private var step = -1
    @State private var detecting = false
    @State private var rightToLeft = false
    @State private var showChrome = true
    @State private var loadToken = 0
    @State private var widePages: Set<Int> = []
    @State private var sizesKnownThrough = 0
    // Detected panels cached per page (in the current reading direction) so back/forward and
    // prefetched pages don't re-run the detector. Cleared when the direction changes.
    @State private var panelCache: [Int: [Panel]] = [:]
    // Manual zoom/pan layered on top of the panel-framing camera (pinch to zoom, drag to pan).
    @State private var zoom: CGFloat = 1
    @State private var steadyZoom: CGFloat = 1
    @State private var pan: CGSize = .zero
    @State private var steadyPan: CGSize = .zero
    // Page scrubber state (commit on release, not per-tick) + per-page fade-in alpha.
    @State private var scrub: Double = 0
    @State private var scrubbing = false
    @State private var pageAlpha: Double = 1

    private var title: String { comicURL.deletingPathExtension().lastPathComponent }
    // Slot model matching Android: step -1 = whole-page intro, 0…n-1 = panels, n = whole-page outro.
    // In Page Flip mode, the camera always frames the whole page.
    private var camera: Panel {
        if settings.pageSlideEnabled {
            return Panel.companion.FULL_PAGE
        }
        return (step >= 0 && step < regions.count) ? regions[step] : Panel.companion.FULL_PAGE
    }
    // Whether the current slot is a framed panel (vs. the whole-page intro/outro).
    private var onPanel: Bool { !settings.pageSlideEnabled && step >= 0 && step < regions.count }

    private var isReady: Bool { if case .ready = state { return true }; return false }

    private func isSpreadActive(for size: CGSize) -> Bool {
        let isLandscape = size.width > size.height
        return CameraTransformKt.resolveSpreadLayout(isLandscape: isLandscape, portraitSpread: settings.portraitSpread, landscapeSpread: settings.landscapeSpread)
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
        return CameraTransformKt.resolveReaderSpread(page: Int32(targetPage), pageCount: Int32(loader.pageCount), widePages: kotlinSet)
    }

    private var currentLoader: PageLoader? {
        if case .ready(let l) = state { return l }
        return nil
    }

    private var isSpread: Bool {
        guard let loader = currentLoader else { return false }
        if !settings.pageSlideEnabled && step >= 0 && step < regions.count {
            return false
        }
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
        return s?.second != nil && secondImage != nil
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
            var restore = ReadingProgress.get(comicURL)?.step ?? -1
            if let s = LeafDebug.env("LEAF_DEBUG_STEP"), let si = Int(s) { restore = si }
            loadPage(loader, restoreStep: restore)
        } catch {
            state = .failed("Could not open archive: \(error.localizedDescription)")
        }
    }

    private func loadPage(_ loader: PageLoader, restoreStep: Int = -1) {
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
        let firstPage = Int(s?.first ?? Int32(page))
        let secondPage = s?.second != nil ? Int(s!.second!.int32Value) : nil

        regions = panelCache[firstPage] ?? [Panel.companion.FULL_PAGE]
        if settings.pageSlideEnabled {
            step = -1
        } else {
            step = (restoreStep == Self.outroSlot) ? regions.count : restoreStep
        }
        resetZoom()
        persist()
        pageAlpha = 0.35

        loadToken &+= 1
        let token = loadToken
        Self.ioQueue.async {
            let img = loader.loadPage(firstPage)
            let sec = secondPage != nil ? loader.loadPage(secondPage!) : nil
            let nextIndex = Int(s?.after ?? Int32(firstPage + 1))
            let next = (nextIndex < loader.pageCount) ? loader.loadPage(nextIndex) : nil
            let prevIndex = firstPage - 1
            let prev = (prevIndex >= 0) ? loader.loadPage(prevIndex) : nil
            DispatchQueue.main.async {
                guard token == self.loadToken else { return }
                self.image = img
                self.secondImage = sec
                self.nextImage = next
                self.prevImage = prev
                withAnimation(.easeOut(duration: 0.28)) { self.pageAlpha = 1 }
                if let cached = self.panelCache[firstPage] {
                    withAnimation(Self.cameraTween) {
                        self.regions = cached
                        if self.step > cached.count { self.step = cached.count }
                    }
                } else if !self.settings.pageSlideEnabled {
                    self.redetect()
                }
                self.prefetch(nextIndex, loader)
            }
        }
    }

    private func resetZoom() {
        zoom = 1; steadyZoom = 1; pan = .zero; steadyPan = .zero
    }

    private func turnPage(next: Bool, in loader: PageLoader) {
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
        let target: Int
        if next {
            target = Int(s?.after ?? Int32(page + 1))
        } else {
            let prevIndex = Int(s?.first ?? Int32(page)) - 1
            if prevIndex >= 0 {
                let prevSpread = resolveSpread(for: prevIndex, in: loader, spreadActive: spreadActive)
                target = Int(prevSpread?.first ?? Int32(prevIndex))
            } else {
                target = -1
            }
        }
        guard target >= 0, target < loader.pageCount else { return }
        page = target
        loadPage(loader)
    }

    private func persist() {
        ReadingProgress.set(comicURL, page: page, step: step, total: pageCount, rtl: rightToLeft)
    }

    private func redetect() {
        guard let img = image, let detector = Self.detector else { return }
        let rtl = rightToLeft
        let forPage = page
        detecting = true
        Self.detectQueue.async {
            let found = detector.zoomRegions(for: img, rightToLeft: rtl)
            DispatchQueue.main.async {
                self.panelCache[forPage] = found
                guard forPage == self.page else { return }
                withAnimation(Self.cameraTween) {
                    self.regions = found
                    if self.step > found.count { self.step = found.count }
                }
                self.detecting = false
            }
        }
    }

    private func prefetch(_ index: Int, _ loader: PageLoader) {
        guard index >= 0, index < loader.pageCount, panelCache[index] == nil,
              let detector = Self.detector else { return }
        let rtl = rightToLeft
        Self.detectQueue.async {
            guard let img = loader.loadPage(index) else { return }
            let found = detector.zoomRegions(for: img, rightToLeft: rtl)
            DispatchQueue.main.async {
                guard self.rightToLeft == rtl else { return }
                self.panelCache[index] = found
            }
        }
    }

    @ViewBuilder
    private func readerBody(_ loader: PageLoader, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            // Incoming adjacent page sliding in when actively dragging / flipping in Page Flip mode
            if settings.pageSlideEnabled && slideOffset != 0 {
                if slideOffset < 0 {
                    let incoming = rightToLeft ? prevImage : nextImage
                    if let incoming, let cg = incoming.cgImage {
                        Image(uiImage: incoming)
                            .resizable()
                            .frame(width: CGFloat(cg.width), height: CGFloat(cg.height))
                            .modifier(CameraFraming(
                                camL: 0, camT: 0, camR: 1, camB: 1,
                                zoom: 1, panX: size.width + slideOffset, panY: 0,
                                container: size,
                                fitMode: settings.pageFitMode))
                    }
                } else if slideOffset > 0 {
                    let incoming = rightToLeft ? nextImage : prevImage
                    if let incoming, let cg = incoming.cgImage {
                        Image(uiImage: incoming)
                            .resizable()
                            .frame(width: CGFloat(cg.width), height: CGFloat(cg.height))
                            .modifier(CameraFraming(
                                camL: 0, camT: 0, camR: 1, camB: 1,
                                zoom: 1, panX: -size.width + slideOffset, panY: 0,
                                container: size,
                                fitMode: settings.pageFitMode))
                    }
                }

                // Shadow separator along the sliding seam
                let dividerX = (slideOffset < 0) ? (size.width + slideOffset) : slideOffset
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [.black.opacity(0.40), .clear],
                            startPoint: (slideOffset < 0) ? .leading : .trailing,
                            endPoint: (slideOffset < 0) ? .trailing : .leading
                        )
                    )
                    .frame(width: 24, height: size.height)
                    .position(x: (slideOffset < 0) ? (dividerX + 12) : (dividerX - 12), y: size.height / 2)
                    .allowsHitTesting(false)
            }

            // Current page content: two-page spread or single page
            if isSpread, let image, let secondImage,
               image.cgImage != nil, secondImage.cgImage != nil {
                let leftImg = !rightToLeft ? image : secondImage
                let rightImg = !rightToLeft ? secondImage : image
                let leftW = Int32(leftImg.cgImage?.width ?? 1)
                let leftH = Int32(leftImg.cgImage?.height ?? 1)
                let rightW = Int32(rightImg.cgImage?.width ?? 1)
                let rightH = Int32(rightImg.cgImage?.height ?? 1)

                let spread = CameraTransformKt.computeSpreadDraw(
                    page1W: leftW, page1H: leftH,
                    page2W: rightW, page2H: rightH,
                    containerW: Float(size.width),
                    containerH: Float(size.height),
                    fill: 0.98,
                    fitMode: settings.pageFitMode
                )

                let currentPanX = (settings.pageSlideEnabled && zoom <= 1.01) ? slideOffset : pan.width

                // Left page
                Image(uiImage: leftImg)
                    .resizable()
                    .frame(width: CGFloat(leftW), height: CGFloat(leftH))
                    .modifier(SpreadPageFraming(
                        pageLeft: CGFloat(spread.left1),
                        pageTop: CGFloat(spread.top1),
                        scaledWidth: CGFloat(spread.width1),
                        scaledHeight: CGFloat(spread.height1),
                        zoom: zoom,
                        panX: currentPanX,
                        panY: pan.height,
                        container: size
                    ))
                    .opacity(pageAlpha)

                // Right page
                Image(uiImage: rightImg)
                    .resizable()
                    .frame(width: CGFloat(rightW), height: CGFloat(rightH))
                    .modifier(SpreadPageFraming(
                        pageLeft: CGFloat(spread.left2),
                        pageTop: CGFloat(spread.top2),
                        scaledWidth: CGFloat(spread.width2),
                        scaledHeight: CGFloat(spread.height2),
                        zoom: zoom,
                        panX: currentPanX,
                        panY: pan.height,
                        container: size
                    ))
                    .opacity(pageAlpha)

                // Center book spine shadow
                let cx = size.width / 2
                let spineX = cx + (CGFloat(spread.left2) - cx) * zoom + currentPanX
                let spineH = CGFloat(spread.totalHeight) * zoom
                let spineTop = (size.height / 2) + (CGFloat(spread.boundTop) - (size.height / 2)) * zoom + pan.height
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
            } else if let image, let cg = image.cgImage {
                Image(uiImage: image)
                    .resizable()
                    .frame(width: CGFloat(cg.width), height: CGFloat(cg.height))
                    .modifier(CameraFraming(
                        camL: CGFloat(camera.left), camT: CGFloat(camera.top),
                        camR: CGFloat(camera.right), camB: CGFloat(camera.bottom),
                        zoom: zoom,
                        panX: (settings.pageSlideEnabled && zoom <= 1.01) ? slideOffset : pan.width,
                        panY: pan.height,
                        container: size,
                        fitMode: settings.pageFitMode))
                    .opacity(pageAlpha)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .overlay { if image == nil { ProgressView().tint(Leaf.ochre) } }
        // Reticle framing brackets — only shown in Panel by Panel mode
        .overlay { if image != nil && !settings.pageSlideEnabled && !isSpread {
            Reticle(color: Leaf.crimson.opacity(0.45), inset: 6, length: 16, stroke: 2).padding(6)
        } }
        // Gesture layer: interactive page slide in Page Flip mode, panel zoom/pan in guided mode
        .overlay {
            ReaderGestures(
                onTap: { location, sz in tapZone(x: location.x, width: sz.width, in: loader) },
                onDoubleTap: { showWholePage() },
                onPinchChanged: { scale in
                    if !isTurning {
                        zoom = min(max(steadyZoom * scale, 1), 5)
                    }
                },
                onPinchEnded: { if zoom <= 1.01 { resetZoom() } else { steadyZoom = zoom } },
                onPanChanged: { t in
                    if settings.pageSlideEnabled && zoom <= 1.01 {
                        guard !isTurning else { return }
                        let isAtLeadingBound = (!rightToLeft && page <= 0) || (rightToLeft && page >= pageCount - 1)
                        let isAtTrailingBound = (!rightToLeft && page >= pageCount - 1) || (rightToLeft && page <= 0)
                        if (t.width > 0 && isAtLeadingBound) || (t.width < 0 && isAtTrailingBound) {
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
        }
    }

    // Top-bar status line, matching Android: "Page X/Y · panel N/M" / "· full page" / "· detecting…".
    private var pageStatus: String {
        let prefix: String
        if let loader = currentLoader {
            let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
            let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
            if let sec = s?.second, isSpread {
                prefix = "Pages \(s!.first + 1)-\(sec.int32Value + 1)/\(pageCount) · "
            } else {
                prefix = "Page \(page + 1)/\(pageCount) · "
            }
        } else {
            prefix = "Page \(page + 1)/\(pageCount) · "
        }
        if settings.pageSlideEnabled { return prefix + "page flip" }
        if detecting { return prefix + "detecting…" }
        if onPanel { return prefix + "panel \(step + 1)/\(regions.count)" }
        return prefix + "full page"
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
                Text(pageStatus).font(.archivo(9)).tracking(1.4).foregroundColor(Leaf.creamMuted)
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
                if case .ready(let l) = state { loadPage(l) }
            }

            FitModeChip(fitMode: settings.pageFitMode) {
                settings.pageFitMode = (settings.pageFitMode == .fitPage ? .fitWidth : .fitPage)
                resetZoom()
            }

            DirectionChip(rightToLeft: rightToLeft) {
                rightToLeft.toggle()
                panelCache.removeAll()
                persist()
                redetect()
            }
        }
        .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 18)
        .frame(maxWidth: .infinity)
        .background(LinearGradient(colors: [Leaf.leafPrimary.opacity(0.98), Leaf.leafPrimary.opacity(0.88), .clear],
                                   startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea(edges: [.top, .horizontal]))
    }

    private var bottomBar: some View {
        VStack(spacing: 8) {
            HStack {
                Button {
                    withAnimation(Self.cameraTween) {
                        settings.pageSlideEnabled.toggle()
                        slideOffset = 0
                        if settings.pageSlideEnabled {
                            step = -1
                            resetZoom()
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: settings.pageSlideEnabled ? "book.fill" : "viewfinder")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Leaf.ochre)
                        Text(settings.pageSlideEnabled ? "PAGE FLIP" : "PANEL BY PANEL")
                            .font(.anton(11))
                            .tracking(1)
                            .foregroundColor(Leaf.cream)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Leaf.leafPrimary.opacity(0.9))
                    .clipShape(RoundedCornerShape(cornerRadius: 12))
                }
                .buttonStyle(.plain)

                Spacer()
                PageCoin(page: (scrubbing ? Int(scrub.rounded()) : page) + 1, total: pageCount)
            }
            ComicScrubberSlider(
                value: $scrub,
                range: 0...Double(max(pageCount - 1, 1)),
                onEditingChanged: { editing in
                    if editing { scrubbing = true }
                    else {
                        scrubbing = false
                        page = Int(scrub.rounded())
                        slideOffset = 0
                        if case .ready(let a) = state { loadPage(a) }
                    }
                }
            )
        }
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(LinearGradient(colors: [.clear, Leaf.inkSoft.opacity(0.98)],
                                   startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea(edges: [.bottom, .horizontal]))
        .onChange(of: page) { if !scrubbing { scrub = Double($0) } }
    }

    // Tap zones, matching Android: left third turns back (or steps to prev panel), right third turns forward
    // (or steps to next panel), middle third toggles chrome.
    private func tapZone(x: CGFloat, width: CGFloat, in loader: PageLoader) {
        guard !isTurning else { return }
        if x < width / 3 {
            if settings.pageSlideEnabled {
                animateTapTurn(forward: rightToLeft, in: loader, width: width)
            } else {
                advance(by: rightToLeft ? 1 : -1, in: loader)
            }
        } else if x > width * 2 / 3 {
            if settings.pageSlideEnabled {
                animateTapTurn(forward: !rightToLeft, in: loader, width: width)
            } else {
                advance(by: rightToLeft ? -1 : 1, in: loader)
            }
        } else {
            withAnimation { showChrome.toggle() }
        }
    }

    private func animateTapTurn(forward: Bool, in loader: PageLoader, width: CGFloat) {
        let spreadActive = isSpreadActive(for: UIScreen.main.bounds.size)
        let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
        let target: Int
        if forward {
            target = Int(s?.after ?? Int32(page + 1))
        } else {
            let prevIndex = Int(s?.first ?? Int32(page)) - 1
            if prevIndex >= 0 {
                let prevSpread = resolveSpread(for: prevIndex, in: loader, spreadActive: spreadActive)
                target = Int(prevSpread?.first ?? Int32(prevIndex))
            } else {
                target = -1
            }
        }
        guard target >= 0, target < loader.pageCount, !isTurning else { return }
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
            if forward {
                let incoming = !rightToLeft ? nextImage : prevImage
                if let incoming { self.image = incoming }
            } else {
                let incoming = !rightToLeft ? prevImage : nextImage
                if let incoming { self.image = incoming }
            }
            page = target
            slideOffset = 0
            isTurning = false
            loadPage(loader)
        }
    }

    private var isFullPage: Bool { settings.pageSlideEnabled || step < 0 || step >= regions.count }

    private func handlePanEnd(translation: CGSize, velocity: CGSize, maxTouches: Int, in loader: PageLoader, size: CGSize) {
        if settings.pageSlideEnabled && zoom <= 1.01 {
            guard !isTurning else { return }
            let dragX = translation.width
            let velX = velocity.width
            let W = size.width

            let spreadActive = isSpreadActive(for: size)
            let s = resolveSpread(for: page, in: loader, spreadActive: spreadActive)
            let canAdvance = !rightToLeft ? (Int(s?.after ?? Int32(page + 1)) < pageCount) : (page > 0)
            let canGoBack = !rightToLeft ? (page > 0) : (Int(s?.after ?? Int32(page + 1)) < pageCount)

            let isAdvance = !rightToLeft ? (dragX < -60 || velX < -300) : (dragX > 60 || velX > 300)
            let isGoBack = !rightToLeft ? (dragX > 60 || velX > 300) : (dragX < -60 || velX < -300)

            if isAdvance && canAdvance {
                isTurning = true
                let targetOffset = !rightToLeft ? -W : W
                withAnimation(.easeOut(duration: 0.20)) {
                    slideOffset = targetOffset
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.20) {
                    let incoming = !rightToLeft ? nextImage : prevImage
                    if let incoming { self.image = incoming }
                    let nextTarget: Int
                    if !rightToLeft {
                        nextTarget = Int(s?.after ?? Int32(page + 1))
                    } else {
                        let prevIndex = Int(s?.first ?? Int32(page)) - 1
                        let prevSpread = prevIndex >= 0 ? resolveSpread(for: prevIndex, in: loader, spreadActive: spreadActive) : nil
                        nextTarget = Int(prevSpread?.first ?? Int32(prevIndex))
                    }
                    if nextTarget >= 0 && nextTarget < loader.pageCount {
                        page = nextTarget
                    }
                    slideOffset = 0
                    isTurning = false
                    loadPage(loader)
                }
            } else if isGoBack && canGoBack {
                isTurning = true
                let targetOffset = !rightToLeft ? W : -W
                withAnimation(.easeOut(duration: 0.20)) {
                    slideOffset = targetOffset
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.20) {
                    let incoming = !rightToLeft ? prevImage : nextImage
                    if let incoming { self.image = incoming }
                    let prevTarget: Int
                    if !rightToLeft {
                        let prevIndex = Int(s?.first ?? Int32(page)) - 1
                        let prevSpread = prevIndex >= 0 ? resolveSpread(for: prevIndex, in: loader, spreadActive: spreadActive) : nil
                        prevTarget = Int(prevSpread?.first ?? Int32(prevIndex))
                    } else {
                        prevTarget = Int(s?.after ?? Int32(page + 1))
                    }
                    if prevTarget >= 0 && prevTarget < loader.pageCount {
                        page = prevTarget
                    }
                    slideOffset = 0
                    isTurning = false
                    loadPage(loader)
                }
            } else {
                withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                    slideOffset = 0
                }
            }
        } else {
            if zoom > 1.01 || settings.pageFitMode == .fitWidth {
                steadyPan = pan
            } else if isFullPage {
                let dragX = translation.width
                let velX = velocity.width
                let isAdvance = !rightToLeft ? (dragX < -60 || velX < -300) : (dragX > 60 || velX > 300)
                let isGoBack = !rightToLeft ? (dragX > 60 || velX > 300) : (dragX < -60 || velX < -300)
                if isAdvance {
                    turnPage(next: !rightToLeft, in: loader)
                } else if isGoBack {
                    turnPage(next: rightToLeft, in: loader)
                }
            }
        }
    }

    private func clampPan(_ raw: CGSize, viewSize: CGSize) -> CGSize {
        let cw = viewSize.width, ch = viewSize.height
        if isSpread, let image, let secondImage,
           image.cgImage != nil, secondImage.cgImage != nil {
            let leftImg = !rightToLeft ? image : secondImage
            let rightImg = !rightToLeft ? secondImage : image
            let spread = CameraTransformKt.computeSpreadDraw(
                page1W: Int32(leftImg.cgImage?.width ?? 1),
                page1H: Int32(leftImg.cgImage?.height ?? 1),
                page2W: Int32(rightImg.cgImage?.width ?? 1),
                page2H: Int32(rightImg.cgImage?.height ?? 1),
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
        } else if let image, let cg = image.cgImage {
            let draw = CameraTransformKt.computePageDraw(
                camera: camera,
                bitmapW: Int32(cg.width),
                bitmapH: Int32(cg.height),
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

    private func advance(by delta: Int, in loader: PageLoader) {
        let next = step + delta
        if next > regions.count {
            turnPage(next: true, in: loader)
        } else if next < -1 {
            turnPage(next: false, in: loader)
        } else {
            withAnimation(Self.cameraTween) {
                step = next
                resetZoom()
            }
            persist()
        }
    }

    private func showWholePage() {
        withAnimation(Self.cameraTween) {
            step = -1
            resetZoom()
        }
        persist()
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