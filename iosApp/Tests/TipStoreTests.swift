import Foundation
import Testing
@testable import Leaf

@MainActor
private final class FakeTipClient: TipClient {
    var canMakePayments = true
    var catalog = TipCatalog.productIDs.map {
        TipProduct(id: $0, name: $0, price: "test price", isConsumable: true)
    }
    var result: TipPurchaseResult = .cancelled
    var failure = false
    var purchaseCalls = 0
    var observerStarts = 0
    var unfinishedCalls = 0
    var recovered: [TipVerification] = []
    var loadHook: (() async -> Void)?
    var purchaseHook: (() async -> Void)?
    var unfinishedHook: (() async -> Void)?
    var continuation: AsyncStream<TipVerification>.Continuation?

    func products() async throws -> [TipProduct] {
        await loadHook?()
        if failure { throw URLError(.notConnectedToInternet) }
        return catalog
    }
    func purchase(_ id: String) async throws -> TipPurchaseResult {
        purchaseCalls += 1
        await purchaseHook?()
        if failure { throw URLError(.notConnectedToInternet) }
        return result
    }
    func updates() -> AsyncStream<TipVerification> {
        observerStarts += 1
        return AsyncStream { continuation = $0 }
    }
    func unfinished() async -> [TipVerification] {
        unfinishedCalls += 1
        await unfinishedHook?()
        return recovered
    }
}

@Suite @MainActor
struct TipStoreTests {
    private func defaults() -> UserDefaults {
        guard let defaults = UserDefaults(suiteName: "LeafTipsTests." + UUID().uuidString) else {
            preconditionFailure("Could not create isolated test preferences")
        }
        return defaults
    }

    private func settle(_ condition: () -> Bool) async throws {
        for _ in 0..<100 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        #expect(condition())
    }


    @Test func purchaseWaitsForInitialRecoveryAndKeepsRapidTapsBusy() async throws {
        let client = FakeTipClient()
        var release: CheckedContinuation<Void, Never>?
        client.unfinishedHook = { await withCheckedContinuation { release = $0 } }
        let store = TipStore(client: client, defaults: defaults())
        store.start()
        try await settle { release != nil }
        await store.loadProducts()
        store.purchase(store.products[0])
        store.purchase(store.products[1])
        try await Task.sleep(nanoseconds: 20_000_000)
        #expect(client.purchaseCalls == 0)
        #expect(store.purchasingID == store.products[0].id)
        client.unfinishedHook = nil
        release?.resume(); release = nil
        try await settle { store.purchasingID == nil }
        #expect(client.purchaseCalls == 1)
        await store.stopForTesting()
    }

    @Test func foregroundRecoveryDoesNotOverlapAnActivePurchase() async throws {
        let client = FakeTipClient()
        var release: CheckedContinuation<Void, Never>?
        client.purchaseHook = { await withCheckedContinuation { release = $0 } }
        let store = TipStore(client: client, defaults: defaults())
        store.start()
        try await settle { client.unfinishedCalls == 1 }
        await store.loadProducts()
        store.purchase(store.products[0])
        try await settle { release != nil }
        store.recover()
        try await Task.sleep(nanoseconds: 20_000_000)
        #expect(client.unfinishedCalls == 1)
        client.purchaseHook = nil
        release?.resume(); release = nil
        try await settle { store.purchasingID == nil }
        store.recover()
        try await settle { client.unfinishedCalls >= 2 }
        #expect(client.unfinishedCalls == 2)
        await store.stopForTesting()
    }

    @Test func cancellationWhileWaitingForRecoveryNeverStartsACharge() async throws {
        let client = FakeTipClient()
        var release: CheckedContinuation<Void, Never>?
        client.unfinishedHook = { await withCheckedContinuation { release = $0 } }
        let store = TipStore(client: client, defaults: defaults())
        store.start()
        try await settle { release != nil }
        await store.loadProducts()
        store.purchase(store.products[0])
        try await Task.sleep(nanoseconds: 20_000_000)
        let teardown = Task { await store.stopForTesting() }
        try await Task.sleep(nanoseconds: 20_000_000)
        client.unfinishedHook = nil
        release?.resume(); release = nil
        await teardown.value
        #expect(client.purchaseCalls == 0)
        #expect(store.purchasingID == nil)
        #expect(store.pendingProductID == nil)
        #expect(store.notice == nil)
    }

