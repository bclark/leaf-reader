import Foundation
import Testing
@testable import Leaf

@Suite(.serialized) @MainActor
struct TipReaderCompletionTests {
    private func policy() throws -> (TipPromptPolicy, UserDefaults, String) {
        let name = "leaf.reader-tip." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: name))
        return (TipPromptPolicy(defaults: defaults), defaults, name)
    }
    private func url() -> URL { URL(fileURLWithPath: "/Comics/" + UUID().uuidString + ".cbz") }
    private func complete(_ url: URL) -> OpenComic {
        ReadingProgress.set(url, page: 0, step: -1, total: 3, rtl: false, completed: false)
        let comic = OpenComic(url: url)
        ReadingProgress.set(url, page: 2, step: -1, total: 3, rtl: false)
        return comic
    }

    @Test func openingSnapshotDoesNotChangeWithProgressAndIncompleteDismissalDoesNotCount() throws {
        let (policy, defaults, name) = try policy()
        defer { defaults.removePersistentDomain(forName: name) }
        let url = url()
        defer { ReadingProgress.clear(url) }
        ReadingProgress.set(url, page: 0, step: -1, total: 3, rtl: false)
        let opened = OpenComic(url: url)
        #expect(opened.opensAtCover && !opened.wasCompletedAtOpen)
        #expect(policy.readerDidClose(opened, canPresent: true) == nil)
        #expect(policy.completedCount == 0)
        ReadingProgress.set(url, page: 2, step: -1, total: 3, rtl: false)
        #expect(!opened.wasCompletedAtOpen && opened.opensAtCover)
        #expect(policy.readerDidClose(opened, canPresent: true) == nil)
        #expect(policy.completedCount == 1)
    }

    @Test func completedAtOpenRereadAndRemovedComicCannotCountAsNewCompletion() throws {
        let (policy, defaults, name) = try policy()
        defer { defaults.removePersistentDomain(forName: name) }
        let url = url()
        defer { ReadingProgress.clear(url) }
        _ = complete(url)
        let reread = OpenComic(url: url)
        #expect(reread.wasCompletedAtOpen)
        #expect(policy.readerDidClose(reread, canPresent: true) == nil)
        #expect(policy.completedCount == 0)
        let newlyOpened = complete(url)
        ReadingProgress.clear(url)
        #expect(policy.readerDidClose(newlyOpened, canPresent: true) == nil)
        #expect(policy.completedCount == 0)
    }

    @Test func thirdDistinctDismissalClaimsOnceAndBackgroundCannotOffer() throws {
        let (policy, defaults, name) = try policy()
        defer { defaults.removePersistentDomain(forName: name) }
        let urls = (0..<4).map { _ in url() }
        defer { urls.forEach { ReadingProgress.clear($0) } }
        for url in urls.prefix(3) {
            #expect(policy.readerDidClose(complete(url), canPresent: false) == nil)
        }
        #expect(policy.completedCount == 3)
        #expect(policy.isEligible && policy.presentationID == nil)
        // Becoming active or re-creating a view has no presentation side effect.
        #expect(TipPromptPolicy(defaults: defaults).presentationID == nil)
        // The counter stays bounded at3; the next real completion is still an opportunity.
        let final = complete(urls[3])
        let token = try #require(policy.readerDidClose(final, canPresent: true))
        #expect(policy.completedCount == 3 && policy.presentationID == token)
        #expect(policy.readerDidClose(final, canPresent: true) == nil)
        policy.dismiss(token)
        #expect(policy.readerDidClose(final, canPresent: true) == nil)
    }

    @Test func neverAskSurvivesSubsequentReaderCompletionsAndRecreation() throws {
        let (policy, defaults, name) = try policy()
        defer { defaults.removePersistentDomain(forName: name) }
        policy.optOut()
        let url = url()
        defer { ReadingProgress.clear(url) }
        #expect(policy.readerDidClose(complete(url), canPresent: true) == nil)
        #expect(policy.completedCount == 0)
        #expect(!TipPromptPolicy(defaults: defaults).isEligible)
    }
}
