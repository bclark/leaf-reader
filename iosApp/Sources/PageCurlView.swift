import SwiftUI
import MetalKit
import LeafShared

enum CurlDirection: Equatable {
    case forward
    case backward
}

/// Evaluates Android FastOutSlowIn cubic-bezier(0.4, 0.0, 0.2, 1.0) easing curve.
func fastOutSlowIn(t: Double) -> Double {
    let t = min(max(t, 0), 1)
    var u = t
    for _ in 0..<5 {
        let currentX = 3 * (1 - u) * (1 - u) * u * 0.4 + 3 * (1 - u) * u * u * 0.2 + u * u * u
        let dx = 3 * (1 - u) * (1 - u) * 0.4 + 6 * (1 - u) * u * (0.2 - 0.4) + 3 * u * u * (1.0 - 0.2)
        if abs(dx) < 1e-6 { break }
        u -= (currentX - t) / dx
        u = min(max(u, 0), 1)
    }
    return 3 * (1 - u) * u * u + u * u * u
}

/// DisplayLink-driven animator for 60/120fps page curl transitions on iOS.
final class CurlAnimator: ObservableObject {
    @Published var progress: CGFloat = 0
    @Published var direction: CurlDirection? = nil
    @Published var isAnimating = false

    private var displayLink: CADisplayLink?
    private var startTime: CFTimeInterval = 0
    private var duration: CFTimeInterval = 0.26
    private var startProgress: CGFloat = 0
    private var targetProgress: CGFloat = 1
    private var onComplete: ((Bool) -> Void)?

    func start(
        from: CGFloat,
        to: CGFloat,
        duration: CFTimeInterval = 0.26,
        direction: CurlDirection,
        onComplete: @escaping (Bool) -> Void
    ) {
        stop()
        self.direction = direction
        self.startProgress = from
        self.targetProgress = to
        self.progress = from
        self.duration = max(duration, 0.05)
        self.isAnimating = true
        self.onComplete = onComplete
        self.startTime = CACurrentMediaTime()

        let link = CADisplayLink(target: self, selector: #selector(tick))
        link.add(to: .main, forMode: .common)
        self.displayLink = link
    }

    @objc private func tick() {
        guard isAnimating else { return }
        let elapsed = CACurrentMediaTime() - startTime
        let t = min(max(elapsed / duration, 0), 1)
        let eased = fastOutSlowIn(t: t)
        progress = startProgress + (targetProgress - startProgress) * CGFloat(eased)

        if t >= 1.0 {
            stop()
            let completed = (targetProgress >= 0.99)
            let cb = onComplete
            onComplete = nil
            cb?(completed)
        }
    }

    /// Immediately finishes an in-flight animation, invoking its completion handler synchronously.
    func completeImmediately() {
        guard isAnimating else { return }
        stop()
        progress = targetProgress
        let completed = (targetProgress >= 0.99)
        let cb = onComplete
        onComplete = nil
        cb?(completed)
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        isAnimating = false
    }

    func updateInteractive(direction: CurlDirection, progress: CGFloat) {
        stop()
        self.direction = direction
        self.progress = min(max(progress, 0), 1)
    }

    func reset() {
        stop()
        onComplete = nil
        direction = nil
        progress = 0
    }
}

enum TurnKind {
    case single(
        topImage: UIImage,
        bottomImage: UIImage?,
        draw: PageDraw,
        bottomDraw: PageDraw? = nil,
        isPeelingFromRight: Bool
    )
    case spread(
        spread: SpreadDraw,
        destinationSpread: SpreadDraw,
        staticImage: UIImage,
        movingFrontImage: UIImage,
        movingBackImage: UIImage?,
        underneathImage: UIImage?,
        mapping: SpreadTurnMapping,
        userScale: CGFloat,
        userPanX: CGFloat,
        userPanY: CGFloat
    )
}

final class MetalCurlUIView: UIView {
    override class var layerClass: AnyClass { CAMetalLayer.self }
    var metalLayer: CAMetalLayer { layer as! CAMetalLayer }

