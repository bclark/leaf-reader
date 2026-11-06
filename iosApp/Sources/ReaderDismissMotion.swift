import SwiftUI
import LeafShared

/// Native gesture ownership and interruptible motion; release policy stays in shared Kotlin.
@MainActor
final class ReaderDismissMotion: ObservableObject {
    enum Intent { case undecided, dismiss, horizontal, pan, cancelled }
    @Published private(set) var offset: CGSize = .zero
    @Published private(set) var committed = false
    private(set) var intent: Intent = .undecided
    private var eligible = false
    private var engaged = false
    private var origin: CGSize = .zero
    private var start: CGSize = .zero
    private var animation: Task<Void, Never>?

    func begin(eligible: Bool, touches: Int) {
        guard !committed else { return }
        self.eligible = eligible && touches == 1
        intent = touches == 1 ? .undecided : .cancelled
        engaged = false
    }

    func change(_ translation: CGSize) {
        guard !committed, translation != .zero else { return }
        let policy = ReaderDismissGesture.shared
        if intent == .undecided {
            if eligible && policy.isDownward(deltaX: Float(translation.width), deltaY: Float(translation.height)) {
                intent = .dismiss
            } else {
                intent = abs(translation.width) > abs(translation.height) ? .horizontal : .pan
            }
        }
        guard intent == .dismiss else { return }
        let distance = CGFloat(policy.ENGAGE_DISTANCE_DP)
        if !engaged {
            guard translation.height >= distance,
                  policy.isDownward(deltaX: Float(translation.width), deltaY: Float(translation.height)) else { return }
            animation?.cancel()
            animation = nil
            origin = offset
            start = CGSize(width: translation.width * distance / translation.height, height: distance)
            engaged = true
        }
        let resistance = CGFloat(policy.DRAG_RESISTANCE)
        offset = CGSize(width: origin.width + (translation.width - start.width) * resistance,
                        height: max(0, origin.height + (translation.height - start.height) * resistance))
    }

    func cancelGesture() {
        guard !committed, intent != .cancelled else { return }
        intent = .cancelled
        engaged = false
        springBack()
    }

    func release(velocity: CGSize, height: CGFloat, dismiss: @escaping () -> Void) {
        guard !committed else { return }
        guard intent == .dismiss, engaged,
              ReaderDismissGesture.shared.shouldDismiss(deltaY: Float(offset.height),
                                                       viewportHeight: Float(height),
                                                       velocityY: Float(velocity.height)) else {
            springBack()
            return
        }
        committed = true
        let slope = min(max(velocity.height > 0 ? velocity.width / velocity.height
                           : offset.width / max(offset.height, 1), -2), 2)
        let target = CGSize(width: offset.width + slope * height * 1.3,
                            height: offset.height + height * 1.3)
        animate(to: target, duration: 0.28, spring: false, completion: dismiss)
    }

    private func springBack() {
        guard offset != .zero else { return }
        animate(to: .zero, duration: 0.55, spring: true, completion: {})
    }

    private func animate(to target: CGSize, duration: Double, spring: Bool, completion: @escaping () -> Void) {
        animation?.cancel()
        let from = offset
        animation = Task { [weak self] in
            let began = Date.timeIntervalSinceReferenceDate
            while !Task.isCancelled {
                let t = min((Date.timeIntervalSinceReferenceDate - began) / duration, 1)
                // Damped spring with the same 0.78 damping ratio as Android.
                let decay = exp(-0.78 * 12 * t)
                let phase = sqrt(1 - 0.78 * 0.78) * 12 * t
                let fraction = spring
                    ? 1 - decay * (cos(phase) + 0.78 / sqrt(1 - 0.78 * 0.78) * sin(phase))
                    : 1 - pow(1 - t, 3)
                self?.offset = CGSize(width: from.width + (target.width - from.width) * fraction,
                                      height: from.height + (target.height - from.height) * fraction)
                if t >= 1 {
                    self?.offset = target
                    self?.animation = nil
                    completion()
                    return
                }
                do { try await Task.sleep(nanoseconds: 16_666_667) } catch { return }
            }
        }
    }

    func reset() {
        animation?.cancel()
        animation = nil
        offset = .zero
        committed = false
        engaged = false
        intent = .cancelled
    }
}
