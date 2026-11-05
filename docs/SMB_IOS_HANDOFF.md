# iOS SMB implementation handoff

Date: 2026-11-04

Status: Implementation proposal; native iOS foundation work is tracked separately.

Prerequisite: [Shared-core migration plan](SMB_MULTIPLATFORM_PLAN.md)

## Target experience

From Add Comics, choose From Files or From SMB Drive. An unconfigured drive opens configuration. A configured drive opens its root directory. Users browse, select files, import with progress, cancel safely, and then read entirely from local storage.

Keep the established Leaf palette, Anton/Archivo typography and native SwiftUI navigation. Files import remains available regardless of SMB configuration or network permission.

Initial scope: one manually configured server/share, optional base directory, read-only operations, sequential foreground batch import, CBZ/ZIP, CBR/RAR and PDF. No discovery, remote deletion, remote editing, or reader streaming.

## Start here: existing repository integration points

| File | Relevant behavior |
| --- | --- |
| Sources/LeafSettings.swift | ObservableObject backed by UserDefaults; no SMB preferences today |
| Sources/MenuView.swift | Existing application settings surface |
| Sources/LibraryView.swift | Presents MenuView and the system fileImporter; successful URLs currently call library.importComic |
| Sources/LibraryStore.swift | MainActor library owner; imports local files, updates ReadingProgress and ComicMetadataStore, then refreshes |
| Sources/CbrConverter.swift and Sources/CbrReader.swift | Existing RAR-to-CBZ conversion; reuse |
| Sources/Info.plist | Currently lacks a local-network purpose string |
| project.yml | Source of truth for dependencies and generated Info.plist properties |
| Tests/LibraryStoreTests.swift | Existing import/library regression tests to preserve |

Use shared types through import LeafShared. Do not duplicate shared path normalization, natural sorting, filtering or browser transitions in Swift. Do not add SMBJ to the iOS build.

## 1. Transport dependency spike

