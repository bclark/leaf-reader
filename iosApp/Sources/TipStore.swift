import Foundation
import SwiftUI
import StoreKit

enum TipCatalog {
    // Proposed production IDs: confirm existing App Store Connect records before release.
    static let productIDs = [
        "net.digitalbc.leafreader.tip.small",
        "net.digitalbc.leafreader.tip.medium",
        "net.digitalbc.leafreader.tip.large"
    ]
    static let supportedIDs = Set(productIDs)
}

struct TipProduct: Identifiable, Equatable {
    let id: String
    let name: String
    let price: String
    let isConsumable: Bool
}

/// Only verified StoreKit transactions may be represented by this value.
struct VerifiedTip {
    let id: UInt64
    let productID: String
    let isConsumable: Bool
    let isRevoked: Bool
    let finish: () async -> Void
}

enum TipVerification {
    case verified(VerifiedTip)
    case unverified
}

enum TipPurchaseResult {
    case success(TipVerification)
    case pending
    case cancelled
}

@MainActor
protocol TipClient {
    var canMakePayments: Bool { get }
    func products() async throws -> [TipProduct]
    func purchase(_ id: String) async throws -> TipPurchaseResult
    func updates() -> AsyncStream<TipVerification>
    func unfinished() async -> [TipVerification]
}

@MainActor
final class StoreKitTipClient: TipClient {
    private var available: [String: Product] = [:]
    var canMakePayments: Bool { AppStore.canMakePayments }

    func products() async throws -> [TipProduct] {
        let products = try await Product.products(for: TipCatalog.productIDs)
        available = Dictionary(uniqueKeysWithValues: products.map { ($0.id, $0) })
        return products.map {
            TipProduct(id: $0.id, name: $0.displayName, price: $0.displayPrice,
                       isConsumable: $0.type == .consumable)
        }
    }

    func purchase(_ id: String) async throws -> TipPurchaseResult {
        guard let product = available[id], product.type == .consumable else {
            throw StoreKitError.notAvailableInStorefront
        }
        switch try await product.purchase() {
        case .success(let result): return .success(Self.verify(result))
        case .pending: return .pending
        case .userCancelled: return .cancelled
        @unknown default: throw StoreKitError.unknown
        }
    }

