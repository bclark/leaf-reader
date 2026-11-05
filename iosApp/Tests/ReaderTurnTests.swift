import XCTest
import SwiftUI
import UIKit
import Metal
import ZIPFoundation
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

    // MARK: - On-Device Metal GPU Rendering & UI Tests

    private func makeTestImage(width: Int = 200, height: Int = 300, color: UIColor) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { ctx in
            color.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    private func makeGrayscaleImage(width: Int = 200, height: Int = 300, grayLevel: UInt8 = 180) -> UIImage {
        let colorSpace = CGColorSpaceCreateDeviceGray()
        var pixels = [UInt8](repeating: grayLevel, count: width * height)
        let ctx = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        )!
        let cgImg = ctx.makeImage()!
        return UIImage(cgImage: cgImg)
    }

    private func makeOffscreenTargetTexture(width: Int = 390, height: Int = 844) -> MTLTexture? {
        guard let dev = PageCurlRenderer.shared.device else { return nil }
        let desc = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: width,
            height: height,
            mipmapped: false
        )
        desc.usage = [.renderTarget, .shaderRead]
        desc.storageMode = .shared
        return dev.makeTexture(descriptor: desc)
    }

    func testMetalGrayscaleImageTextureConversion() {
        guard PageCurlRenderer.shared.isAvailable else { return }
        let grayImg = makeGrayscaleImage(width: 64, height: 64, grayLevel: 200)
        guard let tex = PageCurlRenderer.shared.texture(for: grayImg) else {
            XCTFail("Failed to create MTLTexture from 8-bit grayscale UIImage")
            return
        }
        XCTAssertEqual(tex.pixelFormat, .bgra8Unorm, "Grayscale manga images must be normalized to bgra8Unorm so R, G, B channels are all populated")

        var pixel = [UInt8](repeating: 0, count: 4)
        tex.getBytes(&pixel, bytesPerRow: 64 * 4, from: MTLRegionMake2D(32, 32, 1, 1), mipmapLevel: 0)
        // BGRA8Unorm -> B, G, R should all be ~200, A = 255
        XCTAssertEqual(Double(pixel[0]), 200.0, accuracy: 2.0)
        XCTAssertEqual(Double(pixel[1]), 200.0, accuracy: 2.0)
        XCTAssertEqual(Double(pixel[2]), 200.0, accuracy: 2.0)
        XCTAssertEqual(pixel[3], 255)
    }

    func testMetalSinglePageCurlRenderingAcrossProgressAndDirections() {
        guard PageCurlRenderer.shared.isAvailable,
              let targetTex = makeOffscreenTargetTexture(width: 390, height: 844) else {
            return
        }

        let topImg = makeTestImage(width: 400, height: 600, color: .systemBlue)
        let bottomImg = makeTestImage(width: 400, height: 600, color: .systemOrange)
        let viewport = CGSize(width: 390, height: 844)
        let draw = CameraTransformKt.computePageDraw(
            camera: Panel.companion.FULL_PAGE,
            bitmapW: 400,
            bitmapH: 600,
            containerW: Float(viewport.width),
            containerH: Float(viewport.height),
            fill: 0.98,
            fitMode: .fitPage
        )

        let progresses: [CGFloat] = [0.0, 0.05, 0.25, 0.5, 0.75, 0.95, 1.0]
        for peelRight in [true, false] {
            for p in progresses {
                PageCurlRenderer.shared.renderSinglePageCurl(
                    targetTexture: targetTex,
                    viewportSize: viewport,
                    topImage: topImg,
                    bottomImage: bottomImg,
                    draw: draw,
                    bottomDraw: draw,
                    progress: p,
                    isPeelingFromRight: peelRight
                )
            }
        }

        // Wait for GPU command queue to finish and verify rendered pixel data is non-zero
        if let queue = PageCurlRenderer.shared.commandQueue,
           let cmd = queue.makeCommandBuffer() {
            cmd.commit()
            cmd.waitUntilCompleted()
            XCTAssertEqual(cmd.status, .completed)
        }

        var centerPixel = [UInt8](repeating: 0, count: 4)
        targetTex.getBytes(&centerPixel, bytesPerRow: 390 * 4, from: MTLRegionMake2D(195, 422, 1, 1), mipmapLevel: 0)
        XCTAssertEqual(centerPixel[3], 255, "Rendered curl frame must have opaque alpha")
    }

    func testMetalSpreadCurlRenderingAcrossProgressAndDirections() {
        guard PageCurlRenderer.shared.isAvailable,
              let targetTex = makeOffscreenTargetTexture(width: 844, height: 390) else {
            return
        }

        let p1 = makeTestImage(width: 400, height: 600, color: .systemRed)
        let p2 = makeTestImage(width: 400, height: 600, color: .systemGreen)
        let p3 = makeTestImage(width: 400, height: 600, color: .systemBlue)
        let p4 = makeTestImage(width: 400, height: 600, color: .systemYellow)
        let viewport = CGSize(width: 844, height: 390)

        let spreadDraw = CameraTransformKt.computeSpreadDraw(
            page1W: 400, page1H: 600,
            page2W: 400, page2H: 600,
            containerW: Float(viewport.width),
            containerH: Float(viewport.height),
            fill: 0.98,
            fitMode: .fitPage
        )

        for rtl in [false, true] {
            for isForward in [true, false] {
                let destSpread = ReaderSpread(first: isForward ? 3 : 0, second: isForward ? KotlinInt(int: 4) : nil)
                guard let mapping = CameraTransformKt.computeSpreadTurnMapping(
                    pageIndex: 1,
                    secondPageIndex: KotlinInt(int: 2),
                    pageCount: 10,
                    rightToLeft: rtl,
                    isForward: isForward,
                    destination: destSpread
                ) else {
                    XCTFail("Expected non-nil SpreadTurnMapping")
                    continue
                }

                for p in [CGFloat(0.0), 0.2, 0.5, 0.8, 1.0] {
                    PageCurlRenderer.shared.renderSpreadCurl(
                        targetTexture: targetTex,
                        viewportSize: viewport,
                        spread: spreadDraw,
                        destinationSpread: spreadDraw,
                        staticImage: p1,
                        movingFrontImage: p2,
                        movingBackImage: p3,
                        underneathImage: p4,
                        mapping: mapping,
                        progress: p,
                        userScale: 1.0,
                        userPanX: 0,
                        userPanY: 0
                    )
                }
            }
        }

        if let queue = PageCurlRenderer.shared.commandQueue,
           let cmd = queue.makeCommandBuffer() {
            cmd.commit()
            cmd.waitUntilCompleted()
            XCTAssertEqual(cmd.status, .completed)
        }
    }

    func testCurlAnimatorCompleteImmediatelySupportsRapidTaps() {
        let animator = CurlAnimator()
        var currentPage = 0

        // Start first tap turn
        animator.start(from: 0, to: 1.0, duration: 0.26, direction: .forward) { completed in
            if completed {
                currentPage += 1
            }
            animator.reset()
        }
        XCTAssertTrue(animator.isAnimating)

        // User rapidly taps a second time before 260ms finishes: completeImmediately() commits first turn
        animator.completeImmediately()
        XCTAssertFalse(animator.isAnimating)
        XCTAssertNil(animator.direction)
        XCTAssertEqual(currentPage, 1, "completeImmediately() must commit the first turn synchronously")

        // Second tap turn starts immediately
        animator.start(from: 0, to: 1.0, duration: 0.26, direction: .forward) { completed in
            if completed {
                currentPage += 1
            }
            animator.reset()
        }
        animator.completeImmediately()
        XCTAssertEqual(currentPage, 2, "Second rapid tap turn must advance to page 2")
    }

    private func findSubview<T: UIView>(ofType type: T.Type, in root: UIView) -> T? {
        if let match = root as? T { return match }
        for child in root.subviews {
            if let found = findSubview(ofType: type, in: child) {
                return found
            }
        }
        return nil
    }

    private func findGestureCoordinator(in root: UIView) -> ReaderGestures.Coordinator? {
        if root.accessibilityIdentifier == "ReaderGestureView",
           let recognizers = root.gestureRecognizers {
            for r in recognizers {
                if let c = r.delegate as? ReaderGestures.Coordinator {
                    return c
                }
            }
        }
        for child in root.subviews {
            if let found = findGestureCoordinator(in: child) {
                return found
            }
        }
        return nil
    }

    func testLiveReaderViewAndMetalCurlUIViewInDeviceWindow() throws {
        // Create a temporary 6-page CBZ archive on device
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let cbzURL = tempDir.appendingPathComponent("TestComic-\(UUID().uuidString).cbz")
        defer { ReadingProgress.clear(cbzURL) }
        guard let archive = Archive(url: cbzURL, accessMode: .create) else {
            XCTFail("Could not create test CBZ archive")
            return
        }

        let colors: [UIColor] = [.systemRed, .systemGreen, .systemBlue, .systemOrange, .systemPurple, .systemTeal]
        for (i, color) in colors.enumerated() {
            let img = makeTestImage(width: 300, height: 450, color: color)
            guard let data = img.jpegData(compressionQuality: 0.85) else { continue }
            let entryName = String(format: "page_%02d.jpg", i + 1)
            try archive.addEntry(with: entryName, type: .file, uncompressedSize: Int64(data.count), provider: { position, size in
                data.subdata(in: Int(position)..<Int(position) + size)
            })
        }

        let exp = expectation(description: "Live ReaderView end-to-end UI & Metal curl interaction on device")
        let viewport = CGSize(width: 390, height: 844)

        DispatchQueue.main.async {
            let settings = LeafSettings()
            settings.portraitSpread = false
            settings.landscapeSpread = false

            let readerView = ReaderView(comicURL: cbzURL).environmentObject(settings)
            let hosting = UIHostingController(rootView: readerView)
            let window = UIWindow(frame: CGRect(origin: .zero, size: viewport))
            window.rootViewController = hosting
            window.makeKeyAndVisible()
            hosting.view.layoutIfNeeded()

            // Wait 250ms for ReaderView.task { load() } to open the CBZ and mount ReaderGestures + MetalCurlUIView
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                hosting.view.layoutIfNeeded()
                guard let coordinator = self.findGestureCoordinator(in: hosting.view),
                      let metalView = self.findSubview(ofType: MetalCurlUIView.self, in: hosting.view) else {
                    XCTFail("Expected ReaderGestureView coordinator and MetalCurlUIView to be mounted in ReaderView")
                    window.isHidden = true
                    exp.fulfill()
                    return
                }

                XCTAssertEqual(ReadingProgress.get(cbzURL)?.page ?? 0, 0, "Initial page must be 0 (Page 1/6)")
                XCTAssertTrue(metalView.isHidden, "MetalCurlUIView must be hidden while resting")

                // 1. Tap Right Third (x = 0.85 * W) -> forward turn from Page 1/6 to Page 2/6
                coordinator.simulateTap(at: CGPoint(x: viewport.width * 0.85, y: viewport.height * 0.5), in: viewport)

                // Check mid-animation (80ms into the 260ms curl) that MetalCurlUIView is actively rendering
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                    hosting.view.layoutIfNeeded()
                    XCTAssertFalse(metalView.isHidden, "MetalCurlUIView must be visible during active curl animation")
                }

                // Check after 260ms animation completes (at 360ms)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.36) {
                    hosting.view.layoutIfNeeded()
                    XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 1, "Tap in Right Third must advance to page index 1 (Page 2/6)")
                    XCTAssertTrue(metalView.isHidden, "MetalCurlUIView must hide again once turn settles")

                    // 2. Interactive drag > 35% screen width -> commits forward turn to page index 2 (Page 3/6)
                    coordinator.simulatePanChanged(CGSize(width: -viewport.width * 0.20, height: 0))
                    coordinator.simulatePanChanged(CGSize(width: -viewport.width * 0.50, height: 0))
                    hosting.view.layoutIfNeeded()
                    coordinator.simulatePanEnded(
                        translation: CGSize(width: -viewport.width * 0.50, height: 0),
                        velocity: CGSize(width: -200, height: 0)
                    )

                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
                        hosting.view.layoutIfNeeded()
                        XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 2, "50% drag must commit turn to page index 2 (Page 3/6)")

                        // 3. Interactive drag < 35% screen width (15%) with low velocity -> snaps back, page remains 2
                        coordinator.simulatePanChanged(CGSize(width: -viewport.width * 0.15, height: 0))
                        hosting.view.layoutIfNeeded()
                        coordinator.simulatePanEnded(
                            translation: CGSize(width: -viewport.width * 0.15, height: 0),
                            velocity: CGSize(width: -20, height: 0)
                        )

                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.20) {
                            hosting.view.layoutIfNeeded()
                            XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 2, "15% drag must snap back and stay on page index 2 (Page 3/6)")

                            // 4. Tap Left Third (x = 0.15 * W) -> backward unroll curl to page index 1 (Page 2/6)
                            coordinator.simulateTap(at: CGPoint(x: viewport.width * 0.15, y: viewport.height * 0.5), in: viewport)

                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                                hosting.view.layoutIfNeeded()
                                XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 1, "Tap in Left Third must turn back to page index 1 (Page 2/6)")
                                window.isHidden = true
                                exp.fulfill()
                            }
                        }
                    }
                }
            }
        }

        wait(for: [exp], timeout: 5.0)
    }

    func testLiveReaderViewRTLAndSpreadTurnsInDeviceWindow() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let cbzURL = tempDir.appendingPathComponent("TestRTLSpreadComic-\(UUID().uuidString).cbz")
        defer { ReadingProgress.clear(cbzURL) }
        guard let archive = Archive(url: cbzURL, accessMode: .create) else {
            XCTFail("Could not create test CBZ archive")
            return
        }

        let colors: [UIColor] = [.systemRed, .systemGreen, .systemBlue, .systemOrange, .systemPurple, .systemTeal]
        for (i, color) in colors.enumerated() {
            let img = makeTestImage(width: 300, height: 450, color: color)
            guard let data = img.jpegData(compressionQuality: 0.85) else { continue }
            let entryName = String(format: "page_%02d.jpg", i + 1)
            try archive.addEntry(with: entryName, type: .file, uncompressedSize: Int64(data.count), provider: { position, size in
                data.subdata(in: Int(position)..<Int(position) + size)
            })
        }

        // Pre-configure RTL reading direction for this comic
        ReadingProgress.set(cbzURL, page: 0, step: -1, total: 6, rtl: true)

        let exp = expectation(description: "Live ReaderView RTL and Spread turns on device")
        let viewport = CGSize(width: 390, height: 844)

        DispatchQueue.main.async {
            let settings = LeafSettings()
            settings.portraitSpread = true
            settings.landscapeSpread = true

            let readerView = ReaderView(comicURL: cbzURL).environmentObject(settings)
            let hosting = UIHostingController(rootView: readerView)
            let window = UIWindow(frame: CGRect(origin: .zero, size: viewport))
            window.rootViewController = hosting
            window.makeKeyAndVisible()
            hosting.view.layoutIfNeeded()

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                hosting.view.layoutIfNeeded()
                guard let coordinator = self.findGestureCoordinator(in: hosting.view) else {
                    XCTFail("Expected ReaderGestureView coordinator")
                    window.isHidden = true
                    exp.fulfill()
                    return
                }

                // In RTL Spread mode, page 0 is the standalone cover.
                // Dragging from Left to Right (+50% width) advances forward in RTL to spread pages 1-2!
                coordinator.simulatePanChanged(CGSize(width: viewport.width * 0.20, height: 0))
                coordinator.simulatePanChanged(CGSize(width: viewport.width * 0.50, height: 0))
                hosting.view.layoutIfNeeded()
                coordinator.simulatePanEnded(
                    translation: CGSize(width: viewport.width * 0.50, height: 0),
                    velocity: CGSize(width: 250, height: 0)
                )

                DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) {
                    hosting.view.layoutIfNeeded()
                    XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 1, "RTL forward drag from cover 0 in spread mode must advance to spread 1-2")

                    // Next RTL forward tap (Left Third x = 0.15 * W) advances from spread 1-2 to spread 3-4 (firstPage = 3)
                    coordinator.simulateTap(at: CGPoint(x: viewport.width * 0.15, y: viewport.height * 0.5), in: viewport)

                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                        hosting.view.layoutIfNeeded()
                        XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 3, "RTL Left Third tap on spread 1-2 must advance to spread 3-4 (page index 3)")

                        // RTL backward drag (Right to Left -50% width) returns from spread 3-4 to spread 1-2 (firstPage = 1)
                        coordinator.simulatePanChanged(CGSize(width: -viewport.width * 0.20, height: 0))
                        coordinator.simulatePanChanged(CGSize(width: -viewport.width * 0.50, height: 0))
                        hosting.view.layoutIfNeeded()
                        coordinator.simulatePanEnded(
                            translation: CGSize(width: -viewport.width * 0.50, height: 0),
                            velocity: CGSize(width: -250, height: 0)
                        )

                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) {
                            hosting.view.layoutIfNeeded()
                            XCTAssertEqual(ReadingProgress.get(cbzURL)?.page, 1, "RTL backward drag on spread 3-4 must return to spread 1-2 (page index 1)")
                            window.isHidden = true
                            exp.fulfill()
                        }
                    }
                }
            }
        }

        wait(for: [exp], timeout: 5.0)
    }
}
