import UIKit
import XCTest
@testable import Leaf

@MainActor
final class StackCoverImagesTests: XCTestCase {
    func testLoadedArtworkFollowsIdentityAcrossPromotionsAndRejectsLateLoads() {
        let keys = (0..<4).map { StackCoverKey.remote(profile: "fixture", item: "\($0)", imageTag: "v1") }
        let images = (0..<4).map { _ in UIImage() }
        var stack = StackCoverImages()
        stack.retain(Array(keys.prefix(3)))
        for i in 0..<3 { stack.set(images[i], for: keys[i]) }
        for rotation in 0..<4 {
            let order = (0..<3).map { keys[($0 + rotation) % 3] }
            stack.retain(order)
            for i in 0..<3 {
                XCTAssertTrue(stack.image(for: order[i]) === images[(i + rotation) % 3])
            }
        }
        stack.retain(Array(keys[1...3]))
        stack.set(images[0], for: keys[0]) // A cancelled, evicted request finishes late.
        XCTAssertNil(stack.image(for: keys[0]))
        XCTAssertNil(stack.image(for: keys[3])) // Never show the preceding role's artwork.
        XCTAssertTrue(stack.image(for: keys[1]) === images[1])
        stack.set(images[3], for: keys[3])
        XCTAssertTrue(stack.image(for: keys[3]) === images[3])

        let otherProfile = StackCoverKey.remote(profile: "other", item: "1", imageTag: "v1")
        let replacement = StackCoverKey.remote(profile: "fixture", item: "1", imageTag: "v2")
        stack.retain([otherProfile, replacement])
        XCTAssertNil(stack.image(for: otherProfile))
        XCTAssertNil(stack.image(for: replacement))
        XCTAssertNil(stack.image(for: keys[1]))

        let url = URL(fileURLWithPath: "/fixture/comic.cbz")
        let old = StackCoverKey.local(CoverLoader.Revision(url: url, generation: 0))
        let new = StackCoverKey.local(CoverLoader.Revision(url: url, generation: 1))
        stack.retain([old]); stack.set(images[0], for: old)
        stack.retain([new]); stack.set(images[0], for: old)
        XCTAssertNil(stack.image(for: new))
        XCTAssertNil(stack.image(for: old))
    }
}
