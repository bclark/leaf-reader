import XCTest
import UIKit
import ZIPFoundation
@testable import Leaf

@MainActor
final class CoverQualityTests: XCTestCase {
    func testRetinaShelfBudgetsCoverRenderedPixelsAndStayBounded() {
        for width: CGFloat in [92, 180, 240, 320, 500, 650] {
            for scale: CGFloat in [1, 2, 3] {
                let pixels = CoverResolution.pixels(width: width, scale: scale)
                XCTAssertGreaterThanOrEqual(CGFloat(pixels), min(width / 0.7 * scale, 2048))
                XCTAssertLessThanOrEqual(pixels, 2048)
                XCTAssertEqual(pixels % 256, 0)
            }
        }
        XCTAssertGreaterThan(CoverResolution.pixels(width: 500, scale: 2), CoverResolution.pixels(width: 240, scale: 2))
        XCTAssertEqual(CoverResolution.pixels(width: 0, scale: 2), 0)
        XCTAssertEqual(CoverResolution.pixels(width: .infinity, scale: 2), 0)
        XCTAssertEqual(CoverResolution.pixels(width: 200, scale: .nan), 0)
    }

    func testRemoteDecodeRetainsIPadRetinaDetailInsteadOf160PixelPreview() throws {
        let data = try imageData()
        let low = try XCTUnwrap(JellyfinBookRow.thumbnail(data))
        let budget = CoverResolution.pixels(width: 500, scale: 2)
        let high = try XCTUnwrap(JellyfinBookRow.thumbnail(data, maxPixel: budget))
        XCTAssertEqual(low.cgImage?.height, 160)
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(high.cgImage).width, 1000)
        XCTAssertEqual(high.cgImage?.height, budget)
        let capped = try XCTUnwrap(JellyfinBookRow.thumbnail(data, maxPixel: Int.max))
        XCTAssertLessThanOrEqual(try XCTUnwrap(capped.cgImage).height, 2048)
        for (name, image) in [("old-160px-cover", low), ("retina-cover", high)] {
            let attachment = XCTAttachment(image: image); attachment.name = name; attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    func testLocalCoverUpgradesCachedPreviewAndNeverDowngradesLargerArtwork() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("LeafCoverQuality-\(UUID())")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("synthetic.cbz")
        defer { CoverLoader.invalidate(url); try? FileManager.default.removeItem(at: directory) }
        let data = try imageData()
        do {
            let archive = try Archive(url: url, accessMode: .create)
            try archive.addEntry(with: "page.png", type: .file, uncompressedSize: Int64(data.count)) { position, size in
                data.subdata(in: Int(position)..<(Int(position) + size))
            }
        }
        let small = await CoverLoader.load(for: url, maxPixel: 512)
        XCTAssertEqual(small.cover?.cgImage?.height, 512)
        let big = await CoverLoader.load(for: url, maxPixel: 1792)
        XCTAssertEqual(big.cover?.cgImage?.height, 1792)
        XCTAssertEqual(big.pageCount, 1)
        let smallerAgain = await CoverLoader.load(for: url, maxPixel: 256)
        XCTAssertTrue(smallerAgain.cover === big.cover)
        CoverLoader.invalidate(url)
        let replacement = await CoverLoader.load(for: url, maxPixel: 768)
        XCTAssertEqual(replacement.cover?.cgImage?.height, 768)
        XCTAssertFalse(replacement.cover === big.cover)
        CoverLoader.shared.purge()
        XCTAssertNil(CoverLoader.cachedCover(for: url))
    }

    private func imageData() throws -> Data {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1680, height: 2400), format: format).image { context in
            UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 1680, height: 2400))
            UIColor.black.setFill()
            for x in stride(from: 0, to: 1680, by: 12) { context.fill(CGRect(x: x, y: 300, width: 3, height: 1800)) }
            ("LEAF\nRETINA COVER QA" as NSString).draw(at: CGPoint(x: 48, y: 48), withAttributes: [.font: UIFont.systemFont(ofSize: 72, weight: .bold), .foregroundColor: UIColor.black])
        }
        return try XCTUnwrap(image.pngData())
    }
}
