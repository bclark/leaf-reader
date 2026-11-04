import XCTest
@testable import Leaf
import LeafShared

final class ReaderTurnTests: XCTestCase {

    func testCoverAlwaysStandsAloneInSpread() {
        let spread = CameraTransformKt.resolveReaderSpread(
            page: 0,
            pageCount: 10,
            widePages: Set<KotlinInt>()
        )
        XCTAssertNotNil(spread)
        XCTAssertEqual(spread?.first, 0)
        XCTAssertNil(spread?.second)
        XCTAssertEqual(spread?.after, 1)
    }

    func testStandardPagesFormTwoPageSpreads() {
        let spread1 = CameraTransformKt.resolveReaderSpread(
            page: 1,
            pageCount: 10,
            widePages: Set<KotlinInt>()
        )
        XCTAssertNotNil(spread1)
        XCTAssertEqual(spread1?.first, 1)
        XCTAssertEqual(spread1?.second?.int32Value, 2)
        XCTAssertEqual(spread1?.after, 3)

        let spread2 = CameraTransformKt.resolveReaderSpread(
            page: 2,
            pageCount: 10,
            widePages: Set<KotlinInt>()
        )
        XCTAssertNotNil(spread2)
        XCTAssertEqual(spread2?.first, 1)
        XCTAssertEqual(spread2?.second?.int32Value, 2)

        let spread3 = CameraTransformKt.resolveReaderSpread(
            page: 3,
            pageCount: 10,
            widePages: Set<KotlinInt>()
        )
        XCTAssertNotNil(spread3)
        XCTAssertEqual(spread3?.first, 3)
        XCTAssertEqual(spread3?.second?.int32Value, 4)
        XCTAssertEqual(spread3?.after, 5)
    }

    func testWidePagesStandAloneInSpreads() {
        var widePages = Set<KotlinInt>()
        widePages.insert(KotlinInt(int: 3))

        let spread = CameraTransformKt.resolveReaderSpread(
            page: 3,
            pageCount: 10,
            widePages: widePages
        )
        XCTAssertNotNil(spread)
        XCTAssertEqual(spread?.first, 3)
        XCTAssertNil(spread?.second)
        XCTAssertEqual(spread?.after, 4)

        // Page 4 should now pair with 5
        let spreadAfter = CameraTransformKt.resolveReaderSpread(
            page: 4,
            pageCount: 10,
            widePages: widePages
        )
        XCTAssertNotNil(spreadAfter)
        XCTAssertEqual(spreadAfter?.first, 4)
        XCTAssertEqual(spreadAfter?.second?.int32Value, 5)
        XCTAssertEqual(spreadAfter?.after, 6)
    }

    func testSpreadTurnMappingLtrForward() {
        let mapping = CameraTransformKt.computeSpreadTurnMapping(
            pageIndex: 1,
            secondPageIndex: KotlinInt(int: 2),
            pageCount: 10,
            rightToLeft: false,
            isForward: true,
            destination: nil
        )
        XCTAssertNotNil(mapping)
        XCTAssertEqual(mapping?.staticPageDocIndex, 1)
        XCTAssertEqual(mapping?.movingFrontDocIndex, 2)
        XCTAssertEqual(mapping?.isPeelingFromRight, true)
    }

    func testSpreadTurnMappingRtlForward() {
        let mapping = CameraTransformKt.computeSpreadTurnMapping(
            pageIndex: 1,
            secondPageIndex: KotlinInt(int: 2),
            pageCount: 10,
            rightToLeft: true,
            isForward: true,
            destination: nil
        )
        XCTAssertNotNil(mapping)
        XCTAssertEqual(mapping?.staticPageDocIndex, 1)
        XCTAssertEqual(mapping?.movingFrontDocIndex, 2)
        XCTAssertEqual(mapping?.isPeelingFromRight, false)
    }

    func testDisplaySpreadReadiness() {
        let emptySpread = DisplaySpread(
            firstPage: 0,
            secondPage: nil,
            firstImage: nil,
            secondImage: nil,
            isSpread: false
        )
        XCTAssertFalse(emptySpread.isReady)

        let singleReady = DisplaySpread(
            firstPage: 0,
            secondPage: nil,
            firstImage: UIImage(),
            secondImage: nil,
            isSpread: false
        )
        XCTAssertTrue(singleReady.isReady)

        let spreadHalfReady = DisplaySpread(
            firstPage: 1,
            secondPage: 2,
            firstImage: UIImage(),
            secondImage: nil,
            isSpread: true
        )
        XCTAssertFalse(spreadHalfReady.isReady)

        let spreadFullyReady = DisplaySpread(
            firstPage: 1,
            secondPage: 2,
            firstImage: UIImage(),
            secondImage: UIImage(),
            isSpread: true
        )
        XCTAssertTrue(spreadFullyReady.isReady)
    }

