import UIKit
import SwiftUI
import ImageIO
import os

/// One decoded thumbnail per file revision, shared by concurrent library/detail consumers.
@MainActor
final class CoverLoader: ObservableObject {
    static let shared = CoverLoader()
    @Published private var generations: [URL: Int] = [:]
    private struct Load {
        let id = UUID()
        let task: Task<(UIImage?, Int), Never>
    }
    private var pending: [Revision: Load] = [:]
    private let worker = CoverDecodeWorker()
    private var memoryObserver: NSObjectProtocol?

    private init() {
        memoryObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in self?.purge() }
        }
    }

    deinit {
        if let memoryObserver { NotificationCenter.default.removeObserver(memoryObserver) }
    }

    struct Revision: Hashable {
        let url: URL
        let generation: Int
    }

    func revisions(for urls: [URL]) -> [Revision] {
        urls.map { Revision(url: $0, generation: generations[$0, default: 0]) }
    }

    static func invalidate(_ url: URL) {
        let revision = Revision(url: url, generation: shared.generations[url, default: 0])
        shared.pending.removeValue(forKey: revision)?.task.cancel()
        shared.generations[url, default: 0] += 1
        cache.removeObject(forKey: url as NSURL)
        countCache.removeObject(forKey: url as NSURL)
    }

    private static let cache: NSCache<NSURL, UIImage> = {
        let cache = NSCache<NSURL, UIImage>()
        cache.totalCostLimit = 48 * 1024 * 1024
        cache.countLimit = 128
        return cache
    }()
    private static let countCache: NSCache<NSURL, NSNumber> = {
        let cache = NSCache<NSURL, NSNumber>()
        cache.countLimit = 2048
        return cache
    }()

    /// Visible views keep their images; only reloadable shared work/cache is discarded.
    func purge() {
        for load in pending.values { load.task.cancel() }
        pending.removeAll()
        Self.cache.removeAllObjects()
        Self.countCache.removeAllObjects()
    }

    static func cachedCover(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    static func load(for url: URL) async -> (cover: UIImage?, pageCount: Int) {
        guard !Task.isCancelled else { return (nil, 0) }
        if let image = cache.object(forKey: url as NSURL),
           let count = countCache.object(forKey: url as NSURL) {
            return (image, count.intValue)
        }
        let revision = Revision(url: url, generation: shared.generations[url, default: 0])
        let load: Load
        if let existing = shared.pending[revision] {
            load = existing
        } else {
            let worker = shared.worker
            load = Load(task: Task { await worker.load(url) })
            shared.pending[revision] = load
        }
        let result = await load.task.value
        // Cancellation by one view must not cancel another consumer's shared decode.
        guard revision.generation == shared.generations[url, default: 0] else {
            return Task.isCancelled ? (nil, 0) : await Self.load(for: url)
        }
        guard !load.task.isCancelled else { return (nil, 0) }
        // Another waiter may already have published, or pressure may have purged this flight.
        guard shared.pending[revision]?.id == load.id else {
            return Task.isCancelled ? (nil, 0) : result
        }
        shared.pending.removeValue(forKey: revision)
        if let image = result.0 {
            let cost = image.cgImage.map { $0.bytesPerRow * $0.height }
                ?? Int(image.size.width * image.size.height * image.scale * image.scale * 4)
            cache.setObject(image, forKey: url as NSURL, cost: cost)
        }
        countCache.setObject(NSNumber(value: result.1), forKey: url as NSURL)
        return Task.isCancelled ? (nil, 0) : result
    }
}

/// Serial admission bounds simultaneous archive/decode allocations during fast grid scrolling.
private actor CoverDecodeWorker {
    func load(_ url: URL) -> (UIImage?, Int) {
        guard !Task.isCancelled else { return (nil, 0) }
        let interval = LeafPerformance.signposter.beginInterval("Cover decode", id: LeafPerformance.signposter.makeSignpostID())
        defer { LeafPerformance.signposter.endInterval("Cover decode", interval) }
        return autoreleasepool {
            guard let archive = try? CbzArchive(url: url) else { return (nil, 0) }
            let count = archive.pageCount
            guard count > 0, !Task.isCancelled, let data = try? archive.readPage(0) else { return (nil, count) }
            // Preserve the format fallback; common formats take the bounded ImageIO path.
            return (thumbnail(from: data, maxPixel: 600) ?? UIImage(data: data), count)
        }
    }

    private func thumbnail(from data: Data, maxPixel: Int) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData,
            [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }
}
