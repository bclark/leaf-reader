# Android 0.3.9 RC1 handoff for iOS

This records Android behavior for cross-platform coordination. It does not assign iOS implementation or change its release scope.

## Implemented on Android

- Installation-local first-launch states are Welcome, Optional Jellyfin and Complete. Complete is terminal. Existing-install evidence skips onboarding; failed persistence retains Library access and reports the failure.
- Welcome offers Choose a file, I use Jellyfin and Maybe later. Optional Jellyfin offers Back, Connect Jellyfin and Use without Jellyfin. Actual import/setup outcomes and recovery receipts determine completion; entering a picker alone does not prove success.
- Global library search retains its original tab, collection and account context. Late results after cancellation cannot replace restored state. Saved metadata and downloaded-copy notices describe the active view; authentication failures retain sign-in guidance.
- Download removal can ask for confirmation. It removes local copies while preserving source originals and reading history.
- SMB transfer cancellation closes the operation's connection and skips graceful remote close requests after cancellation. Initial socket connection retains its finite timeout.

## Source and design references

Application baseline: main9ff11f5. The release tag changes Android version metadata to0.3.9/30900; helper repair e18ccfa affects release snapshots only. Design reference: approved leaf-reader-design21f960d exports and the existing onboarding/library handoff. No shared Kotlin API or Room schema change accompanies this release; Android Room remains9.

Relevant Android files are data/settings/FirstLaunchStateStore.kt, ui/onboarding/FirstLaunchViewModel.kt, FirstLaunchHost.kt, FirstLaunchScreen.kt, ui/library/LibraryViewModel.kt, LibraryScreen.kt and data/smb/SmbClient.kt under app/src/main/java/net/digitalbc/leafreader. The MBassador reflection rule in app/proguard-rules.pro is specific to Android's optimized SMBJ build.

For iOS reconciliation, compare fresh install, existing install, picker cancellation, failed import/connect, cold relaunch and cancelled late search results with the current iOS implementation. Preserve installation-local markers, explicit server consent, source-file protection and whole-page reading. Android's Room/SharedPreferences/SMBJ/R8 mechanisms are platform details, not code to port directly.

Android evidence includes fresh unit/lint/build checks, scoped S25/S10 QA, exact signed-APK16KB runtime checks and a local compatible-key upgrade. Exact-candidate restore, final store review and Play delivery remain separately pending. This handoff does not assert new iOS acceptance, physical tablet coverage, TalkBack/VoiceOver completion or a universal performance improvement.
