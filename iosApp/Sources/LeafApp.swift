import SwiftUI

@main
struct LeafApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var tips = TipStore()
    @StateObject private var settings = LeafSettings()

    private var observesPurchases: Bool {
        let environment = ProcessInfo.processInfo.environment
        return environment["XCODE_RUNNING_FOR_PREVIEWS"] != "1"
            && environment["XCTestConfigurationFilePath"] == nil
            && environment["XCTestBundlePath"] == nil
            && NSClassFromString("XCTestCase") == nil
            && NSClassFromString("XCTest.XCTestCase") == nil
    }

    var body: some Scene {
        WindowGroup {
            LibraryView()
                .environmentObject(settings)
                .environmentObject(tips)
                .task {
                    // Tests own isolated clients; never let the host finish their transactions.
                    if observesPurchases { tips.start() }
                }
                .onChange(of: scenePhase) { phase in
                    if phase == .active, observesPurchases { tips.recover() }
                }
                .leafAppearance(settings.theme)
        }
    }
}
