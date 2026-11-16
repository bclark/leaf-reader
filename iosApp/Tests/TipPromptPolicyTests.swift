import Foundation
import Testing
@testable import Leaf

@Suite @MainActor
struct TipPromptPolicyTests {
    private let utc = TimeZone(secondsFromGMT: 0)!

    private func date(_ value: String) throws -> Date {
        try #require(ISO8601DateFormatter().date(from: value))
    }

    private func withDefaults(_ body: (UserDefaults) throws -> Void) throws {
        let name = "LeafTipPolicyTests." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        try body(defaults)
    }

    private func completeThree(_ policy: TipPromptPolicy) {
        for id in ["Comic One.cbz", "Comic Two.pdf", "Comic Three.cbz"] {
            #expect(policy.recordCompletion(itemID: id))
        }
    }

    @Test func freshStateDoesNotOfferOrWriteAnythingAtLaunch() throws {
        try withDefaults { defaults in
            let policy = TipPromptPolicy(defaults: defaults)
            #expect(policy.completedCount == 0)
            #expect(!policy.isEligible && policy.presentationID == nil)
            #expect(policy.claimPresentation() == nil)
            #expect(defaults.object(forKey: TipPromptPolicy.key) == nil)
        }
    }

    @Test func onlyThreeDistinctItemsCountAndPersistWithoutFilenames() throws {
        try withDefaults { defaults in
            let policy = TipPromptPolicy(defaults: defaults)
            #expect(!policy.recordCompletion(itemID: ""))
            #expect(policy.recordCompletion(itemID: "Comic One.cbz"))
            #expect(!policy.recordCompletion(itemID: "Comic One.cbz"))
            #expect(policy.completedCount == 1 && !policy.isEligible)
            #expect(policy.recordCompletion(itemID: "Comic Two.pdf"))
            #expect(!policy.isEligible)
            #expect(policy.recordCompletion(itemID: "Comic Three.cbz"))
            #expect(policy.isEligible)
            #expect(!policy.recordCompletion(itemID: "Comic Four.cbz"))
            #expect(policy.completedCount == 3)
            let relaunched = TipPromptPolicy(defaults: defaults)
            #expect(relaunched.completedCount == 3 && relaunched.isEligible)
            let data = try #require(defaults.data(forKey: TipPromptPolicy.key))
            #expect(!String(decoding: data, as: UTF8.self).contains("Comic"))
        }
    }

    @Test func canonicallyEquivalentFilenameDoesNotCountTwice() throws {
        try withDefaults { defaults in
            let policy = TipPromptPolicy(defaults: defaults)
            #expect(policy.recordCompletion(itemID: "Café.cbz"))
            #expect(!policy.recordCompletion(itemID: "Cafe\u{301}.cbz"))
            #expect(policy.completedCount == 1)
        }
    }