    @Test func oldRecoveryCannotEraseANewPendingPurchaseForTheSameProduct() async throws {
        let client = FakeTipClient()
        var release: CheckedContinuation<Void, Never>?
        client.unfinishedHook = { await withCheckedContinuation { release = $0 } }
        client.recovered = [.verified(VerifiedTip(id: 901, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, finish: {}))]
        client.result = .pending
        let prefs = defaults()
        let store = TipStore(client: client, defaults: prefs)
        store.start()
        try await settle { release != nil }
        await store.loadProducts()
        store.purchase(store.products[0])
        try await Task.sleep(nanoseconds: 20_000_000)
        client.unfinishedHook = nil
        release?.resume(); release = nil
        try await settle { store.purchasingID == nil }
        try await Task.sleep(nanoseconds: 20_000_000)
        #expect(store.pendingProductID == client.catalog[0].id)
        #expect(store.notice == .pending)
        #expect(TipStore(client: client, defaults: prefs).pendingProductID == client.catalog[0].id)
        await store.stopForTesting()
    }

    @Test func restrictionChangedDuringRecoveryPreventsACharge() async throws {
        let client = FakeTipClient()
        var release: CheckedContinuation<Void, Never>?
        client.unfinishedHook = { await withCheckedContinuation { release = $0 } }
        let store = TipStore(client: client, defaults: defaults())
        store.start()
        try await settle { release != nil }
        await store.loadProducts()
        store.purchase(store.products[0])
        try await Task.sleep(nanoseconds: 20_000_000)
        client.canMakePayments = false
        client.unfinishedHook = nil
        release?.resume(); release = nil
        try await settle { store.purchasingID == nil }
        #expect(client.purchaseCalls == 0)
        #expect(store.notice == .restricted)
        await store.stopForTesting()
    }

    @Test func fixtureTeardownWaitsForInFlightTransactionHandler() async throws {
        let client = FakeTipClient()
        let store = TipStore(client: client, defaults: defaults())
        var release: CheckedContinuation<Void, Never>?
        var finished = false
        store.start()
        client.continuation?.yield(.verified(VerifiedTip(id: 900, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, finish: {
                await withCheckedContinuation { release = $0 }
                finished = true
            })))
        try await settle { release != nil }
        var drained = false
        let teardown = Task { await store.stopForTesting(); drained = true }
        await Task.yield()
        #expect(!drained)
        release?.resume()
        await teardown.value
        #expect(finished)
        #expect(drained)
        // The cancelled stream cannot consume a transaction from the next fixture.
        if let continuation = client.continuation {
            let result = continuation.yield(.unverified)
            if case .terminated = result {} else { Issue.record("Old observer still accepts fixture updates") }
        }
    }

    @Test func partialCatalogRejectsUnknownAndWrongTypesAndSorts() async {
        let client = FakeTipClient()
        let small = client.catalog[0]
        let large = client.catalog[2]
        client.catalog = [large, TipProduct(id: TipCatalog.productIDs[1], name: "wrong", price: "0", isConsumable: false),
                          TipProduct(id: "unknown", name: "unknown", price: "0", isConsumable: true), small]
        let store = TipStore(client: client, defaults: defaults())
        await store.loadProducts()
        #expect(store.products == [small, large])
        #expect(store.catalogState == .ready)
        client.catalog = []
        await store.loadProducts()
        #expect(store.catalogState == .unavailable)
        #expect(store.products.isEmpty)
    }

    @Test func closingAndReopeningDuringLoadDoesNotLeaveSpinner() async throws {
        let client = FakeTipClient()
        client.loadHook = { try? await Task.sleep(nanoseconds: 30_000_000) }
        let store = TipStore(client: client, defaults: defaults())
        let first = Task { await store.loadProducts() }
        await Task.yield()
        first.cancel()
        await store.loadProducts()
        await first.value
        #expect(store.catalogState == .ready)
        #expect(store.products.count == 3)
    }

