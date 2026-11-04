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
}
