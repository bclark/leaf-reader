import LeafShared
import XCTest

// Mirrors the synthetic vectors in docs/features/GUIDED_VIEW_SYNTHETIC_BASELINE.md.
final class GuidedPanelOrderingParityTests: XCTestCase {
    func testGridReadingOrderThroughKotlinBridge() {
        let a = Panel(left: 0.05, top: 0.05, right: 0.45, bottom: 0.40)
        let b = Panel(left: 0.55, top: 0.05, right: 0.95, bottom: 0.40)
        let c = Panel(left: 0.05, top: 0.60, right: 0.45, bottom: 0.95)
        let d = Panel(left: 0.55, top: 0.60, right: 0.95, bottom: 0.95)
        let input = [d, b, c, a]
        assertOrder(input, rightToLeft: false, equals: [a, b, c, d])
        assertOrder(input, rightToLeft: true, equals: [b, a, d, c])
    }

    func testInsetReadingOrderThroughKotlinBridge() {
        let g = Panel(left: 0.05, top: 0.05, right: 0.90, bottom: 0.65)
        let i = Panel(left: 0.60, top: 0.26, right: 0.79, bottom: 0.40)
        let f = Panel(left: 0.08, top: 0.75, right: 0.91, bottom: 0.96)
        assertOrder([f, i, g], rightToLeft: false, equals: [g, i, f])
    }

    private func assertOrder(
        _ input: [Panel], rightToLeft: Bool, equals expected: [Panel],
        file: StaticString = #filePath, line: UInt = #line
    ) {
        let actual = PanelOrdering.shared.order(panels: input, rightToLeft: rightToLeft)
        XCTAssertEqual(actual.map(edges), expected.map(edges), file: file, line: line)
    }

    private func edges(_ panel: Panel) -> [Float] {
        [panel.left, panel.top, panel.right, panel.bottom]
    }
}