    @Test func dismissalWaitsAtLeastNinetyDaysFromActualDismissal() throws {
        try withDefaults { defaults in
            var clock = try date("2026-11-14T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock })
            completeThree(policy)
            let token = try #require(policy.claimPresentation())
            #expect(!policy.isEligible && policy.claimPresentation() == nil)
            clock = clock.addingTimeInterval(60)
            policy.dismiss(token)
            let boundary = clock.addingTimeInterval(90 * 24 * 60 * 60)
            clock = boundary.addingTimeInterval(-1)
            #expect(!TipPromptPolicy(defaults: defaults, now: { clock }).isEligible)
            clock = boundary
            #expect(TipPromptPolicy(defaults: defaults, now: { clock }).isEligible)
        }
    }

    @Test func interruptedPresentationCannotRepeatAfterRelaunch() throws {
        try withDefaults { defaults in
            var clock = try date("2026-11-14T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock })
            completeThree(policy)
            #expect(policy.claimPresentation() != nil)
            let relaunched = TipPromptPolicy(defaults: defaults, now: { clock })
            #expect(relaunched.presentationID == nil && !relaunched.isEligible)
            #expect(relaunched.claimPresentation() == nil)
            clock = clock.addingTimeInterval(90 * 24 * 60 * 60)
            #expect(relaunched.isEligible)
            #expect(relaunched.presentationID == nil, "Expiry alone never presents a prompt")
        }
    }

    @Test func staleDismissalCannotEndANewerPresentation() throws {
        try withDefaults { defaults in
            var clock = try date("2026-11-14T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock })
            completeThree(policy)
            let first = try #require(policy.claimPresentation())
            policy.dismiss(first)
            clock = clock.addingTimeInterval(90 * 24 * 60 * 60)
            let second = try #require(policy.claimPresentation())
            policy.dismiss(first)
            #expect(policy.presentationID == second)
            policy.dismiss(second)
            clock = clock.addingTimeInterval(90 * 24 * 60 * 60)
            #expect(policy.isEligible)
        }
    }

    @Test func permanentOptOutWinsOverNewCompletionsAndVerifiedTips() throws {
        try withDefaults { defaults in
            var clock = try date("2026-11-14T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock })
            completeThree(policy)
            #expect(policy.claimPresentation() != nil)
            policy.optOut()
            policy.recordVerifiedTip(purchaseDate: clock)
            clock = try date("2030-11-14T12:00:00Z")
            #expect(!policy.recordCompletion(itemID: "Fourth.cbz"))
            #expect(!policy.isEligible)
            let relaunched = TipPromptPolicy(defaults: defaults, now: { clock })
            #expect(!relaunched.isEligible && relaunched.claimPresentation() == nil)
        }
    }

    @Test func optOutDoesNotDependOnAnActiveOfferOrKeepCollectingCompletions() throws {
        try withDefaults { defaults in
            let policy = TipPromptPolicy(defaults: defaults)
            policy.optOut()
            #expect(!policy.recordCompletion(itemID: "Another.cbz"))
            let saved = defaults.data(forKey: TipPromptPolicy.key)
            policy.recordVerifiedTip(purchaseDate: Date())
            #expect(defaults.data(forKey: TipPromptPolicy.key) == saved)
            #expect(policy.completedCount == 0 && !policy.isEligible)
        }
    }

    @Test func verifiedTipUsesOriginalDateAndCalendarLeapYearBoundary() throws {
        try withDefaults { defaults in
            let purchase = try date("2024-02-29T12:00:00Z")
            var clock = try date("2024-10-01T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock }, timeZone: { utc })
            completeThree(policy)
            policy.recordVerifiedTip(purchaseDate: purchase)
            clock = try date("2025-02-28T11:59:59Z"); #expect(!policy.isEligible)
            clock = try date("2025-02-28T12:00:00Z"); #expect(policy.isEligible)
            #expect(policy.presentationID == nil)
        }
    }

    @Test func replayAndOlderPurchaseDoNotRestartAYearButNewTipExtendsIt() throws {
        try withDefaults { defaults in
            let purchase = try date("2025-11-13T12:00:00Z")
            var clock = try date("2026-11-14T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock }, timeZone: { utc })
            completeThree(policy)
            policy.recordVerifiedTip(purchaseDate: purchase)
            #expect(policy.isEligible)
            let saved = defaults.data(forKey: TipPromptPolicy.key)
            policy.recordVerifiedTip(purchaseDate: purchase)
            policy.recordVerifiedTip(purchaseDate: purchase.addingTimeInterval(-100))
            #expect(defaults.data(forKey: TipPromptPolicy.key) == saved && policy.isEligible)
            policy.recordVerifiedTip(purchaseDate: clock)
            clock = try date("2027-11-14T11:59:59Z"); #expect(!policy.isEligible)
            clock = try date("2027-11-14T12:00:00Z"); #expect(policy.isEligible)
        }
    }

    @Test func savedCalendarDeadlineSurvivesTimezoneChangeAndRecovery() throws {
        try withDefaults { defaults in
            let purchase = try date("2024-03-09T17:00:00Z") // Noon in New York, before DST.
            var clock = purchase
            let zone = try #require(TimeZone(identifier: "America/New_York"))
            let policy = TipPromptPolicy(defaults: defaults, now: { clock }, timeZone: { zone })
            completeThree(policy); policy.recordVerifiedTip(purchaseDate: purchase)
            let saved = defaults.data(forKey: TipPromptPolicy.key)
            let newZone = try #require(TimeZone(identifier: "Pacific/Auckland"))
            let relaunched = TipPromptPolicy(defaults: defaults, now: { clock }, timeZone: { newZone })
            relaunched.recordVerifiedTip(purchaseDate: purchase)
            #expect(defaults.data(forKey: TipPromptPolicy.key) == saved)
            clock = try date("2025-03-09T15:59:59Z"); #expect(!relaunched.isEligible)
            clock = try date("2025-03-09T16:00:00Z"); #expect(relaunched.isEligible)
        }
    }

    @Test func shorterPurchaseCooldownDoesNotEraseDismissalAndNewTipClosesActiveOffer() throws {
        try withDefaults { defaults in
            var clock = try date("2026-11-14T12:00:00Z")
            let policy = TipPromptPolicy(defaults: defaults, now: { clock }, timeZone: { utc })
            completeThree(policy)
            policy.dismiss(try #require(policy.claimPresentation()))
            policy.recordVerifiedTip(purchaseDate: try date("2025-11-15T12:00:00Z"))
            clock = clock.addingTimeInterval(90 * 24 * 60 * 60 - 1); #expect(!policy.isEligible)
            clock = clock.addingTimeInterval(1); #expect(policy.isEligible)
            #expect(policy.claimPresentation() != nil)
            policy.recordVerifiedTip(purchaseDate: clock)
            #expect(policy.presentationID == nil && !policy.isEligible)
        }
    }

    @Test(arguments: ["not-json", "{\"version\":2,\"completed\":[],\"optedOut\":false}",
                      "{\"version\":1,\"completed\":[\"invalid\"],\"optedOut\":false}",
                      "{\"version\":1,\"completed\":[],\"optedOut\":false,\"purchaseDate\":0,\"purchaseUntil\":-1}"])
    func corruptOrFutureStateIsPreservedAndFailsClosed(_ raw: String) throws {
        try withDefaults { defaults in
            let bytes = Data(raw.utf8)
            defaults.set(bytes, forKey: TipPromptPolicy.key)
            let policy = TipPromptPolicy(defaults: defaults)
            #expect(!policy.recordCompletion(itemID: "Comic.cbz"))
            policy.recordVerifiedTip(purchaseDate: Date())
            #expect(!policy.isEligible && policy.claimPresentation() == nil)
            #expect(defaults.data(forKey: TipPromptPolicy.key) == bytes)
        }
    }

    @Test func wrongPreferenceTypeAndInvalidClockDoNotOverwriteOrOffer() throws {
        try withDefaults { defaults in
            defaults.set("future-state", forKey: TipPromptPolicy.key)
            let unknown = TipPromptPolicy(defaults: defaults)
            #expect(unknown.claimPresentation() == nil)
            #expect(defaults.string(forKey: TipPromptPolicy.key) == "future-state")
            defaults.removeObject(forKey: TipPromptPolicy.key)
            let policy = TipPromptPolicy(defaults: defaults, now: { Date(timeIntervalSinceReferenceDate: .nan) })
            completeThree(policy)
            let saved = defaults.data(forKey: TipPromptPolicy.key)
            policy.recordVerifiedTip(purchaseDate: Date(timeIntervalSinceReferenceDate: .nan))
            #expect(policy.claimPresentation() == nil && !policy.isEligible)
            #expect(defaults.data(forKey: TipPromptPolicy.key) == saved)
        }
    }
}