AMSMB2 is a candidate, not an approved dependency. Its upstream repository describes a Swift wrapper around libsmb2 for SMB2/3 and publishes Swift Package Manager integration. The current master manifest declares Swift tools 6.0, iOS 14 minimum, and a dynamic library product. Leaf currently declares Swift language version 5.9; verify the installed compiler and selected tagged release rather than assuming master will fit. The repository license is LGPL-2.1, despite MIT notices in some Swift source files. Review the chosen release, bundled C code and actual linking arrangement before adoption. [Repository](https://github.com/amosavian/AMSMB2), [manifest](https://github.com/amosavian/AMSMB2/blob/master/Package.swift), [license](https://github.com/amosavian/AMSMB2/blob/master/LICENSE).

Spike requirements:

- Pin an exact tested release in project.yml and record its resolved revision using the repository's package-resolution convention. Do not copy an unverified version from README examples.
- Build arm64 device and Apple Silicon simulator targets with Leaf's existing CocoaPods dependencies.
- Demonstrate connection, nested listing, file-to-disk transfer, progress callbacks, cancellation, disconnect, timeout and reconnect.
- Check anonymous and username/domain authentication, optional port, Unicode filenames, read-only shares, signing requirements and the negotiated SMB versions.
- Verify that cancellation actually stops IO; abandoning an await or ignoring callbacks alone is insufficient.
- Confirm large transfers stream to disk with bounded memory. Never load an archive into one Data buffer.
- Inspect the final app's embedded frameworks and dependency notices. Update THIRD_PARTY_NOTICES.md for the exact shipped dependencies.
- Revisit project.yml's existing ITSAppUsesNonExemptEncryption declaration against the chosen dependency and enabled cryptographic functionality; do not change it by guesswork.

If the candidate fails a required capability, document the failing case and evaluate another native adapter before adding a second transport abstraction. A local-files import through the system picker remains useful but does not satisfy the in-app SMB browser requirement.

## 2. Files and ownership

Suggested new Swift files; consolidate small pieces when that improves clarity.

| Proposed file | Responsibility |
| --- | --- |
| SmbService.swift | Small Swift protocol plus native adapter for test/list/download/cancel; maps native values and errors to shared models |
| SmbCredentials.swift | Keychain password load/save/delete |
| SmbBrowserModel.swift | MainActor ObservableObject; owns listing/import tasks and binds shared browser state to SwiftUI |
| SmbBrowserView.swift | Folder navigation, selection, progress, cancellation and summary |
| SmbSettingsView.swift | Host, port, share, base folder, username/domain, password, test/save/forget |

Keep the transport protocol native: async throwing methods for testing, listing, and downloading to a supplied staging URL, plus progress and operation cancellation. The method names are an implementation choice; do not invent calls to unverified upstream APIs.

The adapter consumes shared connection/entry models but obtains the password privately at operation start. Retain the native client for the operation lifetime. Serialize access unless the selected release's supported concurrency contract and tests justify otherwise. Avoid MainActor execution of blocking SMB operations; an actor alone does not make blocking IO nonblocking.

Own callbacks/resources by operation ID. Map errors once at the adapter boundary. Resume any continuation exactly once when success, failure and cancellation race. Throttle progress before publishing UI state. Task cancellation must cancel/close the operation-owned transfer, wait for IO termination, then permit staging cleanup.

Avoid shared Kotlin suspend methods, Kotlin Result exports and Flow subscriptions in the first version. The shared layer supplies synchronous policy/state helpers; Swift owns tasks and ObservableObject publication.

## 3. Preferences and credentials

Store host, validated port, share, base directory and username/domain in UserDefaults via LeafSettings or a small adjacent preferences object. Keep the password in Keychain; do not serialize it into shared models, UserDefaults, debug descriptions, logs, URLs or error messages.

Recommend a device-only, non-synchronizing Keychain item accessible while unlocked for foreground imports. Use a stable profile identifier rather than embedding the password or full connection URL in an item key.

Save only validated configuration. Testing unsaved settings uses a snapshot of the form. Forget Connection removes its saved password and configuration and invalidates pending browser requests. Editing connection settings cancels active work before reconnecting.

Match Android's blank-username anonymous behavior where the server supports it; distinguish explicit guest credentials from anonymous access. Never trim passwords.

## 4. Local-network access

Outgoing TCP connections to local-network addresses are subject to Apple's local-network privacy rules. Add NSLocalNetworkUsageDescription through project.yml's Leaf.info.properties and regenerate the project/plist. Suggested purpose text: “Leaf connects to your SMB drive to browse and import comics you choose.” Request access by initiating the user's Test Connection or Browse action. [Apple TN3179](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy), [purpose-string reference](https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription).

Manual unicast browsing is the proposed implementation. Do not add Bonjour declarations, multicast capabilities or broad ATS exceptions as speculative fixes. Consult the same Apple guidance if discovery is added later.

Present a useful connection error and permission troubleshooting. Do not label every timeout “permission denied”: the adapter may not be able to distinguish denial from an unreachable server. Offer opening app Settings where appropriate and a retry after returning.

PRIVACY.md currently says iOS makes no external network requests. Update it at feature delivery to describe opt-in connections to the user's configured SMB server, local credential storage and offline reading after download. Preserve the no-telemetry/no-cloud behavior. Reconcile the stale AGENTS.md network description without broadening runtime network use.

## 5. Make local import awaitable before adding the batch UI

Today LibraryStore.importComic(from:) is a MainActor method returning Void:

- CBZ/PDF copy and metadata work run inline.
- CBR/RAR import starts an unstructured Task and returns before conversion finishes.
- The CBZ/PDF branch removes an existing destination before copying the new file.

Calling this method in a sequential SMB loop would report completion too early for RAR files and could lose an existing comic on a failed replacement. Do not use a timer or poll library.importing to infer completion.

Introduce an internal awaitable import operation that returns the imported local URL or throws. Keep the existing file-picker entry point as a wrapper so its call sites remain straightforward.

Required pipeline:

1. Download into an application-private unique staging directory. Preserve a sanitized original filename separately for title/extension handling. Incomplete files must not appear under Documents/Comics.
2. Validate byte count when known and detect the actual format using ComicFormatDetector; reject unsupported/truncated input before publication.
3. Prepare a final CBZ/PDF using existing archive/PDF handling. Convert CBR/RAR with CbrConverter. Verify the result can be opened and contains usable pages.
4. Resolve collisions before publication. Recommend an explicit Replace/Keep Both/Cancel choice; a failed replace must preserve the old file. Keep Both chooses a unique filename.
5. Publish from a temporary sibling on the destination filesystem with a safe move/replacement operation. Do not remove the old destination first.
6. Update ReadingProgress and ComicMetadataStore consistently with existing local import semantics, refresh the library, and return success.
7. Clean up downloaded and converted staging files on success, failure and cancellation.

Run large copy/conversion/archive parsing off MainActor. Keep observable state and store publication on MainActor; inspect existing metadata-store synchronization before moving its mutations to another executor. Do not share an unsynchronized ZIPFoundation Archive across tasks.

A security-scoped file-picker URL must remain accessible for the actual copy lifetime. Stage it while access is held or balance access inside the awaited operation. SMB staging URLs are app-owned and do not need an external-document security scope.

Initially retain current local import reading-status behavior. Do not reset an existing replacement's reading progress or list membership incidentally; define and test that policy before changing filenames/metadata.

Expose distinct download/preparation/import phases. A finished byte transfer is not a finished comic import. Treat cancellation before publication as no import; after publication, finish required metadata bookkeeping and count the comic as imported.

## 6. Browser and batch behavior

- Add a source choice to LibraryView's Add Comics action; keep From Files on its existing picker.
- Add SMB settings to MenuView with Test Connection, Save and Forget Connection.
- Present SmbBrowserView using native navigation. Provide title, current path, Back/Up, refresh, loading/empty/error states and retry.
- Render shared filtered/sorted entries. Folder taps navigate; file taps select. Include Select All for visible importable files and Import Selected with a count.
- Use canonical relative paths as selection keys. Clear selection on a successful directory change.
- Each listing request gets a generation token. Ignore late responses even if underlying cancellation is delayed. Keep the previous committed path/listing after failure.
- During import, snapshot the selected entries and settings; prevent a second batch. Show file index/count, filename, bytes, transfer rate when known and a Cancel action.
- Process one selected file at a time. Continue after an individual failure. Cancellation stops the rest; successful earlier imports remain.
- Summarize imported, failed and unprocessed files, with per-file failure details that omit credentials.
- Cancel browser work on dismissal. Proposed initial background policy: cancel active uncommitted transfers, clean up when IO has stopped, and do not automatically resume. Defer cleanup of an interrupted publication until the local transaction is consistent.
- Make failed cleanup recoverable on next launch by deleting only stale files inside Leaf's own staging directory, never arbitrary URLs.

On iPhone use a pushed/full-screen browser consistent with the existing library navigation. On iPad provide a comfortable adaptive sheet or navigation container. Ensure VoiceOver labels describe selection, file/folder type and progress; keep controls accessible at large Dynamic Type sizes.

## 7. Tests and acceptance criteria

Unit tests use a fake SmbService and temporary library directories. Do not require a live NAS for ordinary CI.

- Listings: success, empty directory, access error, cancellation, late response ignored, failed navigation retains previous content.
- Selection: folders cannot be imported; shared extension filtering; Select All; reset after navigation.
- Transfers: known/unknown size, bounded progress updates, cancelled IO completion race, timeout, server disconnect and partial-file cleanup.
- Imports: CBZ, converted CBR/RAR, PDF, bad format, short download, duplicate-name policy, disk-full failure, failed replacement preservation, metadata failure and no premature success.
- Batch: continue after failure, stop on cancel, correct summary, no two simultaneous import batches.
- Preferences: nonsecret values restore; password stays out of UserDefaults; Forget deletes Keychain item.
- Regression: file-picker imports and existing LibraryStoreTests still pass.

Manual integration requires a controlled share and physical iPhone/iPad: permit/deny local-network access, recover via Settings, connect with supported authentication modes, import a large comic, cancel, disconnect server/Wi-Fi, rotate/resize browser, and read a completed import without a network connection.

## 8. Build and delivery

1. Run ./gradlew :shared:jvmTest --stacktrace.
2. Build ./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64.
3. Run xcodegen generate --spec iosApp/project.yml --project iosApp.
4. Run pod install with iosApp as the working directory.
5. Build and test iosApp/Leaf.xcworkspace, scheme Leaf, on an available iOS simulator. Use a real simulator destination for tests.
6. Build the iosArm64 framework and perform a physical-device build and local-network QA.
7. Record the pinned transport release, tested server configuration, supported authentication/security modes, cancellation evidence and any remaining limitations.

Commit authored Swift/shared files, tests, project.yml changes, applicable resolved dependency files, privacy updates and notices. Keep generated Xcode project/workspace, Pods, build outputs, real credentials and private test comics out of source control.

Definition of done: both platforms consume the shared SMB policy/models; Android's current flow remains usable; iOS can configure, browse and import safely; cancellation leaves no published partial comic; completed imports open offline; tests/builds and physical-device permission checks have recorded results.