    @Test func offlineRetryAndRestrictionsDoNotStartPurchase() async {
        let client = FakeTipClient()
        let store = TipStore(client: client, defaults: defaults())
        client.failure = true
        await store.loadProducts()
        #expect(store.catalogState == .unavailable)
        client.failure = false
        await store.loadProducts()
        client.canMakePayments = false
        store.purchase(store.products[0])
        #expect(client.purchaseCalls == 0)
        #expect(store.notice == .restricted)
        #expect(store.purchasingID == nil)
    }

    @Test func cancellationErrorsAndRapidTapsReleaseBusyState() async throws {
        let client = FakeTipClient()
        let store = TipStore(client: client, defaults: defaults())
        await store.loadProducts()
        client.purchaseHook = { try? await Task.sleep(nanoseconds: 30_000_000) }
        store.purchase(store.products[0])
        store.purchase(store.products[1])
        try await settle { store.purchasingID == nil }
        #expect(client.purchaseCalls == 1)
        #expect(store.notice == nil)
        client.failure = true
        store.purchase(store.products[0])
        try await settle { store.purchasingID == nil }
        #expect(store.notice == .unconfirmed)
    }

    @Test func pendingPersistsAndExplicitNewTipDoesNotCancelOldPayment() async throws {
        let client = FakeTipClient()
        let prefs = defaults()
        let store = TipStore(client: client, defaults: prefs)
        await store.loadProducts()
        client.result = .pending
        store.purchase(store.products[0])
        try await settle { store.purchasingID == nil }
        #expect(store.notice == .pending)
        #expect(TipStore(client: client, defaults: prefs).pendingProductID == TipCatalog.productIDs[0])
        store.purchase(store.products[1])
        #expect(client.purchaseCalls == 1)
        store.allowAnotherTip()
        #expect(store.pendingProductID == nil)
        #expect(TipStore(client: client, defaults: prefs).pendingProductID == nil)
    }

