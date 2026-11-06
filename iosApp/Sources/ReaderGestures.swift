import SwiftUI
import UIKit

/// A transparent UIKit gesture layer for the reader. UIKit recognizers, configured so pinch + pan
/// recognize simultaneously while keeping tap and pan cleanly separated, giving fluid zoom, pan,
/// page turning taps, and drag flipping.
struct ReaderGestures: UIViewRepresentable {
    var size: CGSize
    var isZoomed: Bool = false
    /// Tap location (in the view) + container size, so the caller can map to left/right/middle zones.
    var onTap: (CGPoint, CGSize) -> Void
    var onDoubleTap: () -> Void
    /// Cumulative pinch scale since the gesture began (1.0 at start), and the end signal to commit.
    var onPinchChanged: (CGFloat) -> Void
    var onPinchEnded: () -> Void
    /// Cumulative pan translation since the gesture began; end reports final translation, points/s
    /// velocity, and the max simultaneous touch count (so a 1-finger flick can be told from a pinch).
    var onPanChanged: (CGSize) -> Void
    var onPanEnded: (_ translation: CGSize, _ velocity: CGSize, _ maxTouches: Int) -> Void

    var onPanBegan: (_ touches: Int) -> Void = { _ in }
    var onPanCancelled: () -> Void = {}
    var onMultipleTouches: () -> Void = {}

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = true
        view.accessibilityIdentifier = "ReaderGestureView"
        let c = context.coordinator

        let pinch = UIPinchGestureRecognizer(target: c, action: #selector(Coordinator.handlePinch(_:)))
        let pan = UIPanGestureRecognizer(target: c, action: #selector(Coordinator.handlePan(_:)))
        pan.maximumNumberOfTouches = 2
        let tap = UITapGestureRecognizer(target: c, action: #selector(Coordinator.handleTap(_:)))
        let double = UITapGestureRecognizer(target: c, action: #selector(Coordinator.handleDouble(_:)))
        double.numberOfTapsRequired = 2
        tap.require(toFail: double) // single tap fires only once a double-tap is ruled out

        c.doubleTapRecognizer = double

        pinch.delegate = c
        pan.delegate = c
        tap.delegate = c
        double.delegate = c

        for g in [pinch, pan, tap, double] as [UIGestureRecognizer] {
            g.cancelsTouchesInView = false
            view.addGestureRecognizer(g)
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: ReaderGestures
        weak var doubleTapRecognizer: UITapGestureRecognizer?
        private var maxTouches = 1
        init(_ parent: ReaderGestures) { self.parent = parent }

        // Only require double-tap failure when zoomed in (where double-tap resets zoom to 1x).
        // When at 1x zoom, double-tap fails immediately so single-tap page turns have 0ms delay
        // and rapid page-turning taps are never swallowed as double-taps.
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            if gestureRecognizer === doubleTapRecognizer {
                return parent.isZoomed
            }
            return true
        }

        // Only allow pinch and pan to run together (for simultaneous 2-finger zoom and repositioning).
        // Tap must NOT recognize simultaneously with pan, ensuring clean arbitration.
        func gestureRecognizer(_ g: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            if (g is UIPinchGestureRecognizer && other is UIPanGestureRecognizer) ||
               (g is UIPanGestureRecognizer && other is UIPinchGestureRecognizer) {
                return true
            }
            return false
        }

        @objc func handlePinch(_ g: UIPinchGestureRecognizer) {
            switch g.state {
            case .began: parent.onMultipleTouches()
            case .changed: parent.onPinchChanged(g.scale)
            case .ended, .cancelled: parent.onPinchEnded()
            default: break
            }
        }

        @objc func handlePan(_ g: UIPanGestureRecognizer) {
            guard let view = g.view else { return }
            let t = g.translation(in: view)
            switch g.state {
            case .began:
                maxTouches = g.numberOfTouches
                parent.onPanBegan(maxTouches)
                parent.onPanChanged(CGSize(width: t.x, height: t.y))
            case .changed:
                maxTouches = max(maxTouches, g.numberOfTouches)
                if maxTouches > 1 { parent.onMultipleTouches() }
                parent.onPanChanged(CGSize(width: t.x, height: t.y))
            case .cancelled, .failed:
                parent.onPanCancelled()
                maxTouches = 1
            case .ended:
                let v = g.velocity(in: view)
                parent.onPanEnded(CGSize(width: t.x, height: t.y), CGSize(width: v.x, height: v.y), maxTouches)
                maxTouches = 1
            default: break
            }
        }

        @objc func handleTap(_ g: UITapGestureRecognizer) {
            guard let view = g.view else { return }
            let loc = g.location(in: view)
            let viewSize = (view.bounds.width > 0 && view.bounds.height > 0) ? view.bounds.size : parent.size
            parent.onTap(loc, viewSize)
        }

        @objc func handleDouble(_ g: UITapGestureRecognizer) { parent.onDoubleTap() }

        func simulateTap(at location: CGPoint, in viewSize: CGSize) {
            parent.onTap(location, viewSize)
        }

        func simulatePanChanged(_ translation: CGSize) {
            parent.onPanChanged(translation)
        }

        func simulatePanEnded(translation: CGSize, velocity: CGSize, maxTouches: Int = 1) {
            parent.onPanEnded(translation, velocity, maxTouches)
        }
    }
}