    private var currentKind: TurnKind?
    private var currentProgress: CGFloat = 0
    private var currentClearColor: MTLClearColor = MTLClearColor(
        red: 20.0 / 255.0,
        green: 25.0 / 255.0,
        blue: 28.0 / 255.0,
        alpha: 1.0
    )

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupLayer()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupLayer()
    }

    private func setupLayer() {
        guard let dev = PageCurlRenderer.shared.device else { return }
        metalLayer.device = dev
        metalLayer.pixelFormat = .bgra8Unorm
        metalLayer.framebufferOnly = true
        metalLayer.isOpaque = true
        metalLayer.contentsScale = UIScreen.main.scale
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 && bounds.height > 0 else { return }
        let scale = metalLayer.contentsScale
        let expectedSize = CGSize(
            width: max(bounds.width * scale, 1),
            height: max(bounds.height * scale, 1)
        )
        if metalLayer.drawableSize != expectedSize {
            metalLayer.drawableSize = expectedSize
        }
        if let kind = currentKind {
            renderNow(kind: kind, progress: currentProgress, clearColor: currentClearColor)
        }
    }

    func update(kind: TurnKind?, progress: CGFloat, clearColor: MTLClearColor) {
        self.currentKind = kind
        self.currentProgress = progress
        self.currentClearColor = clearColor

        guard let kind = kind else {
            if !isHidden {
                isHidden = true
            }
            return
        }
        if isHidden {
            isHidden = false
        }
        renderNow(kind: kind, progress: progress, clearColor: clearColor)
    }

    func render(
        kind: TurnKind,
        progress: CGFloat,
        clearColor: MTLClearColor = MTLClearColor(red: 20.0 / 255.0, green: 25.0 / 255.0, blue: 28.0 / 255.0, alpha: 1.0)
    ) {
        update(kind: kind, progress: progress, clearColor: clearColor)
    }

    private func renderNow(kind: TurnKind, progress: CGFloat, clearColor: MTLClearColor) {
        guard bounds.width > 0 && bounds.height > 0 else { return }
        let scale = metalLayer.contentsScale
        let expectedSize = CGSize(
            width: max(bounds.width * scale, 1),
            height: max(bounds.height * scale, 1)
        )
        if metalLayer.drawableSize != expectedSize {
            metalLayer.drawableSize = expectedSize
        }
        guard let drawable = metalLayer.nextDrawable() else { return }
        switch kind {
        case .single(let topImg, let bottomImg, let draw, let bottomDraw, let isPeelingFromRight):
            PageCurlRenderer.shared.renderSinglePageCurl(
                to: drawable,
                viewportSize: bounds.size,
                topImage: topImg,
                bottomImage: bottomImg,
                draw: draw,
                bottomDraw: bottomDraw,
                progress: progress,
                isPeelingFromRight: isPeelingFromRight,
                clearColor: clearColor
            )
        case .spread(
            let spread,
            let destSpread,
            let staticImg,
            let movingFrontImg,
            let movingBackImg,
            let underneathImg,
            let mapping,
            let userScale,
            let userPanX,
            let userPanY
        ):
            PageCurlRenderer.shared.renderSpreadCurl(
                to: drawable,
                viewportSize: bounds.size,
                spread: spread,
                destinationSpread: destSpread,
                staticImage: staticImg,
                movingFrontImage: movingFrontImg,
                movingBackImage: movingBackImg,
                underneathImage: underneathImg,
                mapping: mapping,
                progress: progress,
                userScale: userScale,
                userPanX: userPanX,
                userPanY: userPanY,
                clearColor: clearColor
            )
        }
    }
}

struct PageCurlView: UIViewRepresentable {
    var kind: TurnKind?
    var progress: CGFloat
    var clearColor: MTLClearColor = MTLClearColor(
        red: 20.0 / 255.0,
        green: 25.0 / 255.0,
        blue: 28.0 / 255.0,
        alpha: 1.0
    )

    func makeUIView(context: Context) -> MetalCurlUIView {
        let view = MetalCurlUIView()
        view.backgroundColor = .clear
        view.isOpaque = true
        view.isHidden = (kind == nil)
        return view
    }

    func updateUIView(_ uiView: MetalCurlUIView, context: Context) {
        uiView.update(kind: kind, progress: progress, clearColor: clearColor)
    }
}
