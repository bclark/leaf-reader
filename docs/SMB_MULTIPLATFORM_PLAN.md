# SMB shared-core migration plan

Date: 2026-11-04

Status: Original migration proposal; shared contract and Android adoption are now implemented. See [shared API notes](SMB_SHARED_API.md).

Companion: [iOS implementation handoff](SMB_IOS_HANDOFF.md)

## Outcome

Users configure their own SMB share, browse folders, select comic files, and download local copies. Reading, detection, and library management continue to work offline after import.

Put portable models, path rules, listing policy, and browser state transitions in Kotlin commonMain. Keep SMB transport, credentials, files, task ownership, and library persistence on each platform. Compose and SwiftUI remain native.

Do not try to run SMBJ on iOS or move platform networking into commonMain. Start with pure synchronous Kotlin helpers exported through the existing LeafShared framework; a shared coroutine runtime or Swift Flow bridge is unnecessary for this feature.

## What exists today

Paths below are relative to the repository root.

| Area | Current implementation |
| --- | --- |
| Android transport | app/src/main/java/net/digitalbc/leafreader/data/smb/SmbClient.kt: SMBJ connection/authentication, directory enumeration, streaming download, progress and cancellation polling |
| Android persistence | data/settings/AppSettings.kt: host, share, username and password in SharedPreferences |
| Android import | data/library/LibraryRepository.kt: listSmbDirectory and importSmbComic; indexes downloaded files using indexCopiedFile |
| Android state | ui/library/LibraryViewModel.kt: folder loading, sequential batch import, status and summary |
| Android UI | ui/menu/MenuScreen.kt: configuration/test connection; ui/library/LibraryScreen.kt: source chooser and SmbDriveScreen |
| Shared | No SMB feature yet; NaturalOrderComparator and ComicFormatDetector can be reused |
| iOS | No SMB feature. LeafSettings stores preferences; LibraryStore imports local CBZ/ZIP, converts CBR/RAR to CBZ, and handles PDF |

Android currently lists folders and cbz/cbr/pdf/zip/rar files, hides dot-prefixed entries, and sorts folders before naturally ordered files. It supports anonymous access when the username is blank, optional host ports, and DOMAIN\user credentials. Downloads are sequential, with roughly 150 ms progress reporting.

Repository guidance is stale in one important respect: AGENTS.md says Android has no network permission or runtime networking, whereas the implementation and PRIVACY.md already describe optional Android SMB access. Reconcile that documentation as a dedicated implementation change; preserve the intended guarantee of no telemetry, analytics, cloud dependency, or automatic network use.

## Ownership after migration

| Shared Kotlin commonMain | Android | iOS |
| --- | --- | --- |
| Connection configuration without secrets | SMBJ adapter | Native SMB adapter |
| Entry and transfer-progress models | SharedPreferences/credential storage | UserDefaults for nonsecret settings; Keychain for password |
| Canonical relative paths and validation | SMB wire-path conversion | SMB wire-path conversion |
| Comic filtering and natural sorting | Coroutine jobs on IO dispatcher | Swift tasks and isolated transport execution |
| Immutable browser state and pure transitions | StateFlow/ViewModel binding | ObservableObject binding |
| Import phases and aggregate-progress calculation | Room, covers, archive indexing | LibraryStore, conversion, metadata |
| Portable error categories | SMBJ error mapping | Native error mapping |
| Unit-test fixtures and policy tests | Compose UI | SwiftUI UI |

Keep native filesystem handles, Bitmap, URL, File, NSError, SMBJ objects, credentials, and localized strings out of shared state.

## Proposed shared contract

Create shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/smb/. Names below are design targets, not existing APIs.

- SmbConnectionConfig: host, port (default 445), share name, base directory and username/domain as needed for display and validation. No password.
- SmbEntry: name, canonical relative path, directory flag, optional known byte count.
- SmbTransferProgress: transferred bytes, optional total bytes and bytes/second. Unknown totals produce indeterminate progress, not a fabricated zero-percent failure.
- SmbFailureKind: invalid configuration, authentication, access denied, unavailable, timeout, unsupported, IO and unknown. Cancellation is a separate outcome.
- SmbBrowserState: committed directory path, entries, selected file paths, loading flag, optional failure, and request generation.
- SmbBrowserLogic: validate/join/parent paths; filter and sort entries; begin/apply/fail a directory request; toggle/select-all/clear selection.
- SmbImportPhase and a small pure aggregate-progress helper: downloading, preparing/converting, importing, complete. Keep task scheduling and per-file import execution native.

Use explicit factory/helper methods for Swift callers where Kotlin defaults are needed. Verify the generated Objective-C header early, including nullable byte counts, Long bridging, enums and collection conversion.

### Paths and listing policy

Use slash-separated paths relative to the configured base directory; the root is the empty string. Translate separators only inside transport adapters.

Parse host/port separately from share/base-directory input. Preserve filename case and Unicode. Handle bracketed IPv6 and reject ambiguous malformed host/port input instead of copying the current split-on-colon parser.

