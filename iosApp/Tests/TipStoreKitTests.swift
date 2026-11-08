import Foundation
import StoreKit
import StoreKitTest
import Testing
@testable import Leaf

private final class TipTestBundle: NSObject {}

@Suite(.serialized) @MainActor
struct TipStoreKitTests {
    @Test func localCatalogRepeatedTipsAndDelayedApproval() async throws {
        let url = try #require(Bundle(for: TipTestBundle.self).url(forResource: "LeafTips", withExtension: "storekit"))
        let session = try SKTestSession(contentsOf: url)
        session.resetToDefaultState()
        session.clearTransactions()
        session.disableDialogs = true
        defer {
            session.clearTransactions()
            session.resetToDefaultState()
        }
        let suiteName = "LeafStoreKitTests." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = TipStore(defaults: defaults)
        store.start()
        await store.loadProducts()
        #expect(store.products.map(\.id) == TipCatalog.productIDs)
        #expect(store.products.allSatisfy { !$0.price.isEmpty && $0.isConsumable })

        // Exercise the same direct purchase path as the picker. An externally injected
        // transaction can race quiet startup recovery and has no direct result to await.
        for id in TipCatalog.productIDs + [TipCatalog.productIDs[0]] {
            print("Local tip test: purchasing tier")
            store.purchase(try #require(store.products.first { $0.id == id }))
            try await wait { store.purchasingID == nil && store.notice == .thankYou }
            try await waitForFinished()
            #expect(await unfinishedTipCount() == 0)
            store.clearNotice()
        }
        #expect(session.allTransactions().count == 4)
        #expect(await unfinishedTipCount() == 0)

        print("Local tip test: starting approval scenario")
        session.askToBuyEnabled = true
        store.purchase(try #require(store.products.first))
        try await wait { store.purchasingID == nil }
        #expect(store.notice == .pending)
        let pending = try #require(session.allTransactions().first { $0.pendingAskToBuyConfirmation })
        #expect(store.notice != .thankYou)
        print("Local tip test: approving pending transaction")
        try session.approveAskToBuyTransaction(identifier: pending.identifier)
        try await wait { store.notice == .thankYou }
        try await waitForFinished()
        #expect(await unfinishedTipCount() == 0)

        store.clearNotice()
        print("Local tip test: refunding approved purchase")
        let purchased = try #require(session.allTransactions().filter { $0.state == .purchased }
            .max { $0.purchaseDate < $1.purchaseDate })
        try session.refundTransaction(identifier: purchased.identifier)
        try await Task.sleep(nanoseconds: 300_000_000)
        #expect(store.notice != .thankYou)

        // Local fixture prices are test values, not Apple's live Mexico price mapping.
        session.storefront = "MEX"
        session.locale = Locale(identifier: "es_MX")
        await store.loadProducts()
        #expect(store.products.count == 3)
        #expect(store.products.allSatisfy { !$0.price.isEmpty })
        #expect(store.products.map(\.name) == ["Propina pequeña", "Propina mediana", "Propina grande"])
    }

    @Test func unfinishedTransactionRecoversWithoutCatalogOrThankYou() async throws {
        let url = try #require(Bundle(for: TipTestBundle.self).url(forResource: "LeafTips", withExtension: "storekit"))
        let session = try SKTestSession(contentsOf: url)
        session.resetToDefaultState()
        session.clearTransactions()
        session.disableDialogs = true
        defer {
            session.clearTransactions()
            session.resetToDefaultState()
        }
        try session.buyProduct(productIdentifier: TipCatalog.productIDs[1])
        for _ in 0..<100 {
            if await unfinishedTipCount() == 1 { break }
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        #expect(await unfinishedTipCount() == 1)
        let suiteName = "LeafStoreKitTests." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = TipStore(defaults: defaults)
        store.recover()
        try await waitForFinished()
        #expect(await unfinishedTipCount() == 0)
        #expect(store.products.isEmpty)
        #expect(store.notice == nil)
    }

    private func wait(_ condition: () -> Bool) async throws {
        for _ in 0..<1200 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        try #require(condition(), "Timed out waiting for StoreKit acknowledgment")
    }

    private func unfinishedTipCount() async -> Int {
        var count = 0
        for await result in StoreKit.Transaction.unfinished {
            if case .verified(let transaction) = result, TipCatalog.supportedIDs.contains(transaction.productID) {
                count += 1
            }
        }
        return count
    }

    private func waitForFinished() async throws {
        for _ in 0..<100 {
            if await unfinishedTipCount() == 0 { return }
            try await Task.sleep(nanoseconds: 50_000_000)
        }
    }
}
