# iOS SMB implementation and verification

The iOS feature now consumes `docs/SMB_SHARED_API.md`: Add Comics offers From Files and From SMB Drive; Menu includes SMB Drive Settings. The native browser uses shared configuration, paths, filtering, natural sorting, selection, generation tokens and aggregate progress. The existing Files picker and offline reader remain available. One manually configured server/share/base folder is supported; there is no discovery, remote modification, streaming reader or background sync.

## Dependency and build integration

| Item | Selected value |
| --- | --- |
| AMSMB2 | Exact tag `4.0.3` |
| AMSMB2 revision | `1726aaaf7adf63d7d1d2a0c5d1b0e635028215c0` |
| libsmb2 submodule | `aff9fa6ba9f41cfd3c15d184554601ec3f6d8d03` |
| Selected manifest | Swift tools 5.9, Swift language mode 5, iOS 13 minimum |
| Installed compiler | Apple Swift 6.4 (`swiftlang-6.4.0.34.1`) |
| Leaf | Swift language setting 5.9; iOS 16 minimum |
| Linking | Dynamic `AMSMB2.framework`; libsmb2 compiled inside that framework |
| Existing dependencies | ZIPFoundation 0.9.20, libarchive-for-swift 1.0.0, TensorFlowLite pods 2.17.0 |

The tag, not upstream master or a README version example, was inspected. The exact pin is in `iosApp/project.yml`. The generated workspace's `Package.resolved` confirms that revision. This repository does not track generated workspace/project files or an existing standalone SwiftPM lockfile, so resolved revisions are recorded here. AMSMB2's swift-atomics dependency is for its own upstream tests; it is not in Leaf's resolved runtime graph.

Explicit `embed: true` and `codeSign: true` are required in the XcodeGen dependency declaration. A device launch test caught the missing framework before those settings were added. The corrected app contains `Frameworks/AMSMB2.framework/AMSMB2`, whose Mach-O install name is `@rpath/AMSMB2.framework/AMSMB2`; `otool -L` confirms dynamic linking. The framework builds alongside the existing CocoaPods dependencies.

AMSMB2's repository/combined library is LGPL-2.1, despite MIT headers in its Swift wrapper. libsmb2 library/include code is LGPL-2.1-or-later; its sample programs are not compiled. Bundled notices and full LGPL text live in `Sources/ThirdPartyNotices`, with pinned source links and rebuild instructions. No upstream source is modified. Distribution still requires corresponding-source/replacement and App Store licensing review; dynamic embedding alone is not a blanket compliance claim.

