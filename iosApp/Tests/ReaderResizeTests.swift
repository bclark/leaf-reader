import Testing
import SwiftUI
import UIKit
import ZIPFoundation
@testable import Leaf

@Suite(.serialized)
@MainActor
struct ReaderResizeTests {
    @Test(.timeLimit(.minutes(1))) func liveReaderRebuildsSpreadsForItsContainerWithoutChangingPage() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let url = root.appendingPathComponent("Resize.cbz")
        defer {
            ReadingProgress.clear(url)
            try? FileManager.default.removeItem(at: root)
        }
        let archive = try Archive(url: url, accessMode: .create)
        for (index, color) in [UIColor.gray, .red, .blue, .green].enumerated() {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let image = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 600), format: format).image { context in
                color.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 400, height: 600))
            }
            let data = try #require(image.jpegData(compressionQuality: 1))
            try archive.addEntry(with: "page\(index).jpg", type: .file, uncompressedSize: Int64(data.count)) { position, size in
                data.subdata(in: Int(position)..<Int(position) + size)
            }
        }
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = LeafSettings(defaults: defaults)
        settings.portraitSpread = false
        settings.landscapeSpread = true
        ReadingProgress.set(url, page: 1, step: -1, total: 4, rtl: false)
        let host = UIHostingController(rootView: ReaderView(comicURL: url).environmentObject(settings))
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKey = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        let container = UIViewController()
        window.rootViewController = container
        window.makeKeyAndVisible()
        container.addChild(host)
        container.view.addSubview(host.view)
        host.didMove(toParent: container)
        defer {
            host.willMove(toParent: nil)
            host.view.removeFromSuperview()
            host.removeFromParent()
            window.isHidden = true
            previousKey?.makeKey()
        }
        // The physical screen remains unchanged. Only this reader's container changes.
        for size in [CGSize(width: 390, height: 800), CGSize(width: 800, height: 390),
                     CGSize(width: 320, height: 700), CGSize(width: 700, height: 320)] {
            if let coordinator = gestureCoordinator(in: host.view) {
                // Resize with an interactive curl in flight; its old destination must not commit.
                coordinator.parent.onPanBegan(1)
                coordinator.simulatePanChanged(CGSize(width: -host.view.bounds.width * 0.4, height: 0))
            }
            host.view.frame = CGRect(origin: .zero, size: size)
            host.view.setNeedsLayout()
            host.view.layoutIfNeeded()
            let wantsSpread = size.width > size.height
            var matched = false
            var lastImage: UIImage?
            for _ in 0..<60 {
                try await Task.sleep(for: .milliseconds(50))
                host.view.layoutIfNeeded()
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1
                let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
                    host.view.layer.render(in: context.cgContext)
                }
                lastImage = image
                let left = try sample(image, x: 0.3, y: 0.5)
                let right = try sample(image, x: 0.7, y: 0.5)
                if left.red > 180 && left.blue < 70 &&
                    (wantsSpread ? (right.blue > 180 && right.red < 70) : (right.red > 180 && right.blue < 70)) {
                    matched = true
                    break
                }
            }
            if #available(iOS 26.0, *), let lastImage {
                Attachment.record(lastImage, named: "reader-\(Int(size.width))x\(Int(size.height)).png", as: .png)
            }
            #expect(matched, "Reader must use its own \(size) container to select the visible pages")
            #expect(ReadingProgress.get(url)?.page == 1)
        }
    }

    @Test func metalDrawableFollowsLocalBoundsAndDisplayTraits() async throws {
        let view = MetalCurlUIView(frame: CGRect(x: 0, y: 0, width: 320, height: 700))
        guard PageCurlRenderer.shared.isAvailable else { return }
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKey = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        let container = UIViewController()
        window.rootViewController = container
        window.makeKeyAndVisible()
        container.view.addSubview(view)
        defer {
            view.removeFromSuperview()
            window.isHidden = true
            previousKey?.makeKey()
        }
        for size in [CGSize(width: 320, height: 700), CGSize(width: 900, height: 600)] {
            view.frame.size = size
            view.setNeedsLayout()
            view.layoutIfNeeded()
            let scale = max(view.traitCollection.displayScale, 1)
            #expect(view.metalLayer.drawableSize == CGSize(width: size.width * scale, height: size.height * scale))
            #expect(view.metalLayer.contentsScale == scale)
        }
        if #available(iOS 17.0, *) {
            view.traitOverrides.displayScale = 2
            try await Task.sleep(for: .milliseconds(50))
            #expect(view.traitCollection.displayScale == 2)
            view.layoutIfNeeded()
            #expect(view.metalLayer.contentsScale == 2)
            #expect(view.metalLayer.drawableSize == CGSize(width: 1800, height: 1200))
        }
    }

    @Test func resettingCurlDuringResizeNeverCommitsItsOldDestination() async throws {
        let animator = CurlAnimator()
        var committed = false
        animator.start(from: 0.5, to: 1, duration: 0.05, direction: .forward) { committed = $0 }
        animator.reset()
        try await Task.sleep(for: .milliseconds(100))
        #expect(!committed)
        #expect(animator.direction == nil && !animator.isAnimating && animator.progress == 0)
    }

    private func gestureCoordinator(in view: UIView) -> ReaderGestures.Coordinator? {
        if view.accessibilityIdentifier == "ReaderGestureView" {
            for recognizer in view.gestureRecognizers ?? [] {
                if let coordinator = recognizer.delegate as? ReaderGestures.Coordinator { return coordinator }
            }
        }
        for child in view.subviews {
            if let coordinator = gestureCoordinator(in: child) { return coordinator }
        }
        return nil
    }

    private func sample(_ image: UIImage, x: CGFloat, y: CGFloat) throws -> (red: UInt8, blue: UInt8) {
        let source = try #require(image.cgImage)
        let pixel = try #require(source.cropping(to: CGRect(x: CGFloat(source.width) * x,
            y: CGFloat(source.height) * y, width: 1, height: 1)))
        var bytes = [UInt8](repeating: 0, count: 4)
        let context = try #require(CGContext(data: &bytes, width: 1, height: 1, bitsPerComponent: 8,
            bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.draw(pixel, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        return (bytes[0], bytes[2])
    }
}