    @Test func duplicateAndConcurrentDeliveryAcknowledgesOnceAndFinishes() async throws {
        let store = TipStore(client: FakeTipClient(), defaults: defaults())
        var finishes = 0
        let transaction = VerifiedTip(id: 1, productID: TipCatalog.productIDs[0],
                                     isConsumable: true, isRevoked: false) {
            finishes += 1
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
        let first = Task { await store.handle(.verified(transaction), acknowledge: true) }
        await Task.yield()
        await store.handle(.verified(transaction), acknowledge: true)
        await first.value
        #expect(finishes == 1)
        #expect(store.notice == .thankYou)
        store.clearNotice()
        await store.handle(.verified(transaction), acknowledge: true)
        #expect(store.notice == nil)
        #expect(finishes == 2) // Re-delivery can be finished again, without acknowledgment.
    }

    @Test func directPurchaseAcknowledgesAfterQuietRecoveryOnlyOnce() async {
        let store = TipStore(client: FakeTipClient(), defaults: defaults())
        let result = TipVerification.verified(VerifiedTip(id: 11, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false,
            finish: { try? await Task.sleep(nanoseconds: 30_000_000) }))
        let recovery = Task { await store.handle(result, acknowledge: false) }
        await Task.yield()
        #expect(store.notice == nil)
        await store.handle(result, acknowledge: true, directPurchase: true)
        await recovery.value
        #expect(store.notice == .thankYou)
        store.clearNotice()
        await store.handle(result, acknowledge: true, directPurchase: true)
        #expect(store.notice == nil)
    }

    @Test func unverifiedUnknownAndWrongTypeNeverFinish() async {
        let store = TipStore(client: FakeTipClient(), defaults: defaults())
        await store.handle(.unverified, acknowledge: true)
        #expect(store.notice == .unconfirmed)
        for (id, consumable) in [("unrelated", true), (TipCatalog.productIDs[0], false)] {
            await store.handle(.verified(VerifiedTip(id: 5, productID: id,
                isConsumable: consumable, isRevoked: false, finish: { Issue.record("Unowned transaction finished") })),
                acknowledge: true)
        }
        #expect(store.notice != .thankYou)
    }

    @Test func revocationsAndRecoveryAreQuietWithoutLoadingCatalog() async throws {
        let client = FakeTipClient()
        var finishes = 0
        client.recovered = [.verified(VerifiedTip(id: 7, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, finish: { finishes += 1 }))]
        let store = TipStore(client: client, defaults: defaults())
        store.start()
        store.start()
        try await settle { finishes == 1 }
        #expect(client.observerStarts == 1)
        #expect(store.notice == nil)
        await store.handle(.verified(VerifiedTip(id: 8, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: true, finish: { finishes += 1 })), acknowledge: true)
        #expect(finishes == 2)
        #expect(store.notice == nil)
    }

    @Test func delayedApprovalClearsPendingAndObserverDoesNotRetainStore() async throws {
        let client = FakeTipClient()
        var store: TipStore? = TipStore(client: client, defaults: defaults())
        let storeWasReleased = { [weak store] in store == nil }
        await store?.loadProducts()
        store?.start()
        client.result = .pending
        store?.purchase(client.catalog[0])
        try await settle { store?.purchasingID == nil }
        var finished = false
        client.continuation?.yield(.verified(VerifiedTip(id: 9, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, finish: { finished = true })))
        try await settle { finished }
        #expect(store?.pendingProductID == nil)
        #expect(store?.notice == .thankYou)
        store = nil
        try await settle { storeWasReleased() }
    }

    @Test func approvalRacingPendingResultDoesNotLeaveStalePendingState() async throws {
        let client = FakeTipClient()
        let store = TipStore(client: client, defaults: defaults())
        await store.loadProducts()
        client.result = .pending
        client.purchaseHook = {
            await store.handle(.verified(VerifiedTip(id: 10, productID: TipCatalog.productIDs[0],
                isConsumable: true, isRevoked: false, finish: {})), acknowledge: true)
        }
        store.purchase(store.products[0])
        try await settle { store.purchasingID == nil }
        #expect(store.pendingProductID == nil)
        #expect(store.notice == .thankYou)
        client.purchaseHook = nil
    }

    @Test func verifiedOriginalPurchaseDateReachesPolicyAfterFinishingWithoutReplayExtension() async throws {
        let name = "LeafTipDateTests." + UUID().uuidString
        let prefs = try #require(UserDefaults(suiteName: name))
        defer { prefs.removePersistentDomain(forName: name) }
        let purchase = Date(timeIntervalSince1970: 1_700_000_000)
        var clock = purchase
        let policy = TipPromptPolicy(defaults: prefs, now: { clock })
        for id in ["one", "two", "three"] { policy.recordCompletion(itemID: id) }
        let store = TipStore(client: FakeTipClient(), defaults: prefs, promptPolicy: policy)
        var release: CheckedContinuation<Void, Never>?
        let handling = Task {
            await store.handle(.verified(VerifiedTip(id: 701, productID: TipCatalog.productIDs[0],
                isConsumable: true, isRevoked: false, purchaseDate: purchase,
                finish: { await withCheckedContinuation { release = $0 } })), acknowledge: false)
        }
        try await settle { release != nil }
        #expect(policy.isEligible, "No cooldown is published before transaction finishing completes")
        release?.resume(); await handling.value
        #expect(!policy.isEligible)
        clock = purchase.addingTimeInterval(400 * 24 * 60 * 60)
        await store.handle(.verified(VerifiedTip(id: 701, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, purchaseDate: purchase, finish: {})), acknowledge: false)
        #expect(policy.isEligible, "Old verified recovery must not use observation time as purchase time")
        await store.handle(.verified(VerifiedTip(id: 702, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, purchaseDate: clock, finish: {})), acknowledge: false)
        #expect(!policy.isEligible)
    }

    @Test(arguments: ["unsupported", "nonconsumable", "revoked", "missing-date"])
    func onlySupportedNonrevokedDatedTipsSuppressPrompts(_ outcome: String) async throws {
        let name = "LeafTipDateTests." + UUID().uuidString
        let prefs = try #require(UserDefaults(suiteName: name))
        defer { prefs.removePersistentDomain(forName: name) }
        let clock = Date(timeIntervalSince1970: 1_700_000_000)
        let policy = TipPromptPolicy(defaults: prefs, now: { clock })
        for id in ["one", "two", "three"] { policy.recordCompletion(itemID: id) }
        let store = TipStore(client: FakeTipClient(), defaults: prefs, promptPolicy: policy)
        var finished = 0
        await store.handle(.verified(VerifiedTip(id: 703,
            productID: outcome == "unsupported" ? "unknown.product" : TipCatalog.productIDs[0],
            isConsumable: outcome != "nonconsumable", isRevoked: outcome == "revoked",
            purchaseDate: outcome == "missing-date" ? nil : clock,
            finish: { finished += 1 })), acknowledge: false)
        #expect(policy.isEligible)
        #expect(finished == (["unsupported", "nonconsumable"].contains(outcome) ? 0 : 1))
    }

    @Test(arguments: ["pending", "cancelled", "unverified", "failure", "restricted"])
    func unsuccessfulPurchasesNeverBecomeVerifiedPromptSuppression(_ outcome: String) async throws {
        let name = "LeafTipDateTests." + UUID().uuidString
        let prefs = try #require(UserDefaults(suiteName: name))
        defer { prefs.removePersistentDomain(forName: name) }
        let policy = TipPromptPolicy(defaults: prefs)
        for id in ["one", "two", "three"] { policy.recordCompletion(itemID: id) }
        let client = FakeTipClient()
        let store = TipStore(client: client, defaults: prefs, promptPolicy: policy)
        await store.loadProducts()
        switch outcome {
        case "pending": client.result = .pending
        case "unverified": client.result = .success(.unverified)
        case "failure": client.failure = true
        case "restricted": client.canMakePayments = false
        default: client.result = .cancelled
        }
        store.purchase(try #require(store.products.first))
        try await settle { store.purchasingID == nil }
        #expect(client.purchaseCalls == (outcome == "restricted" ? 0 : 1))
        #expect(policy.isEligible)
        #expect(store.pendingProductID == (outcome == "pending" ? TipCatalog.productIDs[0] : nil))
    }

    @Test func permanentPromptOptOutKeepsVoluntaryPurchasesAvailable() async throws {
        let name = "LeafTipDateTests." + UUID().uuidString
        let prefs = try #require(UserDefaults(suiteName: name))
        defer { prefs.removePersistentDomain(forName: name) }
        let policy = TipPromptPolicy(defaults: prefs)
        for id in ["one", "two", "three"] { policy.recordCompletion(itemID: id) }
        #expect(policy.claimPresentation() != nil)
        policy.optOut()
        let client = FakeTipClient()
        client.result = .success(.verified(VerifiedTip(id: 704, productID: TipCatalog.productIDs[0],
            isConsumable: true, isRevoked: false, purchaseDate: Date(), finish: {})))
        let store = TipStore(client: client, defaults: prefs, promptPolicy: policy)
        await store.loadProducts()
        store.purchase(try #require(store.products.first))
        try await settle { store.purchasingID == nil }
        #expect(client.purchaseCalls == 1 && store.notice == .thankYou)
        #expect(!policy.isEligible)
    }

    @Test func eligibilityAndPromptPolicyNeverLoadTheCatalog() throws {
        let name = "LeafTipDateTests." + UUID().uuidString
        let prefs = try #require(UserDefaults(suiteName: name))
        defer { prefs.removePersistentDomain(forName: name) }
        let policy = TipPromptPolicy(defaults: prefs)
        let client = FakeTipClient()
        var requests = 0
        client.loadHook = { requests += 1 }
        let store = TipStore(client: client, defaults: prefs, promptPolicy: policy)
        for id in ["one", "two", "three"] { policy.recordCompletion(itemID: id) }
        #expect(policy.isEligible)
        policy.dismiss(try #require(policy.claimPresentation()))
        #expect(requests == 0 && store.catalogState == .idle)
        #expect(client.observerStarts == 0 && client.unfinishedCalls == 0)
    }
}