The selected library includes NTLM authentication, SMB signing and optional SMB3 encryption code. Its Apple AES path uses platform primitives, but other native protocol/cryptographic implementation is bundled. The previous unconditional `ITSAppUsesNonExemptEncryption = false` assertion has been removed rather than replaced with an unverified classification. Complete the App Store export questionnaire/review before release. See [Apple export compliance](https://developer.apple.com/documentation/security/complying-with-encryption-export-regulations).

## Native ownership and cancellation

`SmbTransport` owns one active operation and obtains its password privately from `SmbCredentialStore` at operation start. A fresh `NativeSmbSession` owns its AMSMB2 manager until native completion and disconnect; another operation is rejected while one is finishing. The adapter only provides connect, nonrecursive raw listing and file-to-disk download. Raw entries retain their native kind, including symbolic link/unknown. They are not safe browser entries until the shared policy validates them.

AMSMB2 dispatches blocking IO to its own queue. `downloadItem` streams through `OutputStream` with server-negotiated chunks; it does not collect the comic into a single `Data`. The adapter checks server-reported length and rejects short EOF. A progress callback returning false exits the native read loop. `OperationCancellation` is lock-protected; Swift task cancellation and explicit `transport.cancel()` set it. The owner does not resume early or remove staging while the native download might still be writing. Disconnect occurs exactly once. Progress is throttled to approximately 150 ms with a final known-length update.

**Cancellation limitation:** callback cancellation interrupts between SMB reads, not an already-blocked read. AMSMB2's timeout is set to 15 seconds, but its reply loop checks timeout on idle polls. DNS, repeated socket events and teardown can extend wall-clock cancellation latency. There is no claim of an unconditional 15-second deadline or forced socket interruption. Listing/connect cancellation likewise waits for the native callback before disconnect. Measure stalled-server behavior before release; if a hard abort deadline is required, investigate upstream support or an alternate native adapter rather than exposing unsafe concurrent handle destruction.

`SmbWireConnection` and `SmbWireEntry` are internal native values, bridged to shared models by `SmbSharedAdapter`. Callers supply a host-only SMB URL and an already-selected wire path. Passwords are never placed in that URL/model. Separate username/domain fields exist; blank username is passed explicitly, avoiding upstream's nil-credential guest default. Kerberos/domain/signing enforcement and negotiated dialect are not exposed or proven by this spike.

## Credentials and permission

`SmbKeychainCredentials` supports load/update/delete with a stable profile account key, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, and `kSecAttrSynchronizable = false`. Passwords are preserved byte-for-byte as UTF-8, including whitespace. No password is stored in UserDefaults or encoded into shared models. The native manager must never be encoded or logged (upstream supports encoding credentials).

`NSLocalNetworkUsageDescription` is authored in project.yml. No Bonjour declarations, multicast entitlement or ATS exception was added. No permission prompt is deliberately triggered at launch; Test/Browse actions initiate the connection. Timeout/unreachable errors suggest checking connectivity and Local Network access without asserting that a timeout proves permission denial. [Apple TN3179](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy) explains why outgoing local TCP requires permission.

`SmbProfile` validates saved and loaded preferences through the shared factory. Test Connection snapshots the unsaved form and password in memory without modifying saved credentials. Save writes the Keychain first; Forget deletes the password before removing preferences. Browser profile changes call shared reset without reusing generation tokens.

## Awaitable local import

`LibraryStore.importComicAwaitingCompletion` returns the published local URL only after preparation, publication and store bookkeeping. The existing `importComic(from:)` Files wrapper remains available and keeps its replacement semantics. SMB's internal `SmbComicImport.run` defaults to Keep Both; callers can explicitly request Replace or Cancel-on-collision. The browser exposes Keep Both (default), Replace, and Skip for existing comics before starting a batch.

- Downloads use an opaque filename in an app-owned unique directory. Remote names are not used as local paths.
- The local pipeline holds security-scoped access throughout copying, copies in 1 MiB chunks on a serial IO queue and checks expected byte count when supplied.
- Actual format comes from the shared `ComicFormatDetector` with extension fallback disabled for validation. ZIP entries are CRC-checked with streaming consumers, at least one image must decode, and PDFs must be unlocked with a usable first page. RAR conversion reuses CbrReader/CbrConverter and validates the resulting CBZ.
- RAR reading checks cancellation between headers/chunks. The existing converter still holds extracted RAR entries in memory; download memory is bounded, but bounded-memory RAR conversion is not claimed.
- The prepared file is a sibling on the destination filesystem. POSIX rename is the publication commit point; the old comic is never removed first. Failed preparation/rename leaves the previous destination intact.
- Metadata parsing runs off MainActor. Existing metadata and reading-list membership are preserved on replacement; last-opened behavior is retained. MainActor finishes bookkeeping even if cancellation arrives after publication. UserDefaults-backed metadata APIs do not expose durable write errors; disk-failure rollback of metadata is not claimed.
- Staging is removed on success/failure/cancellation after native writes stop. Startup cleanup removes only UUID-named directories older than 24 hours inside Leaf's own staging roots, excluding symlinks and fresh work.
- Download, preparation and publication are distinct callbacks. No polling `library.importing` or timer-based completion inference is used.

## Verification evidence

Initial shared JVM tests passed before native implementation. Both Kotlin device and simulator release frameworks were built before XcodeGen generation, followed by `pod install` and workspace builds.

A configured live server was tested using the user's ignored `.env` on the Mac. No credentials, host/share names, private filenames or comic bytes are committed or printed in the evidence. The read-only checks demonstrated:

- Named-account connection and disconnect; root and nested listings.
- Full streamed download of 142,054,782 bytes (and an earlier 5,160,065-byte fixture).
- Native callback cancellation of a larger 616,579,766-byte fixture after an 8,388,608-byte read; file size remained stable after native completion.
- Reconnection after normal operations and cancellation.

`iosApp/ci/smb-spike/run.sh /absolute/path/to/private.env` reproduces the macOS native checks using the production adapter source copied into an ephemeral Swift package. The environment file is parsed as data, never sourced as shell code. It uses SMB_SERVER, SMB_PATH (share/base), SMB_USERNAME, SMB_PASSWORD and FILE-prefixed fixture names. The current harness downloads FILE5 and uses the largest listed fixture for cancellation; supplied fixtures must exist in the configured base or its immediate subfolders. It only reads remote content and removes temporary downloads.

Swift Testing fake-session coverage includes raw listings/reconnect, complete imports, short downloads, disconnect failure, cancellation while IO is still active, rejection of overlapping operations, known/unknown size, redacted error mapping, Keychain round-trip/delete, CBZ/PDF/synthetic RAR5 imports, failed replacement preservation, Keep Both/Cancel, metadata/progress/list preservation, cancellation before preparation, failed publication, and stale-directory cleanup. Existing library, backup and reader tests are also run. Reader integration fixtures now use unique filenames and clear their progress, fixing repeated-run contamination discovered during verification.

Foundation-stage device verification: **52/52 tests passed**, including all 14 new SMB/import Swift Testing tests, on an arm64 iPhone 11 Pro Max running iOS 27.0.1. The arm64 simulator workspace build also passed on the iOS 27.1 runtime. All **14/14 SMB/import tests also passed on the arm64 iOS 27.1 simulator**. The full simulator suite stalled and was stopped, leaving an incomplete result bundle; it is not counted as passing. The focused feature suite completed successfully after that stop.

The final native macOS harness was rerun against the production adapter after short-read validation and explicit cancellation errors were added; the 142 MB full download, 8 MiB cancellation stop and reconnect checks passed again. No server identity or private fixture names are included here.

## Shared integration and batch behavior

`SmbBrowserModel` owns foreground tasks using ObservableObject for iOS 16 compatibility. Failed navigation retains the committed directory and selection. Cancellation invalidates the shared request token immediately but keeps controls disabled until IO/cleanup finishes. Done waits for cancellation cleanup; swipe dismissal is disabled during work. Backgrounding cancels, and returning does not automatically restart transfers.

Each batch snapshots configuration, selected shared entries and the Keychain password. It imports sequentially, continues after individual errors, and reports imported/failed/unprocessed counts. Successful earlier imports survive cancellation. Progress includes file index/name, downloaded bytes and average rate, then preparation/publication. Shared aggregate progress is indeterminate for unknown transfer length. Shared native listings discard symbolic links, unknown types and invalid child names before shared policy prepares them. The pinned libsmb2 maps every reparse-point attribute to its link type; the adapter also checks regular-file attributes before download. A hostile server changing paths between operations remains outside the trusted personal-server model.

Shared integration verification: shared JVM tests and both release frameworks passed; workspace device and simulator builds passed, and **57/57 device tests passed**, including five new tests for profile validation/secret exclusion, native-to-shared filtering, failed navigation/profile reset, continuing a batch after invalid content, and cancellation waiting for IO with correct unprocessed counts.

The updated native harness also passed against the private configured server after integration, including full 142 MB transfer, cancellation after 8 MiB and reconnect. The simulator focused-test launch stalled and was stopped without results; that integration run is not counted as passing.

Simulator UI automation installed and launched the app, but returned black screenshots and inconsistent simulator coordinates; visual/tap verification could not be completed. The physical device was eligible for builds/tests but unavailable to the UI interaction service. No permission or UI acceptance pass is claimed.

## Remaining acceptance work

- Physical iPhone/iPad Local Network allow/deny prompt, Settings recovery and live-server access from the device. Device unit-test execution is not evidence of those permission flows.
- Anonymous versus explicit guest, domain credentials, nondefault ports, Unicode filenames, required signing/encryption, SMB2/SMB3 negotiated dialect, read-only account enforcement, denied folders and bad passwords.
- Stalled socket/server shutdown/Wi-Fi loss cancellation latency, real ENOSPC, metadata persistence failure, malformed PDFs beyond the basic open/page check, and RAR4/encrypted/multivolume archives.
- On-device memory measurements for large downloads/conversions, foreground/background behavior and completed native imports opened with networking disabled.
- Release licensing/source archive and encryption export classification. Do not treat this spike as approval to ship the full SMB feature.