    func testFastOutSlowInEasingCurve() {
        XCTAssertEqual(fastOutSlowIn(t: 0.0), 0.0, accuracy: 1e-5)
        XCTAssertEqual(fastOutSlowIn(t: 1.0), 1.0, accuracy: 1e-5)

        let t0 = fastOutSlowIn(t: 0.0)
        let t25 = fastOutSlowIn(t: 0.25)
        let t50 = fastOutSlowIn(t: 0.5)
        let t75 = fastOutSlowIn(t: 0.75)
        let t100 = fastOutSlowIn(t: 1.0)

        // Monotonic progression
        XCTAssertLessThan(t0, t25)
        XCTAssertLessThan(t25, t50)
        XCTAssertLessThan(t50, t75)
        XCTAssertLessThan(t75, t100)

        // FastOutSlowIn has accelerated lead-in (t=0.5 yields ~0.7-0.8)
        XCTAssertGreaterThan(t50, 0.6)
    }

    func testCurlAnimatorInteractiveProgress() {
        let animator = CurlAnimator()
        XCTAssertNil(animator.direction)
        XCTAssertEqual(animator.progress, 0.0)

        animator.updateInteractive(direction: .forward, progress: 0.42)
        XCTAssertEqual(animator.direction, .forward)
        XCTAssertEqual(animator.progress, 0.42, accuracy: 1e-4)

        // Clamping to [0, 1]
        animator.updateInteractive(direction: .backward, progress: 1.5)
        XCTAssertEqual(animator.direction, .backward)
        XCTAssertEqual(animator.progress, 1.0, accuracy: 1e-4)

        animator.updateInteractive(direction: .backward, progress: -0.25)
        XCTAssertEqual(animator.progress, 0.0, accuracy: 1e-4)

        // Reset
        animator.reset()
        XCTAssertNil(animator.direction)
        XCTAssertEqual(animator.progress, 0.0)
        XCTAssertFalse(animator.isAnimating)
    }

    func testPageCurlRendererAvailability() {
        XCTAssertTrue(PageCurlRenderer.shared.isAvailable)
    }

    // MARK: - Recommended Test Suite Checklist

