# SMB shared API integration notes

Date: 2026-11-04

Status: Shared contract and Android adoption implemented; see verification below before release.

Related: [migration plan](SMB_MULTIPLATFORM_PLAN.md), [iOS handoff](SMB_IOS_HANDOFF.md)

## Where the code lives

shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/smb/:

- SmbModels.kt: configuration without a password, entry, byte-progress, failure categories, import phases and aggregate progress.
- SmbPaths.kt: relative-path validation, child/parent/join helpers and listing filtering/sorting.
- SmbBrowser.kt: immutable browser state and synchronous state transitions.

There are no platform or coroutine dependencies. SMBJ stays in Android; the native iOS adapter and its credentials remain Swift-owned.

## Contract for the iOS agent

Use the generated LeafShared API rather than translating these rules into Swift.

1. Parse the saved/form input through SmbConnectionConfig.companion.fromInputs(hostInput:shareInput:usernameInput:). The factory is throwing. Host accepts an optional smb:// prefix and port; bracket IPv6, for example [2001:db8::1]:445. Share input combines the share and optional base folder, such as Comics/Manga. DOMAIN\\user is split into username/domain. Password stays outside this object.
2. Create browser state with SmbBrowserState.companion.empty(). Native code owns the current state, tasks and observable publication.
3. Call SmbBrowserLogic.shared.begin(state:path:) for navigation. Retain its generation and pendingPath for that request. The committed path/entries remain unchanged until success.
4. List through your native transport. Native enumeration must reject symlinks/reparse points it cannot safely contain. Map wire entries to SmbEntry(name:path:isDirectory:sizeBytes:) using SmbPaths.shared.child(parent:name:).
5. Call complete(state:generation:entries:) against the latest state, not the captured request state. It filters/sorts the entries, commits the path and clears selection. Old responses return the current state unchanged.
6. On errors, use fail(state:generation:failure:). On dismissal/cancellation, use cancel(state:); it invalidates late responses while preserving the committed listing. On a connection/profile change or Forget Connection, use reset(state:); it clears the old listing/selection while preserving monotonically increasing request tokens. Do not replace active state with empty() during a profile switch, which would reuse old tokens.
7. Use toggle(state:path:), selectAll(state:) and clearSelection(state:) for selection.
8. Build remote paths with SmbPaths.shared.join(base:relative:), converting separators only if your SMB library requires it. User-visible paths stay relative to the configured base.
9. Map transfer byte callbacks to SmbTransferProgress(bytesTransferred:totalBytes:bytesPerSec:). Use totalBytes = 0 for unknown length. isIndeterminate distinguishes an unknown/zero total; no password, URLs, files or native handles enter shared state.
10. Use SmbImportProgress.shared.fraction(completed:count:transfer:phase:) for overall UI progress. A null result means indeterminate; bridge the boxed nullable Float deliberately. Download reserves 5% per file for local preparation/import. COMPLETE uses completed/count, so increment completed only after the native import transaction succeeds.

The configuration, path, entry, browser-state, failure-enum and progress call shapes above were checked with swiftc against the generated iOS simulator framework. For example, SmbFailureKind.IO imports as .io and SmbImportPhase.DOWNLOADING as .downloading. Native error localization and native task scheduling are intentionally outside shared code.

### Constructor/default conventions

Kotlin default arguments are not exported as Swift defaults. Supply all four SmbEntry fields and all three SmbTransferProgress fields explicitly. Use the configuration factory and empty-state helper rather than constructing partially valid state. Keep entry byte counts nonnegative; zero represents unknown/empty size.

### Paths and failures

The root is the empty string. Paths use '/'; legacy Android backslash inputs normalize to '/'. Absolute paths, '..', ambiguous dot/space-only components, NULs and colon-bearing path components are rejected. child requires a single name component. Preserve case and Unicode; do not lowercase path keys.

SmbListing.prepare(directory:entries:) accepts only direct children whose path matches their name. It hides dot-prefixed entries, accepts folders and cbz/cbr/pdf/zip/rar files, deduplicates path keys, and sorts directories first using NaturalOrderComparator with a path tie-breaker.

SmbFailureKind provides invalid configuration, authentication, access denied, unavailable, timeout, unsupported, IO and unknown. Cancellation is never mapped to a connection failure. Native adapters localize these categories without exposing native exception text, hostnames or credentials.

## Android changes

- SmbClient uses the shared configuration, entries, listing policy and progress models. Its SMBJ adapter alone converts paths to backslashes.
- Directory and download operations own their connection. A cancellation monitor closes that connection to release blocked IO after connection setup; initial connect remains bounded by the configured native timeout.
- Downloads stream to disk, compare the final length against the open file's reported length and delete failed/cancelled output.
- LibraryRepository rethrows cancellation for listings and imports. The existing archive/indexing path remains authoritative.
- LibraryViewModel binds the shared browser state, suppresses stale results, clears the previous server's listing when connection settings change, snapshots batch configuration/credentials and joins the previous import's cleanup before starting another batch.
- Compose consumes the shared selection/path state and supports indeterminate progress. Preference keys and the existing SMBJ security/dialect configuration are retained.

## Verification

Shared policy tests cover configuration parsing, root containment, Unicode, extension filtering, ordering, stale responses, cancellation, selection and indeterminate/aggregate progress. Android transfer tests cover byte copying, changed lengths, cancellation before write and safe error messages.

The opt-in SmbLiveTest reads literal values from the ignored root .env without executing it. It uses SMB_SERVER, SMB_PATH, SMB_USERNAME, SMB_PASSWORD and FILE-prefixed sample entries (including FILEFILE1). It tests connection/listing, locates a configured nonempty comic (including a bounded subfolder search for filename-only fixtures) and downloads it when local disk space permits, checks its detected format, and checks cancellation cleanup. It never writes to the remote share or includes configuration values in its failure messages.

Run explicitly:

    LEAF_SMB_LIVE_TEST=1 ./gradlew :app:testFullDebugUnitTest --tests '*SmbLiveTest'

Verified during this implementation:

- Shared JVM tests and shared iOS simulator tests.
- Android fullDebug unit tests, APK assembly and lint.
- Release iOS simulator framework build and a Swift type-check using the configuration, path, entry, browser and progress APIs.
- Opt-in live SMBJ test against the private configured server: connection, listing, configured-fixture lookup, full download, format detection and cancellation cleanup.
- git diff --check and confirmation that .env is ignored and untracked.

The live check ran on the host through Android's SMBJ adapter under Robolectric. It is not physical Android/iOS device evidence or proof of every NAS/authentication mode. The transfer test cancels after opening the remote file; a controlled stalled-server test is still needed to measure worst-case cancellation latency. Connection setup is bounded by native transport/negotiation timeouts rather than immediate cancellation. Credentials and remote sample paths are absent from the report.

An existing Android metadata test race was corrected: it now waits for both comics' updates before asserting both, rather than waiting only for the first comic.

The final combined verification passed with --no-daemon -Djava.io.tmpdir=/private/tmp -Pkotlin.compiler.execution.strategy=in-process. These command-line-only settings avoided a local compiler-daemon temporary-file stall; no project build configuration was changed.