Normalize separators, reject traversal outside the configured root, absolute child paths, NULs, and invalid ports. A server-supplied entry name must represent one child component; never use an unchecked remote name as a local destination. Filter dot entries and unsupported files centrally. Sort folders first, then NaturalOrderComparator; use the canonical path to break equal-name ties.

Do not assume remote symlinks remain inside the base directory. Exclude symlink/reparse entries unless the adapter can establish safe containment; document servers where entry type cannot be determined.

### Browser concurrency

A platform owner creates a monotonically increasing request token. A response can replace the listing only if its token still matches the active request. Cancel superseded native work where supported; the token still protects against late callbacks.

Commit a new directory path with its successful listing. A failed navigation retains the previous usable listing/path and shows an error. Clear selection when a new directory succeeds. Selection is restricted to importable files in the visible directory.

### Import transaction

1. Snapshot configuration, credentials and selected entries when starting a batch.
2. Download one file at a time into a unique app-private staging location.
3. Check cancellation, expected length when known, and the existing archive/PDF validation before publishing.
4. Feed the completed file into the platform's ordinary library import/indexing path.
5. Publish success only after file publication and library metadata work complete.
6. Remove staging files in all outcomes. A failed replacement must leave an existing local comic intact.
7. Continue after individual file failures; cancellation stops the batch and keeps already imported comics. Show imported, failed and unprocessed counts.

No remote writes, directory recursion, server discovery, streaming reader, download resume, automatic retry loop, scheduled sync, or background-transfer service in the first release.

## Delivery sequence

### 1. Extract portable policy and models

Add shared models, path/listing helpers, immutable browser transitions and tests. Characterize existing Android sorting/filtering and configuration behavior before replacing it. Keep archive and metadata logic where it is.

Exit: shared JVM tests pass and LeafShared exposes an ergonomic Swift API.

### 2. Adapt Android without changing the workflow

Use shared entry/progress types in SmbClient, and delegate path/listing policy to shared helpers. Keep SMBJ and blocking IO behind the existing repository boundary. Bind shared browser transitions to the existing LibraryViewModel and Compose screens.

Preserve existing preference keys/configuration. Do not silently reset saved credentials or make unrelated credential-storage migrations. Rethrow coroutine cancellation rather than turning it into an ordinary connection error. Test cancellation during blocked IO as well as between chunks; apply finite timeouts and close operation-owned resources safely.

Exit: configuration, test connection, nested browsing, selection, batch progress, cancel and local reading behave as before, with stale listing results suppressed.

### 3. Prove the iOS transport

Follow the companion handoff. Resolve package/toolchain/linking compatibility and test connect/list/download/cancel against a controlled share before constructing the full UI.

Exit: device and simulator builds work; cancellation cleans up; SMB2/3 and the required authentication modes work.

### 4. Integrate iOS library import and native UI

First add an awaitable staged-file import path. Then add settings, browser and sequential import UI using the shared contract. Preserve existing local imports and metadata semantics.

Exit: imports finish transactionally, local reading works after disconnecting, and permission/error/cancellation states are usable on iPhone and iPad.

### 5. Release verification and documentation

Run the tests below, reconcile AGENTS.md, update privacy disclosures and third-party notices, and record the exact iOS dependency version and integration evidence.

## Verification

- Shared: root/parent/join containment; Unicode/case; natural ordering; extension and hidden-file filters; stale responses; selection clearing; progress with unknown totals; cancellation versus failures.
- Android: saved-setting compatibility; fake transport tests for listing races and download failure/cancel cleanup; existing unit tests, assemble and lint.
- iOS: fake adapter tests for the same scenarios; async CBZ/CBR/PDF imports; filename collision and failed replacement preservation; continuation cancellation exactly once.
- Integration: controlled Samba/NAS share with a read-only account; anonymous where supported; domain credentials; nested folders; nondefault port; SMB2 and SMB3; bad password; permissions; server disconnect; large file; corrupt/truncated archive; disk full; cancellation; app backgrounding.
- Physical iPhone/iPad: local-network permission allowed/denied, recovery through Settings, responsive browser layout, progress VoiceOver labels and offline reading after import.

Run shared JVM tests first, then Android fullDebug unit tests/build/lint. For iOS follow the repository framework → XcodeGen → CocoaPods → workspace build order. Live SMB checks require an intentionally configured test server; CI unit tests should use fakes.

## Decisions remaining before implementation

- Exact iOS SMB dependency/tag, toolchain compatibility and verified cancellation behavior.
- Dependency licensing/distribution and notices for the actual linked artifacts.
- Consistent signing/encryption behavior supported by both adapters; never silently downgrade a server's required protection.
- Collision behavior: recommend confirmation before replacing a same-name comic on iOS, with atomic publication after confirmation. Android's UUID-based storage can retain its existing duplicate behavior.
- First-release lifecycle policy: recommend cancelling uncommitted imports on backgrounding, with cleanup and no automatic restart. Already imported comics remain available.
