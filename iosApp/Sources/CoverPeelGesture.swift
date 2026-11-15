import SwiftUI
import UIKit

/// A card owns downward peeling; its enclosing scroll view owns other drag directions.
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
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        pan.maximumNumberOfTouches = 1
        pan.delegate = context.coordinator
        view.pan = pan
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ view: GestureView, context: Context) {
        context.coordinator.parent = self
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

        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? UIPanGestureRecognizer else { return false }
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
