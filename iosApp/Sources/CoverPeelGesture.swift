import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// Quick drags scroll. Only a downward drag after the platform hold interval peels.
/// SwiftUI simultaneousGesture no longer opts into ancestor scroll gestures on newer SDKs.
struct CoverPeelGesture: UIViewRepresentable {
    struct Value {
        var translation: CGSize
        var predictedEndTranslation: CGSize
    }
    var onChanged: (Value) -> Void
    var onEnded: (Value) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> GestureView {
        let view = GestureView()
        let pan = HeldPeelPan(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        pan.maximumNumberOfTouches = 1
        pan.delegate = context.coordinator
        view.pan = pan
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ view: GestureView, context: Context) {
        context.coordinator.parent = self
    }

    /// Observe the touch start without recognizing a competing long press. This lets the
    /// existing native context menu keep a stationary hold, while a held drag can peel.
    final class HeldPeelPan: UIPanGestureRecognizer {
        private var beganAt: TimeInterval = 0
        private let holdDuration = UILongPressGestureRecognizer().minimumPressDuration
        private var stationaryHold: DispatchWorkItem?
        var isArmed: Bool { ProcessInfo.processInfo.systemUptime - beganAt >= holdDuration }
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            beganAt = ProcessInfo.processInfo.systemUptime
            super.touchesBegan(touches, with: event)
            // Context-menu presses wait for this pan to fail, and a stationary pan only fails on
            // touch-up. Fail a hold that has not started its drag shortly after arming, so the
            // menu presents while the finger is still down instead of racing the release.
            let hold = DispatchWorkItem { [weak self] in
                guard let self, self.state == .possible else { return }
                self.state = .failed
            }
            stationaryHold?.cancel()
            stationaryHold = hold
            DispatchQueue.main.asyncAfter(deadline: .now() + holdDuration * 1.5, execute: hold)
        }
        override func reset() {
            stationaryHold?.cancel()
            stationaryHold = nil
            super.reset()
        }
    }

    final class GestureView: UIView {
        var pan: UIPanGestureRecognizer?
        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard window != nil, let pan else { return }
            var ancestor = superview
            while let view = ancestor {
                if let scroll = view as? UIScrollView {
                    scroll.panGestureRecognizer.require(toFail: pan)
                    break
                }
                ancestor = view.superview
            }
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: CoverPeelGesture
        init(_ parent: CoverPeelGesture) { self.parent = parent }

        func gestureRecognizer(_ recognizer: UIGestureRecognizer,
                               shouldBeRequiredToFailBy other: UIGestureRecognizer) -> Bool {
            // Window-level system gestures keep their own priority.
            guard let otherView = other.view, !(otherView is UIWindow) else { return false }
            // Competing cover taps/context presses wait for the directional pan to fail.
            // Keep the system edge-back gesture independent and never order peer peels.
            let priority = recognizer is HeldPeelPan && !(other is HeldPeelPan)
                && !(other is UIScreenEdgePanGestureRecognizer)
            return priority
        }

        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? HeldPeelPan else { return false }
            guard pan.isArmed else { return false }
            let velocity = pan.velocity(in: pan.view)
            return velocity.y > 0 && velocity.y > abs(velocity.x)
        }

        @objc func handle(_ pan: UIPanGestureRecognizer) {
            let translation = pan.translation(in: pan.view)
            let velocity = pan.velocity(in: pan.view)
            let value = Value(translation: CGSize(width: translation.x, height: translation.y),
                predictedEndTranslation: CGSize(width: translation.x + velocity.x * 0.2,
                    height: translation.y + velocity.y * 0.2))
            switch pan.state {
            case .began, .changed: parent.onChanged(value)
            case .ended: parent.onEnded(value)
            case .cancelled, .failed:
                parent.onEnded(Value(translation: .zero, predictedEndTranslation: .zero))
            default: break
            }
        }
    }
}
