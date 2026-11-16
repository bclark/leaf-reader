import Foundation
import Combine
import CryptoKit

/// Local prompt state, separate from free reading and voluntary Settings support.
@MainActor
final class TipPromptPolicy: ObservableObject {
    static let key = "leaf.tipPrompt.v1"
    static let dismissalInterval: TimeInterval = 90 * 24 * 60 * 60

    private struct State: Codable {
        var version = 1
        var completed: [String] = []
        var optedOut = false
        var dismissedUntil: Date?
        var purchaseDate: Date?
        var purchaseUntil: Date?

        var isValid: Bool {
            if let purchaseDate {
                guard let purchaseUntil, purchaseUntil > purchaseDate else { return false }
            } else if purchaseUntil != nil { return false }
            return version == 1 && completed.count <= 3 && Set(completed).count == completed.count &&
            completed.allSatisfy { $0.count == 64 && $0.allSatisfy { "0123456789abcdef".contains($0) } } &&
            [dismissedUntil, purchaseDate, purchaseUntil].compactMap { $0 }.allSatisfy {
                $0.timeIntervalSinceReferenceDate.isFinite
            }
        }
    }

    @Published private var state: State?
    @Published private(set) var presentationID: UUID?
    private let defaults: UserDefaults
    private let now: () -> Date
    private let timeZone: () -> TimeZone

    init(defaults: UserDefaults = .standard, now: @escaping () -> Date = Date.init,
         timeZone: @escaping () -> TimeZone = { .current }) {
        self.defaults = defaults
        self.now = now
        self.timeZone = timeZone
        if defaults.object(forKey: Self.key) == nil {
            state = State()
        } else if let data = defaults.data(forKey: Self.key),
                  let saved = try? JSONDecoder().decode(State.self, from: data), saved.isValid {
            state = saved
        }
        // Unknown/corrupt data stays untouched and suppresses prompts.
    }

    var completedCount: Int { state?.completed.count ?? 0 }
    var isEligible: Bool { eligible(at: now()) && presentationID == nil }

    /// Uses the same persistent item identity as reading progress, without storing its filename.
    @discardableResult
    func recordCompletion(itemID: String) -> Bool {
        guard !itemID.isEmpty, var saved = state, !saved.optedOut, saved.completed.count < 3 else { return false }
        let identity = itemID.precomposedStringWithCanonicalMapping
        let opaqueID = SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
        guard !saved.completed.contains(opaqueID) else { return false }
        saved.completed.append(opaqueID)
        saved.completed.sort()
        return save(saved)
    }

    /// Call only after an appropriate foreground reader-dismissal event, never on launch/a timer.
    func claimPresentation() -> UUID? {
        let date = now()
        guard presentationID == nil, eligible(at: date), var saved = state else { return nil }
        // Persist before presentation so process death cannot immediately repeat the same offer.
        saved.dismissedUntil = date.addingTimeInterval(Self.dismissalInterval)
        guard save(saved) else { return nil }
        let id = UUID()
        presentationID = id
        return id
    }

    func dismiss(_ id: UUID) {
        guard presentationID == id, var saved = state else { return }
        presentationID = nil
        let date = now()
        if date.timeIntervalSinceReferenceDate.isFinite {
            saved.dismissedUntil = max(saved.dismissedUntil ?? .distantPast,
                date.addingTimeInterval(Self.dismissalInterval))
        }
        _ = save(saved)
    }

    /// Honor explicit opt-out even if a verified purchase has just dismissed the active offer.
    func optOut() {
        guard var saved = state else { return }
        saved.optedOut = true
        if save(saved) { presentationID = nil }
    }

    /// Receives only a recognized, verified, nonrevoked transaction's original purchase date.
    func recordVerifiedTip(purchaseDate: Date) {
        guard purchaseDate.timeIntervalSinceReferenceDate.isFinite, var saved = state, !saved.optedOut,
              saved.purchaseDate.map({ purchaseDate > $0 }) ?? true else { return }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone()
        guard let until = calendar.date(byAdding: .year, value: 1, to: purchaseDate),
              until > purchaseDate else { return }
        saved.purchaseDate = purchaseDate
        saved.purchaseUntil = until
        if save(saved), until > now() { presentationID = nil }
    }

    private func eligible(at date: Date) -> Bool {
        guard let saved = state, !saved.optedOut, saved.completed.count == 3,
              date.timeIntervalSinceReferenceDate.isFinite else { return false }
        return date >= max(saved.dismissedUntil ?? .distantPast, saved.purchaseUntil ?? .distantPast)
    }

    @discardableResult
    private func save(_ saved: State) -> Bool {
        guard saved.isValid, let data = try? JSONEncoder().encode(saved) else { return false }
        defaults.set(data, forKey: Self.key)
        state = saved
        return true
    }
}
