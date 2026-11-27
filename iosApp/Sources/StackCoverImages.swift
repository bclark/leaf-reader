import UIKit

enum StackCoverKey: Hashable {
    case local(CoverLoader.Revision)
    case linked(URL, UInt)
    case remote(profile: String, item: String, imageTag: String)
}

/// Artwork follows a comic when a loaded rear cover becomes the front cover.
@MainActor
struct StackCoverImages {
    private var active: Set<StackCoverKey> = []
    private var images: [StackCoverKey: UIImage] = [:]

    func image(for key: StackCoverKey?) -> UIImage? {
        key.flatMap { images[$0] }
    }

    mutating func retain(_ keys: [StackCoverKey]) {
        active = Set(keys)
        images = images.filter { active.contains($0.key) }
    }

    mutating func set(_ image: UIImage?, for key: StackCoverKey) {
        guard active.contains(key) else { return }
        images[key] = image
    }
}