    func updates() -> AsyncStream<TipVerification> {
        AsyncStream { continuation in
            let task = Task {
                for await result in StoreKit.Transaction.updates {
                    guard !Task.isCancelled else { break }
                    continuation.yield(Self.verify(result))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func unfinished() async -> [TipVerification] {
        var results: [TipVerification] = []
        for await result in StoreKit.Transaction.unfinished {
            guard !Task.isCancelled else { break }
            results.append(Self.verify(result))
        }
        return results
    }

    private static func verify(_ result: VerificationResult<StoreKit.Transaction>) -> TipVerification {
        guard case .verified(let transaction) = result else { return .unverified }
        return .verified(VerifiedTip(
            id: transaction.id, productID: transaction.productID,
            isConsumable: transaction.productType == .consumable,
            isRevoked: transaction.revocationDate != nil,
            finish: { await transaction.finish() }
        ))
    }
}

@MainActor
final class TipStore: ObservableObject {
    enum CatalogState { case idle, loading, ready, unavailable }
    enum Notice: Equatable { case thankYou, pending, unconfirmed, restricted }

    @Published private(set) var products: [TipProduct] = []
    @Published private(set) var catalogState: CatalogState = .idle
    @Published private(set) var purchasingID: String?
    @Published private(set) var notice: Notice?
    @Published private(set) var pendingProductID: String?

    private let client: any TipClient
    private let defaults: UserDefaults
    private static let pendingKey = "leaf.pendingTipProduct"
    private var listener: Task<Void, Never>?
    private var recovery: Task<Void, Never>?
    private var purchaseTask: Task<Void, Never>?
    private var handlingIDs: Set<UInt64> = []
    private var completedActivePurchase = false
    private var handledIDs: Set<UInt64> = []
    private var acknowledgedIDs: Set<UInt64> = []

    init(client: (any TipClient)? = nil, defaults: UserDefaults = .standard) {
        self.client = client ?? StoreKitTipClient()
        self.defaults = defaults
        if let id = defaults.string(forKey: Self.pendingKey), TipCatalog.supportedIDs.contains(id) {
            pendingProductID = id
            notice = .pending
        }
    }

    deinit {
        listener?.cancel()
        recovery?.cancel()
        purchaseTask?.cancel()
    }

    func start() {
        guard listener == nil else { return }
        let stream = client.updates()
        listener = Task { [weak self] in
            for await result in stream {
                guard !Task.isCancelled else { break }
                await self?.handle(result, acknowledge: true)
            }
        }
        recover()
    }

    func recover() {
        guard recovery == nil else { return }
        let client = client
        recovery = Task { [weak self] in
            let results = await client.unfinished()
            for result in results {
                guard !Task.isCancelled else { break }
                await self?.handle(result, acknowledge: false)
            }
            self?.recovery = nil
        }
    }

    func loadProducts() async {
        guard catalogState != .loading, purchasingID == nil else { return }
        catalogState = .loading
        do {
            let returned = try await client.products()
            products = TipCatalog.productIDs.compactMap { id in
                returned.first { $0.id == id && $0.isConsumable }
            }
            catalogState = products.isEmpty ? .unavailable : .ready
        } catch {
            products = []
            catalogState = .unavailable
        }
    }

    func purchase(_ product: TipProduct) {
        guard purchasingID == nil, pendingProductID == nil,
              catalogState == .ready, products.contains(product) else { return }
        guard client.canMakePayments else { notice = .restricted; return }
        purchasingID = product.id
        completedActivePurchase = false
        notice = nil
        // Owned by the store, not the sheet: dismissal cannot cancel a charge or its handling.
        purchaseTask = Task {
            defer {
                purchasingID = nil
                purchaseTask = nil
            }
            do {
                switch try await client.purchase(product.id) {
                case .success(let result):
                    await handle(result, acknowledge: true, directPurchase: true)
                case .pending:
                    guard !completedActivePurchase else { break }
                    pendingProductID = product.id
                    defaults.set(product.id, forKey: Self.pendingKey)
                    notice = .pending
                case .cancelled:
                    break
                }
            } catch is CancellationError {
                // Interruption is not evidence that a payment failed.
            } catch {
                notice = .unconfirmed
            }
        }
    }

    /// Explicitly acknowledged by the user; this does not cancel Apple's pending purchase.
    func allowAnotherTip() {
        clearPending()
        notice = nil
    }

    func clearNotice() {
        notice = pendingProductID == nil ? nil : .pending
    }

    func handle(_ result: TipVerification, acknowledge: Bool, directPurchase: Bool = false) async {
        guard case .verified(let transaction) = result else {
            if acknowledge { notice = .unconfirmed }
            return
        }
        guard TipCatalog.supportedIDs.contains(transaction.productID), transaction.isConsumable else { return }
        if purchasingID == transaction.productID { completedActivePurchase = true }
        let isNew = handledIDs.insert(transaction.id).inserted
        if !transaction.isRevoked, pendingProductID == transaction.productID { clearPending() }
        // No entitlement, receipt storage, or durable side effect. Recovery stays quiet.
        if transaction.isRevoked {
            if notice == .thankYou { notice = nil }
        } else if acknowledge && (isNew || directPurchase),
                  acknowledgedIDs.insert(transaction.id).inserted {
            // Foreground recovery may finish a payment before purchase() returns.
            // Still acknowledge that direct user action once, without replaying recovery UI.
            notice = .thankYou
        }
        guard handlingIDs.insert(transaction.id).inserted else { return }
        defer { handlingIDs.remove(transaction.id) }
        await transaction.finish()
    }

    private func clearPending() {
        pendingProductID = nil
        defaults.removeObject(forKey: Self.pendingKey)
        if notice == .pending { notice = nil }
    }
}
