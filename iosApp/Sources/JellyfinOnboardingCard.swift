import SwiftUI

struct JellyfinOnboardingCard: View {
    let connect: () -> Void
    let skip: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your comics, your way").font(.headline)
            Text("Connect your Jellyfin library, or read files stored on this device. You can connect later from Add Comics.")
            Button("Connect Jellyfin", action: connect)
                .buttonStyle(.borderedProminent).frame(minHeight: 44)
            Button("Use without Jellyfin", action: skip).frame(minHeight: 44)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
