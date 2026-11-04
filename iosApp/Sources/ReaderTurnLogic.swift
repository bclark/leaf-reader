import Foundation
import CoreGraphics

/// Zones partitioning the screen when zoom is at 1.0x (default reading mode).
enum ReaderScreenZone: Equatable {
    case left
    case center
    case right
}

/// Pure business logic and calculation rules for Reader gestures, 3D curl turns, and UI formatting.
enum ReaderTurnLogic {
    /// Swipe threshold: drag distance must exceed 35% of screen width to commit on low-velocity release.
    static let dragThresholdFraction: CGFloat = 0.35

    /// Fling velocity threshold: >= 500 pt/s in turn direction settles forward regardless of distance.
    static let flingVelocityThreshold: CGFloat = 500.0

    /// Reverse fling tolerance: opposing velocity stronger than -400 pt/s blocks settle.
    static let reverseFlingTolerance: CGFloat = -400.0

    /// Automated curl duration: 260ms.
    static let animationDuration: Double = 0.26

    /// Minimum pan displacement required to initiate drag peel (abs(Δx) > 6 pt).
    static let minPanDisplacement: CGFloat = 6.0

    /// Maximum zoom allowing page turn gestures. When zoom > 1.05, curl is disabled.
    static let maxInteractiveZoom: CGFloat = 1.05

    /// Resolves horizontal touch position into one of three interaction zones:
    /// - Left Third: x < 1/3 W
    /// - Center Third: 1/3 W <= x <= 2/3 W
    /// - Right Third: x > 2/3 W
    static func resolveZone(x: CGFloat, width: CGFloat) -> ReaderScreenZone {
        guard width > 0 else { return .center }
        let leftBound = width / 3.0
        let rightBound = width * 2.0 / 3.0
        if x < leftBound {
            return .left
        } else if x > rightBound {
            return .right
        } else {
            return .center
        }
    }

    /// Resolves tap zone to curl direction or chrome toggle (nil):
    /// - Left Third: Back in LTR, Next in RTL
    /// - Center Third: nil (toggle chrome HUD/bars)
    /// - Right Third: Next in LTR, Back in RTL
    static func resolveTapZone(x: CGFloat, width: CGFloat, rightToLeft: Bool) -> CurlDirection? {
        switch resolveZone(x: x, width: width) {
        case .left:
            return rightToLeft ? .forward : .backward
        case .right:
            return !rightToLeft ? .forward : .backward
        case .center:
            return nil
        }
    }

    /// Resolves initial drag direction from horizontal pan translation if displacement exceeds 6 pt.
    /// In LTR: Δx < -6 pt initiates forward turn; Δx > +6 pt initiates backward turn.
    /// In RTL: Δx > +6 pt initiates forward turn; Δx < -6 pt initiates backward turn.
    static func resolveDragDirection(translationX: CGFloat, rightToLeft: Bool) -> CurlDirection? {
        guard abs(translationX) > minPanDisplacement else { return nil }
        if !rightToLeft {
            return translationX < 0 ? .forward : .backward
        } else {
            return translationX > 0 ? .forward : .backward
        }
    }

    /// Computes effective positive drag distance along the active turn direction:
    /// - LTR Forward: -Δx
    /// - LTR Backward: +Δx
    /// - RTL Forward: +Δx
    /// - RTL Backward: -Δx
    static func computeDragDistance(translationX: CGFloat, direction: CurlDirection, rightToLeft: Bool) -> CGFloat {
        let isForward = (direction == .forward)
        if !rightToLeft {
            return isForward ? -translationX : translationX
        } else {
            return isForward ? translationX : -translationX
        }
    }

    /// Computes 1:1 drag progress normalized to [0.0, 1.0]:
    /// progress = min(max(dragDistance / screenWidth, 0.0), 1.0)
    static func computeDragProgress(dragDistance: CGFloat, screenWidth: CGFloat) -> CGFloat {
        guard screenWidth > 0 else { return 0.0 }
        return min(max(dragDistance / screenWidth, 0.0), 1.0)
    }

    /// Determines whether an interactive drag-to-peel gesture should settle (commit turn) or snap back (cancel).
    /// - Settle Forward: drag distance > 35% screen width OR fling velocity > 500 pt/s in turn direction (and vel > -400).
    /// - Snap Back: <= 35% distance with low velocity.
    static func shouldSettle(dragDistance: CGFloat, screenWidth: CGFloat, velocity: CGFloat) -> Bool {
        guard screenWidth > 0 else { return false }
        let distanceThreshold = screenWidth * dragThresholdFraction
        return (dragDistance > distanceThreshold || velocity > flingVelocityThreshold) && velocity > reverseFlingTolerance
    }

    /// Formats the page status string:
    /// - Single page: "Page 1/<N>"
    /// - Spread mode: "Pages 1-2/<N>"
    static func formatPageStatus(
        page: Int,
        pageCount: Int,
        firstPage: Int = 0,
        secondPage: Int? = nil,
        isSpread: Bool = false
    ) -> String {
        if let second = secondPage, isSpread {
            return "Pages \(firstPage + 1)-\(second + 1)/\(pageCount)"
        } else {
            return "Page \(page + 1)/\(pageCount)"
        }
    }

    /// Checks if curl gestures should be inhibited by zoom level.
    static func isCurlGestureEnabled(zoom: CGFloat) -> Bool {
        return zoom <= maxInteractiveZoom
    }
}