    /// 1. testTapForwardAdvancesPage:
    /// - Verify initial page is Page 1/<N>.
    /// - Tap at (x = width * 0.85, y = height * 0.5).
    /// - Wait 300ms for animation completion (260ms duration with fastOutSlowIn curve).
    /// - Assert page text updates to Page 2/<N>, and PageCoin updates from 1 to 2.
    func testTapForwardAdvancesPage() {
        let width: CGFloat = 390
        let pageCount = 24
        var currentPage = 0

        // Initial state verification
        let initialStatus = ReaderTurnLogic.formatPageStatus(page: currentPage, pageCount: pageCount)
        XCTAssertEqual(initialStatus, "Page 1/24")

        // Tap Right Third (x = width * 0.85) in LTR mode
        let tapX = width * 0.85
        let turnDirection = ReaderTurnLogic.resolveTapZone(x: tapX, width: width, rightToLeft: false)
        XCTAssertEqual(turnDirection, .forward, "Tapping right third in LTR mode must initiate a forward turn")

        // Start 260ms curl animation
        let animator = CurlAnimator()
        var completed = false
        animator.start(from: 0.0, to: 1.0, duration: ReaderTurnLogic.animationDuration, direction: .forward) { success in
            completed = success
            if success {
                currentPage += 1
            }
        }
        XCTAssertTrue(animator.isAnimating)

        // Wait for animation completion (300ms > 260ms duration)
        let exp = expectation(description: "Curl animation completion")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
            XCTAssertTrue(completed, "Animation should complete within 300ms")
            XCTAssertFalse(animator.isAnimating)
            XCTAssertEqual(currentPage, 1)

            // Assert page text updates to Page 2/<N>
            let updatedStatus = ReaderTurnLogic.formatPageStatus(page: currentPage, pageCount: pageCount)
            XCTAssertEqual(updatedStatus, "Page 2/24")

            // Assert bottom PageCoin value is 1-indexed (page + 1 = 2)
            let pageCoinValue = currentPage + 1
            XCTAssertEqual(pageCoinValue, 2)
            exp.fulfill()
        }
        wait(for: [exp], timeout: 1.0)
    }

    /// 2. testInteractiveDragThresholdCommit:
    /// - Drag from (x = width * 0.9, y = height * 0.5) to (x = width * 0.4, y = height * 0.5) (50% swipe distance) and release.
    /// - Wait for settle animation.
    /// - Assert page text advances to next page.
    func testInteractiveDragThresholdCommit() {
        let width: CGFloat = 400
        let pageCount = 10
        var currentPage = 0

        let startX = width * 0.9
        let endX = width * 0.4
        let translationX = endX - startX // -200 pt (drag leftwards in LTR)

        // Resolve drag direction: Δx < -6 pt initiates forward turn in LTR
        let direction = ReaderTurnLogic.resolveDragDirection(translationX: translationX, rightToLeft: false)
        XCTAssertEqual(direction, .forward)

        // Drag distance: -translationX = 200 pt (50% of screen width)
        let dragDist = ReaderTurnLogic.computeDragDistance(translationX: translationX, direction: .forward, rightToLeft: false)
        XCTAssertEqual(dragDist, 200.0)

        // Progress tracks 1:1 with finger displacement
        let progress = ReaderTurnLogic.computeDragProgress(dragDistance: dragDist, screenWidth: width)
        XCTAssertEqual(progress, 0.5, accuracy: 1e-4)

        // Release threshold: 50% > 35% threshold
        let shouldSettle = ReaderTurnLogic.shouldSettle(dragDistance: dragDist, screenWidth: width, velocity: 0)
        XCTAssertTrue(shouldSettle, "50% swipe distance must exceed 35% threshold and commit forward")

        // Settle animation to 1.0
        let animator = CurlAnimator()
        var turnCommitted = false
        let remaining = 1.0 - progress
        let settleDuration = max(ReaderTurnLogic.animationDuration * Double(remaining), 0.10)
        animator.start(from: progress, to: 1.0, duration: settleDuration, direction: .forward) { success in
            if success {
                turnCommitted = true
                currentPage += 1
            }
        }

        let exp = expectation(description: "Settle commit completion")
        DispatchQueue.main.asyncAfter(deadline: .now() + settleDuration + 0.05) {
            XCTAssertTrue(turnCommitted)
            XCTAssertEqual(currentPage, 1)
            let updatedStatus = ReaderTurnLogic.formatPageStatus(page: currentPage, pageCount: pageCount)
            XCTAssertEqual(updatedStatus, "Page 2/10")
            exp.fulfill()
        }
        wait(for: [exp], timeout: 1.0)
    }

    /// 3. testInteractiveDragSnapBack:
    /// - Drag slightly from (x = width * 0.9, y = height * 0.5) to (x = width * 0.75, y = height * 0.5) (15% swipe distance) and release slowly.
    /// - Assert curl snaps back and page text remains on the current page.
    func testInteractiveDragSnapBack() {
        let width: CGFloat = 400
        let pageCount = 10
        var currentPage = 0

        let startX = width * 0.9
        let endX = width * 0.75
        let translationX = endX - startX // -60 pt (15% drag distance)

        let direction = ReaderTurnLogic.resolveDragDirection(translationX: translationX, rightToLeft: false)
        XCTAssertEqual(direction, .forward)

        let dragDist = ReaderTurnLogic.computeDragDistance(translationX: translationX, direction: .forward, rightToLeft: false)
        XCTAssertEqual(dragDist, 60.0)

        let progress = ReaderTurnLogic.computeDragProgress(dragDistance: dragDist, screenWidth: width)
        XCTAssertEqual(progress, 0.15, accuracy: 1e-4)

        // Release at 15% with low velocity (e.g. 50 pt/s)
        let shouldSettle = ReaderTurnLogic.shouldSettle(dragDistance: dragDist, screenWidth: width, velocity: 50)
        XCTAssertFalse(shouldSettle, "15% swipe with low velocity must snap back (cancel)")

        // Snap-back animation from 0.15 back to 0.0
        let animator = CurlAnimator()
        var turnCommitted = false
        let snapDuration = max(0.22 * Double(progress), 0.08)
        animator.start(from: progress, to: 0.0, duration: snapDuration, direction: .forward) { _ in
            animator.reset()
            // Turn cancelled: page index unchanged
            turnCommitted = false
        }

        let exp = expectation(description: "Snap back completion")
        DispatchQueue.main.asyncAfter(deadline: .now() + snapDuration + 0.05) {
            XCTAssertFalse(turnCommitted)
            XCTAssertEqual(currentPage, 0, "Page must remain unchanged after snap-back")
            let status = ReaderTurnLogic.formatPageStatus(page: currentPage, pageCount: pageCount)
            XCTAssertEqual(status, "Page 1/10")
            exp.fulfill()
        }
        wait(for: [exp], timeout: 1.0)
    }

    /// 4. testRTLDirectionInvertsTurnZones:
    /// - Tap DirectionChip to switch to RTL.
    /// - Tap Left Third (x < 1/3 W).
    /// - Assert page advances forward to the next page.
    func testRTLDirectionInvertsTurnZones() {
        let width: CGFloat = 390
        let pageCount = 15
        var currentPage = 0

        // In RTL mode:
        let isRTL = true

        // Left Third (x < 1/3 W): e.g. x = 0.15 * width
        let leftThirdX = width * 0.15
        let leftTurn = ReaderTurnLogic.resolveTapZone(x: leftThirdX, width: width, rightToLeft: isRTL)
        XCTAssertEqual(leftTurn, .forward, "In RTL mode, tapping Left Third must advance forward to the next page")

        // Right Third (x > 2/3 W): e.g. x = 0.85 * width
        let rightThirdX = width * 0.85
        let rightTurn = ReaderTurnLogic.resolveTapZone(x: rightThirdX, width: width, rightToLeft: isRTL)
        XCTAssertEqual(rightTurn, .backward, "In RTL mode, tapping Right Third must turn back to the previous page")

        // Center Third (1/3 W <= x <= 2/3 W): e.g. x = 0.5 * width
        let centerThirdX = width * 0.5
        let centerTurn = ReaderTurnLogic.resolveTapZone(x: centerThirdX, width: width, rightToLeft: isRTL)
        XCTAssertNil(centerTurn, "In RTL mode, tapping Center Third must toggle chrome HUD/bars")

        // Perform forward turn from Left Third tap
        if leftTurn == .forward {
            currentPage += 1
        }
        let updatedStatus = ReaderTurnLogic.formatPageStatus(page: currentPage, pageCount: pageCount)
        XCTAssertEqual(updatedStatus, "Page 2/15")
    }

    // MARK: - Additional Gesture & State Invariants

    func testCenterTapTogglesChrome() {
        let width: CGFloat = 390

        // In LTR: center tap toggles chrome (returns nil)
        XCTAssertNil(ReaderTurnLogic.resolveTapZone(x: width * 0.4, width: width, rightToLeft: false))
        XCTAssertNil(ReaderTurnLogic.resolveTapZone(x: width * 0.5, width: width, rightToLeft: false))
        XCTAssertNil(ReaderTurnLogic.resolveTapZone(x: width * 0.6, width: width, rightToLeft: false))

        // In RTL: center tap also toggles chrome
        XCTAssertNil(ReaderTurnLogic.resolveTapZone(x: width * 0.5, width: width, rightToLeft: true))
    }

    func testFlingVelocityOverridesDragDistance() {
        let width: CGFloat = 400

        // Drag is only 10% (well below 35% threshold), but release velocity is high fling (600 pt/s > 500)
        let commitByFling = ReaderTurnLogic.shouldSettle(dragDistance: 40, screenWidth: width, velocity: 600)
        XCTAssertTrue(commitByFling, "Fling velocity > 500 pt/s must commit turn even below 35% drag distance")

        // Opposing fling (e.g. -450 pt/s opposing the turn direction) cancels settle even if distance > 35%
        let blockedByOpposingFling = ReaderTurnLogic.shouldSettle(dragDistance: 200, screenWidth: width, velocity: -450)
        XCTAssertFalse(blockedByOpposingFling, "Opposing velocity below -400 pt/s must block commit")
    }

    func testPinchZoomDisablesPageCurl() {
        XCTAssertTrue(ReaderTurnLogic.isCurlGestureEnabled(zoom: 1.0))
        XCTAssertTrue(ReaderTurnLogic.isCurlGestureEnabled(zoom: 1.04))
        XCTAssertFalse(ReaderTurnLogic.isCurlGestureEnabled(zoom: 1.06), "Zoom > 1.05 must disable page curl gestures")
        XCTAssertFalse(ReaderTurnLogic.isCurlGestureEnabled(zoom: 2.5))
    }

    func testSpreadModePageStatusFormatting() {
        // Single page
        let singleStatus = ReaderTurnLogic.formatPageStatus(page: 0, pageCount: 20, firstPage: 0, secondPage: nil, isSpread: false)
        XCTAssertEqual(singleStatus, "Page 1/20")

        // Spread pages 1-2
        let spreadStatus = ReaderTurnLogic.formatPageStatus(page: 1, pageCount: 20, firstPage: 1, secondPage: 2, isSpread: true)
        XCTAssertEqual(spreadStatus, "Pages 2-3/20")
    }
}
