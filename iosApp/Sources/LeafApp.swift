import SwiftUI

@main
struct LeafApp: App {
    @StateObject private var settings = LeafSettings()

    var body: some Scene {
        WindowGroup {
            LibraryView()
                .environmentObject(settings)
                .leafAppearance(settings.theme)
        }
    }
}
