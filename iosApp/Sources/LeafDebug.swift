import Foundation

/// Central gate for the LEAF_DEBUG_* QA hooks used by the simulator screenshot CI.
///
/// App Store installs can't receive environment variables, so the hooks were runtime no-ops in
/// production anyway — but the code paths shouldn't ship in a store binary at all. They compile
/// only in Debug builds, or in Release builds made with
/// `SWIFT_ACTIVE_COMPILATION_CONDITIONS="$(inherited) LEAF_SIM_CHECK CHIKA_SIM_CHECK"` (the sim-check workflow).
enum LeafDebug {
    static func env(_ key: String) -> String? {
        #if DEBUG || LEAF_SIM_CHECK || CHIKA_SIM_CHECK
        let env = ProcessInfo.processInfo.environment
        if let val = env[key] { return val }
        if key.hasPrefix("LEAF_") {
            let legacyKey = key.replacingOccurrences(of: "LEAF_", with: "CHIKA_")
            return env[legacyKey]
        }
        return nil
        #else
        return nil
        #endif
    }
}
