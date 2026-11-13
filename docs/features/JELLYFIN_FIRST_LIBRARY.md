# JF-01 · Jellyfin-first library

Status: **approved product direction; implementation plan, not shipped functionality**<br>
Created: 2026-11-11 · Shared Android/iOS feature contract<br>
Owner: Bryan · [Feature index](README.md) · [Design system](../LEAF_DESIGN_SYSTEM.md)

## Outcome

Leaf becomes a comfortable front end for a user's Jellyfin comic library without requiring Jellyfin to use Leaf. A connected user browses covers and reading progress, opens an informative detail screen, and taps Read or Continue. Leaf uses an existing local copy or retrieves the comic before reading. Device and SMB imports remain first-class, available offline in the same experience.

**Enough is decided to plan and implement in slices.** True streaming, discovery entitlement feasibility, cache policy and conflict semantics still need bounded investigation/decisions. Do not turn these unknowns into promises or silently choose incompatible behavior on the two platforms.

## Approved decisions and boundaries

| Decision | Contract | Source |
|---|---|---|
| Default library | Jellyfin is the default library for connected users; local-only use remains fully usable. | Bryan, 2026-11-11 |
| Entry flow | Cover + progress → detail → Read/Continue. Browse remote metadata without downloading every archive. | Bryan, 2026-11-11 |
| Read behavior | Use the on-disk copy first; otherwise retrieve the comic. Full-download is the first implementable path. | Bryan, 2026-11-11 |
| Connected views | Existing All, Series and Unread directly browse Jellyfin metadata/progress when connected, not only imported comics. | Bryan, clarified/approved 2026-11-11 |
| Availability | Downloaded combines readable device imports, SMB imports and downloaded Jellyfin copies; preserve source labels and reading-status distinctions. | Bryan, clarified/approved 2026-11-11 |
| Lists | Preserve Leaf lists and include compatible Jellyfin collections where supported by the server; visibly indicate source. | Bryan accepted recommendation, 2026-11-11 |
| Sync | Reliable two-way progress is required for the feature, not a later optional enhancement. | Bryan, 2026-11-11 |
| Onboarding | Offer Connect Jellyfin or Use without Jellyfin. Attempt local discovery where supported; always offer manual address entry. | Bryan's requested direction |
| Server upload | Sending device/SMB files to Jellyfin is a later phase, not a prerequisite. | Bryan, 2026-11-11 |
| Visual direction | Approved forest/sage/gold colors, soft rounded controls and clean outline icon direction; native accessibility/navigation remain authoritative. | Bryan, 2026-11-11; design system v0.3 |

Not approved by the mockup: wholesale bottom-navigation replacement, every generated label, invented comic metadata, exact raster-sampled color/radius values, or claims that sync is already implemented on iOS. Preserve existing library views where useful rather than replacing All / Series / Lists / Unread with three overlapping filters.

## Information architecture and flows

### Library

**Owner clarification, 2026-11-11:** Jellyfin comics belong in the existing main library cover grid, retaining its Leaf shell, column controls, search and All/Series/Lists/Unread navigation. A separate connected catalogue screen with similar filter labels is an intermediate implementation, not the target. Remote covers must work with no local comics and without bulk archive imports. Apply this surface requirement on both platforms while retaining native behavior.

- **Connected:** existing **All**, **Series** and **Unread** directly browse the Jellyfin catalogue and its metadata/progress, including remote-only comics. Do not leave these views local-only behind a separate Jellyfin importer. All is the connected comic catalogue, Series groups supported remote series metadata, and Unread uses supported remote reading state under the agreed sync/conflict policy. Missing metadata/state needs an honest fallback, not fabricated series or unread status.
- **Downloaded:** one cross-source view combines readable device/SMB imports and downloaded Jellyfin copies. Show provenance without duplicating a cached remote identity. Local-only mode retains existing usable local views; server failure must not block downloaded reading.
- **Lists:** retain existing Leaf lists and include compatible Jellyfin collections when the server exposes them. Label Leaf versus Jellyfin origin; use server/user/collection identity, not matching names, and preserve unsupported/empty/error states honestly. This approves browsing compatible collections, not creating/editing/deleting collections on the server, automatic migration of Leaf lists, or universal playlist compatibility.
- Keep the established All / Series / Lists / Unread navigation, with Downloaded accessible across sources. Source selection is supplemental, not a replacement for the connected-view mapping above. Item selection follows detail → valid cached copy or cancellable download-on-read → reader; current explicit-Download-only slices are incomplete against that target.
- Existing local content, lists and reading position must survive migration. Do not require reconnecting a server to read a complete local copy.
- Covers show meaningful reading progress and a distinct offline-availability indicator. A checkmark must not ambiguously mean both downloaded and synced.
- Retain search, sorting and reading-status/series/list navigation as appropriate. Preserve browsing position on back navigation.
- Identity includes server, user and item identity. Do not merge different editions solely because their titles match. A matching cached remote item should not appear as a duplicate second comic.

### Comic detail

- Cover, title, available creator/series/issue information, synopsis, progress and Read/Continue primary action. Omit absent metadata gracefully rather than fabricate it.
- Separate Download/Remove download controls from sync status and from removing a library entry. No server deletion is part of this feature.
- Read immediately uses a valid complete local copy. Otherwise show preparation/download progress with Cancel and a recoverable failure state; only expose the reader once the archive is usable.
- Explicit Download means retain for offline reading. Cache eviction/pinning policy must be agreed before implementation; never evict device/SMB originals as disposable remote cache.
- Proposed sync statuses: synced, pending offline changes, retry needed, conflict. Show truthful state, not an optimistic success check before acknowledgment.

### First run and connection

- Connect Jellyfin and Use without Jellyfin are both clear choices; skipping is reversible through Sources/settings.
- Discovery is a convenience, never the sole path. No server found, denied local-network access, remote server and unsupported discovery all lead to manual URL entry.
- Explain optional server requests and permissions in context. Preserve existing HTTPS and explicit local-HTTP safeguards; do not broaden insecure transport silently.
- Cancel/back/repeated connect, expired credentials, wrong credentials and server unavailability must leave usable local content and an understandable retry route.

### Read / sync lifecycle

1. Load persisted local state and pending operations. Browse metadata and fetch remote progress at appropriate foreground refresh/entry points.
2. Reconcile rather than overwrite unsent local progress. Obtain an agreed conflict result before writing conflicting state.
3. Persist reading changes locally immediately. Queue remote updates durably, scoped to the correct server/user/item; retry after reconnection and relaunch without crossing accounts.
4. Apply authoritative remote changes to existing downloads as well as newly retrieved comics. Completion, rereading and explicit mark-unread must work, not just monotonically increasing page numbers.
5. Display acknowledgment/failure honestly. Download completion is not progress-sync completion.

**Proposed conflict safety:** preserve both candidate positions and do not silently discard unsent local work. The exact choice of automatic resolution versus a user choice is OPEN (T-02); “highest page wins” is not a sufficient general rule because rereading/reset is intentional. No timestamp strategy is assumed correct without validating available server semantics and clock behavior.

## Acceptance contract and parity ledger

All rows begin **not accepted** for this new feature. Existing foundations below are not final-feature passes. Engineers record version/build, test/fixture, result and evidence in their notes using these IDs, then update this matrix with links.

| ID | Required outcome | Android | iOS |
|---|---|---|---|
| JF-A01 | Connected All/Series/Unread browse remote metadata/progress without bulk downloads; preserve Leaf Lists and expose compatible source-labelled Jellyfin collections; keep local-only views usable | Not accepted | Not accepted |
| JF-A02 | Cover opens detail; metadata fallback, back/search/filter state work | Not accepted — [Android UI slice/evidence](../ANDROID_ENG_NOTES.md); unit/lint/build and synthetic previews verified; physical navigation/accessibility pending | Not accepted — [iOS detail slice/design](JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md); build/tests and bounded synthetic navigation verified; full acceptance pending |
| JF-A03 | Read uses valid cached copy or cancellable download; retry/insufficient storage/corrupt archive/network loss handled | Not accepted | Not accepted |
| JF-A04 | Downloaded filter works across sources; complete copies open offline after cold start | Not accepted | Not accepted |
| JF-A05 | Progress and remote outbox survive interruption/relaunch; retry sends to correct account and no other | Not accepted | Not accepted |
| JF-A06 | Other-client progress reaches an existing download; conflict, reread, mark-unread/completion follow the same policy | Not accepted | Not accepted |
| JF-A07 | Optional onboarding, discovery fallback, manual connection, cancellation and expired authentication work | Not accepted | Not accepted |
| JF-A08 | Upgrade preserves comics, identities, progress, lists and settings; no duplicate remote/local representation | Not accepted | Not accepted |
| JF-A09 | Native accessibility, large text, contrast, touch targets, light/dark, phone/tablet/Fold layouts pass | Not accepted | Not accepted |
| JF-A10 | Disconnect/account switch, cache removal and item disappearance preserve data and explain consequences | Not accepted | Not accepted |
| JF-A11 | Disclosures/help accurately describe remote metadata/progress traffic and optional offline use; no streaming claim without proof | Not accepted | Not accepted |

Minimum shared scenarios: clean connected and local-only install; upgraded populated library; remote-only comic; existing cached comic; device and SMB import; offline reading then reconnect; two devices changing the same comic; reread/mark unread; account/server switch with pending changes; process termination during download and sync; revoked credentials; server item removed/replaced; storage exhaustion; missing metadata/cover; large library pagination; portrait/landscape/tablet/Fold and assistive technology. Use rights-cleared or synthetic fixtures, no credentials or real private library dumps in shared evidence.

## Current foundations versus gaps (source review 2026-11-11)

- **Android:** `LibraryRepository.kt` contains a persistent Jellyfin outbox, revision-checked deletion, pending-user flush and remote reconciliation. `reconcileItemProgressFromRemote` currently avoids replacing local state while a pending outbox entry exists. This is a useful implementation baseline, not proof of the new conflict policy, all lifecycle cases or release parity. Retain existing test/evidence scope in Android notes.
- **iOS:** `JellyfinBrowserView.swift` explicitly says reading progress is not synced. Existing `JellyfinClient.swift`, `JellyfinBrowserModel.swift` and `JellyfinImport.swift` provide browse/import foundations. Reliable two-way sync is new work, not a cosmetic parity change.
- Both platforms currently have import-oriented flows; the remote-first catalogue/detail/cache model requires deliberate storage/identity/migration work. Audit reusable shared Jellyfin policy/models rather than duplicating policy by default.
- Root AGENTS.md contains historical product and palette descriptions. Preserve its applicable engineering rules, but use the current design system for the approved visual direction; do not reintroduce removed panel features or old crimson branding based on historical prose.

This review ran no builds, live server tests or device actions and establishes no acceptance results.

## Implementation sequence

1. **Contract and feasibility:** resolve T-01 through T-04 enough to write shared fixtures and a data/identity/migration design. Map existing sync tests to the acceptance IDs. Capture key UI states using the approved design direction.
2. **Common behavior foundations:** catalogue/cache identity, durable progress/outbox and conflict contract; build deterministic shared policy tests where appropriate, native persistence/network adapters where necessary. Close iOS's sync gap.
3. **Vertical slice on each platform:** connect → catalogue → detail → download-on-read → offline reading → two-way reconciliation. Add source/download filters without regressing local/SMB or lists. Keep incomplete work behind the project's chosen development boundary rather than claiming it ready.
4. **Parity acceptance:** run the same scenario fixtures and compare outcomes, then platform-specific accessibility, lifecycle, archive handling and physical-device acceptance. Shared contract plus native evidence, not identical pixels.
5. **Release preparation:** only after required rows pass, reconcile docs/privacy/store claims and exact artifacts through existing platform release processes. This spec does not authorize upload, submission or publishing.
6. **Later, separate scoped feature:** server upload/transfer after feasibility, destination permissions, duplicate handling and user confirmation are defined. True streaming may be added only after demonstrated format/server support; neither blocks the approved download-on-read first implementation.

## Technical decisions and bounded investigations

| ID | Open work | Required output / decision boundary |
|---|---|---|
| T-01 | True streaming feasibility by archive format/server API | Prove whether useful pages can be accessed before full archive completion and what cancellation/random access require. Do not call full-archive download streaming; do not show an unsupported setting. |
| T-02 | Conflict semantics, progress mapping and completion/reset | Document current Jellyfin fields/version support, page index/percent mapping, pending-local vs remote cases, and reset/reread semantics. Propose one cross-platform rule with tests; seek Bryan if it changes expected behavior or risks losing progress. |
| T-03 | Catalogue/cache storage and migration | Define remote identity, cache validity, duplicate policy, explicit-download retention vs transient cache, storage limits and server replacement behavior. Preserve local originals. |
| T-04 | Local discovery feasibility and permissions | Validate Jellyfin UDP discovery on supported networks/platforms, including iOS local-network/multicast requirements. Manual entry always remains. No entitlement or security-setting changes assumed authorized here. |
| T-06 | Compatible Jellyfin collections and remote view semantics | Verify Book-compatible BoxSet/collection discovery and membership, account/library scope, pagination/order, missing series/user-data, unsupported mixed members and version differences. Preserve Leaf lists; no server-side edits. API support alone is not live acceptance. |
| T-05 | Later file transfer to server | Do not assume a general Jellyfin upload endpoint. Investigate supported plugin or separately configured writable storage, permissions and atomic import behavior in a separate spec. No automatic upload/delete. |

## Design reference and assets

[LEAF_DESIGN_SYSTEM.md](../LEAF_DESIGN_SYSTEM.md) v0.3 is the visual authority: forest/sage/gold, rounded branded buttons/selectors and native outline icons. Native navigation, touch targets (44pt iOS / 48dp Android), scalable text, contrast and reduced motion stay mandatory. Tablets need responsive composition, not an enlarged phone screenshot.

The owner-approved library/detail/onboarding image is a concept. Its fictional comics, apparent sync state, navigation and sizing are not implementation evidence. The dewy leaf illustration pack is a separate raster asset deliverable; integrate actual assets through the design repository rather than linking private internal storage identifiers here. Required additional design states: downloading/cancel, offline, expired auth, pending sync, sync conflict, empty library and wider tablet detail.

## Decision history

- **2026-11-11 | Bryan | APPROVED:** Jellyfin default, browse covers/progress → detail → read/download/cache, downloaded-only cross-source view, reliable sync required, file transfer later.
- **2026-11-11 | Bryan | APPROVED:** mockup colors, rounded button shapes and icon direction added to design system v0.3.
- **2026-11-11 | Bryan | REQUESTED:** feature docs as a shared resource, platform engineering docs refer to the next feature to improve parity.
- **2026-11-11 | aux | DOCUMENTED:** initial contract, explicit unknowns, unaccepted parity ledger and native evidence boundaries. No application implementation, acceptance waiver or release action implied.

## Implementation record — 2026-11-11 | Android detail-data foundation

Bryan authorized starting JF-01. Added shared server/user/item identity and optional detail metadata models without changing the existing JellyfinItem constructor used by Swift. Android now has an authenticated metadata-only detail request and an account-scoped repository entry point. Detail reads neither download archives nor reconcile/flush progress; stale responses after disconnect/account change are discarded. List/detail parsing shares null-safe metadata and media-source format/size fallback.

API reference: Jellyfin [10.10.7 UserLibraryController](https://github.com/jellyfin/jellyfin/blob/v10.10.7/Jellyfin.Api/Controllers/UserLibraryController.cs) provides GET Items/{itemId} with explicit userId. This implementation uses that route, preserving a configured reverse-proxy prefix. Optional overview, people, genres, series/issue, image/ETag and user-data fields remain absent when unavailable. ETag and last-played date are captured metadata, not a validated conflict-ordering or cache-validity rule.

VERIFIED: shared JVM and both Android debug unit-test tasks passed (IDE shell-178; final repository changes retested in shell-179). New tests cover scoped identity, optional metadata, request/auth shape, malformed/unauthorized/missing responses, media-source fallback, wrong-account rejection and held-response disconnect. MockWebServer fixtures only; no personal-server requests, native iOS build, new UI or device acceptance. See [Android engineering notes](../ANDROID_ENG_NOTES.md) and [compliance record](../../RELEASE_COMPLIANCE.md). JF-A02 has a data foundation; every parity acceptance row remains not accepted.

Next implementation slice: catalogue/detail navigation and native loading/error states using this data path, then Read/Continue with existing complete-copy reuse and cancellable retrieval. T-02 progress mapping/conflict and T-03 retention remain unresolved; owner questions are recorded as Android Q-019/Q-020. Do not interpret the offered recommendations as approval, change existing sync precedence, introduce cache eviction or claim iOS sync parity. Existing outbox/page-tick mapping still needs the shared two-client fixtures described in T-02.



## Implementation record — 2026-11-11 | iOS detail and identity slice

Added an iOS wire adapter to the shared detail/identity models, authenticated metadata-only requests with returned-item/session checks, and native detail navigation with optional metadata, explicit Download/cancel and retained-copy Read/Continue. Browser reappearance preserves the loaded folder/page state. Import mappings now recover across reconnects using server/user/item identity instead of the replaceable credential profile ID; existing checksum/publication validation remains. No bulk file/progress/list migration or deletion.

[iOS foundation design](JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md) records the concrete storage/sync proposal and version-pinned Jellyfin Web CBZ/PDF mapping evidence. T-02/T-03 choices remain pending in Android Q-019/Q-020, cross-referenced by IOS-Q-022/023; no conflict winner, outbox behavior or eviction is enabled. Existing disclosure that iOS progress is not synced remains accurate. Connected-default catalogue, source/download filters, read-triggered download and reliable two-way sync remain outstanding.

BuildProject including tests passed; shared JVM command succeeded with UP-TO-DATE tasks. Fifteen selected native tests were requested but the Xcode tool stalled without results and its wait was terminated; compilation is not an execution pass. Added tests cover optional/shared metadata, authenticated detail route, wrong response identity/configuration/authentication and account-safe reconnection. Runtime/device evidence and exact source/build logs are tracked in [iOS engineering notes](../IOS_ENG_NOTES.md) and iosApp/build/jf01-ios-20261111/. All JF-A01–JF-A11 rows remain unaccepted. No personal-server writes, Apple changes, commit, upload or release.


2026-11-11 | iOS verification follow-up: simulator retry passed **15/15 JellyfinTests**, zero failures/skips/not-run. Synthetic localhost-only device interaction verified sign-in → library → detail → back at default text size, with title/series/issue/creators/synopsis visible. Actual download/read/cancel, offline UI, covers, accessibility matrix and two-way sync were not manually verified. Fixture connection removed, server stopped and session closed. The earlier stalled attempt remains historical evidence; it does not invalidate the successful scoped retry. See iOS notes/evidence directory above. No full acceptance row closed.

## Implementation record — 2026-11-11 | Android catalogue-to-detail UI slice

Comic rows now open native details, including already downloaded and unsupported items; separate 48dp checkboxes preserve batch download selection. Visible catalogue/detail covers load independently with a book fallback. Cover requests now use the existing cancellable HTTP wrapper. Returning from details retains the loaded folder, selection and list scroll state; detail state remains in the activity ViewModel across configuration recreation. No catalogue reload is triggered by detail back navigation.

The detail page renders available title/series/issue/year, creators, synopsis, genres and explicitly labelled Jellyfin reading status. Missing metadata is omitted, server HTML synopsis is rendered as plain text, and no new page-tick interpretation or sync-success claim is introduced. Loading, authentication/access/missing-item errors, retry and explicit Download/cancel reuse the existing data/import paths. Existing copies are currently opened from My Comics; direct detail Read/Continue and read-triggered retrieval are still next work.

VERIFIED: shared JVM plus both Android debug unit suites and full-debug assembly passed (shell-187); final account-retry/cover-cancellation tests, both debug lints and full-debug assembly passed (shell-188). Final presentation changes passed IDE assembly and both lints. Five new ViewModel tests cover detail close/filter preservation, superseded requests, retry, missing auth/folders and account changes including retry; one client test covers cancellation of an unresponsive cover request. Scoped synthetic Compose renders inspected: dark phone detail, light tablet detail, 2× text authentication error and catalogue with separate checkboxes. Render review found and fixed dark-theme inherited-text/Retry contrast. Initial IDE resource-cache render failure was resolved by building through the IDE; it was not a runtime failure.

No personal-server request, phone installation, archive download/read UI interaction or physical rotation/accessibility test was performed. Image fallback renders do not establish real cover decoding. Connected-default landing, catalogue search/pagination/filter expansion, valid-file availability, direct Read/Continue, cache policy and reliable cross-platform sync remain outstanding. All JF-A01–A11 rows remain not accepted. See [Android evidence](../ANDROID_ENG_NOTES.md); pending T-02/T-03 decisions are unchanged.


### iOS durable progress foundation and device evidence — 2026-11-11

Native atomic progress/outbox storage is implemented but not wired to production reader/sync. Revision acknowledgment preserves newer edits and competing remote state; account identity and corrupt/write-failure cases have coverage. Physical iPhone 11 Pro Max / iOS 27.0.1: 26/26 focused tests pass, including download/reconnect/offline reopening and in-flight cancel/retry. Device tests exposed missing download progress; delegate-driven transport corrects it. [Foundation status](JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md), [engineering notes](../IOS_ENG_NOTES.md), [retained results](../../iosApp/build/jf01-progress-20261111/physical-result.json). No rendered-device UI, live two-client sync or complete JF-A acceptance claimed; T-02/T-03 and acceptance statuses remain unchanged.


### iOS reader-edit recovery integration — 2026-11-11

Local capture is now wired: native resume records save a revision alongside page state; checksum-validated download restoration recovers those edits into the account-scoped outbox. Acknowledged revisions do not requeue on reopening. Legacy untouched progress does not invent remote intent, and outbox errors preserve local reading. This is recoverable native checkpointing, not a cross-file atomicity or completed remote-sync claim. [Foundation implementation](JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md), [iOS notes](../IOS_ENG_NOTES.md), [63/63 physical-device tests](../../iosApp/build/jf01-reader-progress-20261111/physical-result.json). Tests include live reader gestures/rendering through an in-process device window; one QoS warning occurred in a separate library rendering test. T-02/T-03 decisions, remote application/writes and all JF acceptance states remain open.

## Android retained-copy Read/Continue — 2026-11-11

Details now open an existing local copy directly in the current reader, preserving native page/slot state and refreshing it when returning. Read/Continue is independent of remote metadata loading/failure. The lookup uses server/user/item provenance without credentials or network; navigation rejects a different active account, while a disconnected session may still use its captured identity. A consumed navigation request is not replayed on recreation. Resume/repeated taps do not replace an in-flight Read check.

Before offering/opening, the repository checks file presence/nonzero size, archive open, recorded page-count match and a nonempty first-page read on IO. Missing/corrupt/mismatched copies show a retry state and are not removed or overwritten; comic records, links and saved position remain. This bounded usability check is not a checksum or proof every page decodes. No automatic repair, edition replacement, eviction or download-on-read was introduced.

VERIFIED: shell-191 shared JVM/both Android unit suites/full-debug assembly/both lints passed; final IDE build reran both Android suites/assembly/lints, reporting 134 passed/1 skipped/0 failed per flavor. Added two repository and four ViewModel cases covering credential-free/network-free access, preserved progress, unusable copies, metadata failure/disconnect, revalidation, account isolation and single navigation. Synthetic previews inspected local Continue with remote failure at 2× phone text/tablet, and remote-only light-tablet Download. No personal-server/device/install/read gesture was performed.

JF-A03 has retained-copy groundwork; complete acceptance remains open, including physical offline/cold-start behavior, all-page integrity/error handling, remote retrieval/cancellation/storage failures and agreed T-02/T-03 behavior. Explicit Download remains the remote-only action. See [Android notes](../ANDROID_ENG_NOTES.md) for evidence; shared owner questions remain unanswered.


### iOS explicit two-way sync implementation — 2026-11-11

Downloaded comic details now offer explicit Sync reading position, with shared baseline/local/remote comparison, remote reset/completion application, durable pending retry and exact-revision acknowledgment. Conflicts retain both candidates and offer per-conflict device/server/defer choices; stale choices are re-fetched and rejected. This does not settle a global automatic-resolution preference or cache-retention policy. Android has not yet adopted the new common policy or native backup completion override, so JF-A05/A06 and overall parity remain not accepted.

The shared user-data endpoint supports one update for PlaybackPositionTicks and Played; iOS avoids playback-session side effects and verifies response/readback. Six new shared JVM tests pass; **78/78 focused native tests pass on the physical iPhone**, including real HTTP loopback round trips, model integration, both conflict choices, stale responses, offline/lost-response recovery and live-reader regressions. [iOS engineering record](../IOS_ENG_NOTES.md), [foundation design](JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md), [physical evidence](../../iosApp/build/jf01-sync-20261111/physical-result.json). Candidate privacy wording is updated locally. No actual Jellyfin account, hosted policy, release state or acceptance checkbox was changed. Real server/version/two-client behavior, the fetch-to-write concurrency window, lifecycle interruption and full accessibility/localization remain acceptance work.

### Android Fold retained-copy route verified — 2026-11-11

On the installed fullDebug0.3.7/30700 hash a9ba016212fd2ac9686a012b83ec934238bd28554bf3c23b04d274a65b25f91d, actual King Spawn - Vol.01 Jellyfin details rendered cover/title/series, Read and device page1of172. Read opened the retained PDF cover; Back returned to details/page1, then Back restored the identical catalogue positions/DOWNLOADED marker. No page advance; advanced Continue, offline cold-start and wider lifecycle/accessibility are not physically accepted by this check. [Android evidence and open findings](../ANDROID_ENG_NOTES.md), [artifact-scoped compliance](../../RELEASE_COMPLIANCE.md).

JF-A02/A03 gain bounded unfolded/default-text device evidence, remaining not accepted overall. Folder-to-folder position leakage/lost parent restoration and white status-bar content on the light detail background are verified open issues; detail-back preservation itself passed. No source fix, cache/conflict-policy change, build, release or iOS action in this follow-up.

## Android connected-default entry — 2026-11-11

New connected Android app sessions now open Jellyfin directly; local-only sessions keep My Comics. A single Books collection opens automatically; multiple Books collections offer a choice, and absent collection typing falls back to the existing library picker. Display names are not used as collection identity. My Comics, Libraries and Settings remain available through loading/error states. Loaded browsing state survives source reentry; account changes invalidate old landing responses.

Automatic startup reads catalogue metadata/covers without reconciling native progress or flushing pending writes; existing explicit browse/import behavior is unchanged. This implements the connected-default portion of JF-A01, not unified source/download filters, automatic download-on-read, new conflict policy or eviction. [Android candidate privacy wording](../../PRIVACY.md) records startup traffic locally, preserving the concurrent iOS sync disclosure.

VERIFIED shared JVM, both Android unit suites, assembly and both debug lints; final Android suites report140 passed/1 skipped per flavor. Three shared selection tests, five ViewModel landing tests and one repository no-progress-mutation/no-outbox-flush test added. Light/dark/error/large-font source-control previews inspected. New fullDebug installed successfully on the Fold; physical cold-start/My Comics/back checks are pending because the device is locked. [Exact artifact and evidence](../ANDROID_ENG_NOTES.md), [compliance](../../RELEASE_COMPLIANCE.md). Existing folder-offset and light-detail status-bar defects remain open; all complete JF-A acceptance rows remain not accepted. No native iOS build or release/publication action.

### Android connected-entry physical follow-up — 2026-11-11

Bryan selected the Galaxy S25 Ultra as replacement test device. On the same fullDebug hash f02a28711578b7ff335fca86e36119b33ecf9bce4601782ef14f069aafe2aa1f, in-place install and installed-checksum verification passed. Personally exercised cold launch directly into Comics with real covers; My Comics→hardware Back and Settings→toolbar Back restored the same catalogue positions. Libraries→Books/Comics opened correctly. Existing local content/list/status controls remained visible; no reader or transfer initiated.

This resolves the preceding pending startup/source/back check on the S25 Ultra only, not Fold verification or whole-feature acceptance. Default dark/AMOLED portrait and existing configured account were used. Offline/local-only/account-transition/accessibility cases remain open, as do folder offsets and light-detail system-bar contrast. [Android device evidence](../ANDROID_ENG_NOTES.md), [compliance](../../RELEASE_COMPLIANCE.md). No source fix, new sync policy, iOS test/build, release-gate closure or publication.


## Owner clarification and bounded feasibility — 2026-11-11 | connected views and Lists

Bryan accepted the recommendation: All/Series/Unread browse Jellyfin directly when connected; Downloaded combines local/device/SMB and downloaded Jellyfin; Lists retains Leaf lists plus compatible source-labelled Jellyfin collections. This supersedes earlier generic source-selector wording and the interim iOS statement that Lists was undecided. Historical implementation records above describe their actual slice only, not completion of this clarified contract. T-02 conflict and T-03 retention decisions remain separate and open.

**Read-only technical check:** the version-pinned [Jellyfin 10.10.7 ItemsController](https://github.com/jellyfin/jellyfin/blob/v10.10.7/Jellyfin.Api/Controllers/ItemsController.cs) exposes parentId, includeItemTypes, isPlayed and pagination query parameters, with explicit BoxSet handling. It clears parentId for a BoxSet-only query, so do not assume a library-parent filter scopes discovery correctly: validate visible collections and Book-compatible membership under the authenticated user. This supplies an API route to investigate, not proof every server exposes comic collections or that collections and playlists are interchangeable. Existing Leaf shared userItems currently requests only Book,Folder, Recursive=false; current plumbing therefore does not establish collection discovery or whole-catalogue All/Series/Unread support. Extend shared contracts/fixtures before native implementations diverge. Native adapters must validate missing metadata, permissions, empty/mixed collections, pagination and account changes; collections with unsupported members must not pretend those members are readable. No live private-server request was made.

Keep JF-A01/A02/A03/A04/A08 unaccepted until their full requirements have evidence. No application code, build, test run, commit or publication is part of this clarification.

## Android connected-view implementation — 2026-11-11

Connected entry now exposes All/Series/Lists/Unread plus Downloaded. All/Unread use paginated recursive Book queries; unknown Played is excluded from Unread rather than fabricated. Series groups server metadata from loaded pages, with an explicit missing-metadata group and Load more. Scoped per-view page/scroll state and account invalidation retain navigation safely. Local-only/My Comics remain usable.

Downloaded combines readable device/SMB/Jellyfin copies with source labels and bounded archive/first-page checks; no files/records are removed on failure. Lists preserves native Leaf lists and adds labelled account-wide BoxSet discovery. Collection members are restricted to importable Books with authenticated ancestry in the selected Books library; raw offsets preserve pagination even when members are omitted. This is a bounded adapter, not universal Book-collection or playlist compatibility. Remote Read now performs cancellable full download/import before opening a validated retained copy. It adds no automatic eviction or conflict winner.

Folder positions now use server/account/parent saveable scopes. Detail headers extend forest through status insets; keyboard/safe-area handling and dark action contrast were corrected. Tests exposed/fixed observer initialization, stale-account download markers and cancellation of blocked HTTP reads. New catalogue metadata requests do not reconcile local progress or flush pending writes; original explicit folder/import behavior remains distinct.

VERIFIED:131 shared tests pass;148 Android tests pass/1 skipped per flavor; full debug assembly and both debug lints pass. Added3 shared/8 Android cases. Light-tablet/large-text-error detail renders inspected. Final fullDebug hash c929d40361e5fe91a354062a3bd64a0b4d6e709eb5d7917c296efbe428cd2e30 installed on S25 Ultra, but lock screen prevents new-flow physical verification. [Android report](../ANDROID_ENG_NOTES.md), [QA record](../local/ANDROID_JF_VIEWS_QA_20261111.json), [compliance](../../RELEASE_COMPLIANCE.md). Original S25/Fold passes remain scoped to their older artifacts; complete JF-A01/A02/A03/A04/A08/A09 acceptance stays open. No native iOS build, global T-02/T-03 decision, release or hosted-policy publication.

## Android existing main-grid integration — 2026-11-11

Connected and local-only startup now share LibraryScreen. AppNavHost passes the existing catalogue controller into this screen, removes the parallel connected screen from normal navigation and redirects restored legacy catalogue routes into the main library. Remote entries use the same ComicCard cover aspect, crop, typography, spacing and column controls as local comics, with direct remote metadata and explicit server status; no fake ComicEntity/file records are created. Cards open the existing detail/validated Read/download path. Unknown progress does not produce an invented percentage bar.

Existing All/Series/Unread controls select the paginated remote queries; Lists retains native Leaf list cards plus labelled server collections. Downloaded shows readable copies across sources. Grid saveable state is scoped by account/filter/search/collection/series. Remote-only cards expose no local edit or long-press action. Series currently uses metadata group cover cards with loaded counts and drill-down; it does not yet reproduce the local cover-peel stack animation or claim a complete index before all pages load. Local-only/native stack behavior remains unchanged.

VERIFIED full gate shell-368 and final shell-370: shared JVM, both Android unit suites, assembly and both debug lints pass;149 Android tests passed/1 skipped per flavor, shared131 remained passing. New empty-library regression obtains remote metadata/cover identity while local comics, remote links and pending outbox remain empty, with one metadata GET and no archive import. Dark main-card previews at390dp/default and2× text inspected. FullDebug0.3.7/30700 APK f70ff9c16598e7bfc27c0c0b3b95daa49396a6da064df09971bd1c38963f6b98 installed in place on S25 Ultra; cold launch personally shows the existing empty main library in light mode. Connected remote-cover/device acceptance awaits owner sign-in; no data reset or personal archive download was performed. [Android notes](../ANDROID_ENG_NOTES.md) and [compliance](../../RELEASE_COMPLIANCE.md) retain exact scope. Previous separate-screen results remain historical; no full JF/release gate or iOS implementation acceptance closed.


## Approved Series and Lists behavior — 2026-11-11

Bryan approved this refinement through the main conversation. This is the shared product contract for both native implementations, not a claim of completed implementation. It supplements the Library section and T-06; historical slices remain evidence of their stated scope only.

### Series: automatic organization, existing Leaf experience

- Populate the existing Leaf series presentation from Jellyfin metadata, retaining the established stacks/cover-peel animation, drill-down, navigation, search and applicable card/long-press interactions. Change the catalogue source rather than replace the UI. Remote-only items must not acquire fictitious local-file actions or server-write controls; provide truthful capability-aware actions in the existing interaction pattern.
- Group from supported series metadata, not guesses from title prefixes or arbitrary directories. Keep uncertain/missing series in **Unsorted**. Separate distinct runs/editions when reliable metadata allows it; a matching display name alone is not a stable series identity.
- Preserve sensible reading order: use supported volume/issue metadata, with deterministic natural-title ordering when structured order is absent. Distinguish missing/ambiguous numbering instead of inventing it; preserve explicit user order in Leaf lists.
- Validate a stable series key scoped to server, account and library, plus available run/edition metadata. Define safe fallback and metadata-change behavior in shared fixtures before claiming parity. Current trimmed-SeriesName grouping is an implementation baseline, not the complete identity contract.
- A series is not complete merely because one catalogue page loaded. Fetch/cache lightweight catalogue metadata with honest loading/partial-count states, stable ordering and pagination. Do not download every archive to build the index. A loaded count must not masquerade as the total issue count.

### Lists: intentional curation, clearly separated ownership

- Keep Leaf's existing Lists UI. Editable **Leaf lists** can reference local/device/SMB comics and remote Jellyfin comics together; adding a remote item to a Leaf list must not require downloading its archive. Preserve existing list IDs, membership and user ordering during migration.
- Show compatible **Jellyfin collections** alongside Leaf lists with clear origin labels, initially read-only. Do not automatically migrate, merge by name or push Leaf lists to the server. Server collections, playlists and ordered reading lists are not interchangeable capabilities.
- List membership uses durable item references, not titles or download paths. A remote reference includes server/account/item identity; local imports retain their local identity. Link a downloaded copy to its remote reference without creating a duplicate member. Leaf list IDs and scoped server collection IDs remain distinct even when names match.
- Preserve source/library/account permission checks and pagination for collection discovery/members. Unsupported or mixed collection members need honest filtering/explanation; errors and unsupported collections must not look like successfully empty lists. On server removal, disconnect or account switch, retain the Leaf list structure and local data, hide inaccessible private metadata appropriately and show unavailable references rather than silently deleting membership or binding it to another account.
- Server-side collection creation/editing/deletion and universal playlist support are outside this approved first implementation. Cross-source Leaf list editing is not server mutation.

### Offline and Downloaded behavior

- Make **Downloaded only** usable inside Series and Lists while retaining their grouping and navigation. Include valid retained copies regardless of device/SMB/Jellyfin origin; do not count incomplete transfers as available.
- Cache lightweight series/list metadata separately from archive availability. Offline, cached remote entries can remain visible with a clear needs-connection state; downloaded members remain readable. Never block local reading behind server login, falsely offer a remote-only item as offline-readable or remove list membership merely because its download was removed.
- Preserve current filter/search/scroll and series/list position through detail/reader/back, reconnect and refresh. Respect account boundaries and invalidate stale responses. This does not settle T-03 eviction/retention or T-02 conflict policy.

### Engineering follow-through and acceptance

Extend shared fixtures and native evidence for JF-A01/A02/A04/A08/A09/A10: distinct same-name runs/collections, missing metadata/order, series spanning pages, mixed-source Leaf lists, remote-to-downloaded identity, download removal without membership loss, unsupported/mixed server collections, offline reopening, account switching and metadata refresh. Validate stack/peel and long-press interaction parity on both platforms, alongside selection/detail/back and accessibility; do not equate main-grid routing with complete interaction parity.

Current Android limitations remain explicit: grouping only loaded entries by SeriesName, simplified remote group cards rather than local stack animation, and absent remote long-press actions. Mixed-source editable Leaf membership and the full stable-series/offline contract require implementation and evidence. iOS must use this same contract rather than a separate catalogue design. All JF/release acceptance states remain unchanged. No app code, build/test execution, server mutation, commit or publication is part of this documentation update.

## Android Series and Lists implementation follow-through — 2026-11-11 | Codex

The preceding Android limitations are historical at that snapshot. Current source reuses native stack/cover-peel rendering for remote groups and supplies scoped run keys plus volume/issue/natural-title ordering through shared JellyfinSeries. Series pages build a lightweight cached metadata index with honest partial counts. Uncertain identity stays in scoped Unsorted; the connected server's724 records currently all lack supported reliable run metadata. No title/directory grouping guess was added.

Android Room8 preserves Leaf list IDs/order and stores durable local/scoped remote membership independently of retained files. Remote long-press can add/create a Leaf list without downloading. Download import attaches a local copy to that reference; download removal preserves membership. Mixed-member reorder/removal and portable backup are supported. Another account cannot display the previous account's remote snapshot. Native Leaf lists and read-only source-labelled Jellyfin collections remain distinct. Downloaded-only keeps Series/list grouping and membership; cache restoration never makes a remote-only archive offline-readable.

[Android QA record](../local/ANDROID_JF_SERIES_LISTS_QA_20261111.json) distinguishes automated coverage, pre-layout-fix phone checks and final de175f1e92c5d92678845d46c100d7032d0a3ba0154bc5eff2f0ba61c90e731a startup/Series-filter verification. Full gate passed; temporary mixed-list phone check retained2 members while showing1 download. Full offline/accessibility/account-switch/device matrix, live collection acceptance, final layout re-render and compact stack-badge polish remain open. Shared model constructors retain the prior Swift-callable signatures, but no native iOS build or parity pass was performed. This is implementation evidence, not closure of JF-A01/A02/A04/A08/A09/A10, T-02/T-03 or release gates.



## Launch fallback when Jellyfin is unreachable — 2026-11-11 | aux

OWNER-APPROVED PRODUCT REQUIREMENT, not implementation or acceptance evidence. Bryan requested: when Leaf launches and cannot reach the configured Jellyfin server, smoothly go to the Downloaded/offline section and explain that the Jellyfin server is unreachable. Apply the same behavior on Android and iOS within the existing main library surface, not a separate error screen or directory browser.

- Fall back to the existing Downloaded view when startup establishes that the configured server cannot be reached; local browsing/reading must not remain blocked behind an indefinite server-loading state. Show a clear non-blocking explanation, for example: “Jellyfin server is unreachable. Showing downloaded comics.” Exact copy and native presentation may be refined without changing the meaning.
- Downloaded retains its cross-source definition: complete readable device/SMB imports and downloaded Jellyfin copies, with existing navigation and interactions. If none are available, show an honest empty Downloaded state alongside the connection explanation, not a false empty server library.
- Preserve the configured connection, local files, progress, cached metadata and list memberships. A temporary reachability failure is not logout, library removal, successful sync or permission to delete data. Keep authentication/access failures distinct from server-unreachable messaging while retaining access to eligible local copies.
- Keep a clear retry/reconnection route without forcing setup again. Recovery must not interrupt an active reader or discard the user's current browsing state. Exact timeout/retry mechanics remain implementation details to validate, not a newly chosen polling policy.

Acceptance remains open: validate cold launch with an unreachable server and with no network, both populated and empty Downloaded states, retry/recovery, preservation of local lists/progress and existing grid interactions, and distinct authentication failure. Record exact-candidate native evidence on both platforms before claiming completion. Existing JF-A01/JF-A05 and release checklist statuses are unchanged; this requirement does not settle T-02/T-03 or authorize code, server, credential, commit or publication actions in this documentation update.

## Android launch fallback implementation — 2026-11-11 | Codex

Android source now checks fresh startup library discovery with a bounded first-page request and falls back to the existing main Downloaded surface on launch failure. Cached discovery cannot be mistaken for current reachability. Connection/timeout, authentication and access failures have distinct notice states; Retry leaves local content visible until a network result succeeds. The fallback is transient, preserves the manual filter preference and carries readable-copy restrictions into native Series/list detail screens. Connection, cache, list membership and reading state are retained; no polling or automatic reader navigation was added.

[Exact Android QA record](../local/ANDROID_JF_LAUNCH_FALLBACK_QA_20261111.json): final shared/Android test/build/lint gate passed, both Android flavors159 pass/1 skip, large-text notice preview inspected. APK6a8e0b2f2f58da2a3d9e151fd8a60b028d72b1ecaaf39d56e06ddf290aed4e0f installed in place on S25. Physical offline launch/reconnection remains pending owner unlock; no network settings were changed. Native iOS parity and exact-candidate empty/populated offline/lifecycle acceptance remain open. This implementation record supplements the owner contract without closing JF-A01/JF-A05 or release gates.


## iOS main-library development evidence — 2026-11-11 | Codex

Implemented connected main-grid/Series, scoped catalogue cache, mixed Leaf references/read-only compatible collections, full retained download-on-Read, optional onboarding and explained offline fallback. Physical iPhone11ProMax/iOS27.0.1:143 non-StoreKit-integration cases passed;30 overlapping affected cases passed after final list-unlink fix; final app launched. StoreKit integration reproduced acknowledgment timeout and was stopped during stalled cleanup. This is development evidence, not JF/release acceptance. See [native implementation and limitations](JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md) and the linked engineering notes for exact manifest, evidence and open work. No release/hosted policy publication or automatic conflict/retention decision.


## Approved coherent catalogue and Series behavior — 2026-11-12 | aux

OWNER-APPROVED PRODUCT DIRECTION, not a claim of implementation or native acceptance. Bryan supplied his Hellboy/B.P.R.D. folder hierarchy, discussed connected versus offline Series, then approved the full recommendation with “looks good, add all this to the eng docs.” This section refines the earlier conservative Unsorted-only fallback; it does not erase that implementation history or waive the need to validate server metadata.

### Series identity, grouping and offline availability

- Prefer reliable Jellyfin series/run metadata. When absent, use the server's folder relationship as a useful fallback **after verifying that this Jellyfin server exposes usable parent/folder identity**. Scope identity to server/account/library plus stable run or folder identity, not just a display name or the raw NAS path. Cache the chosen group identity, membership and metadata so going offline does not regroup or rename the same books. Unresolved identity remains honest Unsorted rather than a guessed title merge.
- Keep B.P.R.D., B.P.R.D. – Hell on Earth, B.P.R.D. – The Devil You Know, Hellboy and the B.P.R.D., Hellboy, Hellboy in Hell and Young Hellboy as distinct groups in Bryan's example. Naturally order numbered volumes. A folder can mix specials, crossovers, cover books and prose; folder/filename sorting is not an authoritative narrative reading order. Do not fabricate issue numbers or merge separate runs/editions.
- Normal cached library/Series views may show unavailable remote members with a clear needs-connection state. Downloaded shows the **same groups**, narrowed to complete locally readable members. Example: downloading Hell on Earth volumes 1 and 3 produces that same series with those two readable books, not a new offline series. Cache metadata and file availability independently. The previously approved unreachable-launch fallback still selects Downloaded and explains the failure.
- Device and SMB imports retain Leaf's existing local grouping rules. Do not automatically merge local and Jellyfin groups merely because their names match. Preserve a known scoped remote-to-local copy link without duplicating the item. SMB provenance does not imply offline availability: it belongs in Downloaded only when an actual local copy opens without network access.
- EPUB reading is not currently supported by Leaf. A folder containing EPUBs is not evidence that those entries are readable; retain truthful format/availability treatment rather than silently promising EPUB support. No EPUB implementation is approved by this grouping decision.

### Automatic pagination and search

- Replace the required Load more interaction with loading the next batch as the user approaches the end of the grid, with a small loading indicator and an explicit retry after failure. Preserve the existing grid, scroll position and interactions. Avoid duplicate/in-flight page requests, stale-account/query responses, skipped/duplicated items and retry loops; indicate partial counts until indexing is complete.
- Online search must query Jellyfin's indexed catalogue in the selected library/collection scope, not only filter already-loaded grid items. Results can include unloaded items and remain paginated. Keep scope visible; “server-wide” does not mean bypassing the selected library, collection or account permissions.
- A persistent local catalogue should support offline search over the metadata actually indexed. Show partial/stale/offline coverage honestly until an initial index completes; cached search responses alone are not a complete searchable library. Metadata search results are not a promise of offline readability.

### Refresh and persistent catalogue

- Add pull-to-refresh and a quiet foreground/return-to-app check for catalogue changes. Refresh Leaf's view of what Jellyfin has **already indexed**. Files copied onto the NAS may require Jellyfin's own scan first; normal Leaf refresh must not trigger a full server scan. This is not approval for a server mutation or unrelated background polling service.
- No special inventory file is required. Fetch lightweight catalogue metadata through paginated API requests, display first results quickly and complete indexing in the background without blocking reading. Persist the account/library-scoped catalogue; subsequent launches can display cached metadata immediately while refreshing it. Distinguish cached freshness from current server reachability, preserving the approved launch fallback.
- Comic files remain on-demand: catalogue indexing, paging, search and refresh must not bulk-download archives. Reuse current metadata/data foundations where appropriate, but a complete persistent searchable catalogue is distinct from per-query response caches.
- Preserve cached entries, local files, progress and list membership when refresh fails or is interrupted. Do not treat a partial page, failed request, timeout, auth failure or interrupted initial index as deletion evidence. Reconcile catalogue removals only after a successful complete sync of the relevant scope; a remote removal is not permission to delete retained local copies or list history. Keep account/session boundaries and discard stale responses. This catalogue reconciliation does not choose T-02 reading-progress conflict policy or T-03 file-retention/eviction policy.

### Current behavior and required evidence

Read-only Android source spot-check on 2026-11-12 confirms: MainCatalogueGrid still offers Load more; JellyfinCatalogueViewModel auto-fetches remaining metadata pages for Series, while other views use the explicit page callback; JellyfinCatalogueQuery sends SearchTerm/recursive paginated requests to Jellyfin; LibraryRepository persists account/server/query-scoped response snapshots and can restore them on eligible transport/server failures, not authentication failures. Shared JellyfinSeries uses conservative run metadata and does not yet infer groups from folders. These foundations do not constitute the newly approved folder fallback, automatic grid paging, foreground refresh or complete cache-first searchable index. Source inspection is not a new device/server test. The existing native iOS implementation/evidence remains at its recorded scope; no Android behavior or pass is promoted to iOS parity here.

Before acceptance, verify real-server folder ancestry/identity and missing/conflicting metadata; separate same-name runs/local groups; numbered volumes versus specials and unsupported EPUBs; stable group identity across download/offline/reconnect; SMB local-copy availability; multi-page grouping and online search for an unloaded item; near-end paging/retry and stale query/account cancellation; complete/partial offline search; cache-first relaunch; refresh with adds/removals and interruption; server-scan distinction; and preservation of local archives, progress and lists. Record shared fixtures and exact-candidate native Android/iOS results. No JF/release checkbox closes in this documentation update; no code, build, device/server action, commit or publication.


## Native catalogue continuity follow-through — 2026-11-12 | Codex

Implemented verified-folder fallback, progressive cache-first full metadata indexing, scoped paginated online search, pull/foreground refresh, conservative complete-snapshot reconciliation, historical grouping for retained copies, offline collection cache, live Series membership, remote-first mixed-list Continue and progress-preserving remove/redownload. No raw-path grouping, archive prefetch, server scan, automatic progress conflict winner or eviction policy. Concurrent Android/shared folder identity work preserved.

Final combined verification:140 shared tests passed with six Gradle tasks executed;140 native definitions/144 parameterized cases passed on attached iPhone11ProMax/iOS27.0.1 with no runtime warnings; Xcode build/normal launch succeeded. Two StoreKit integrations remain excluded/unresolved. Live-server metadata inspection was blocked by debugger/CoreDevice access; fixture results do not establish owner-server grouping or full acceptance. See [engineering evidence, manifest and precise remaining work](../IOS_ENG_NOTES.md#catalogue-continuity-implementation-and-phone-regression--2026-11-12--codex). All release/JF gate states preserved; no commit or publication.

## Android catalogue continuity and creator detail evidence — 2026-11-12 | Codex

Android now implements the approved persistent cache-first catalogue, validated scoped folder fallback, automatic near-end query paging, unloaded-item server search, offline indexed search, pull/foreground metadata refresh and complete-pass-only metadata reconciliation. Failed/partial passes preserve saved entries and all retained files/list/progress data. Downloaded continues to narrow the same cached groups; a local retained cover is used offline. Stable SeriesId wins; verified folder identity keeps collected volumes together before legacy name/volume fallback. No narrative order is invented for specials, and no EPUB/scan/prefetch/eviction or automatic conflict policy is added.

Also implemented the Jellyfin [cinematic detail/creator contract](../COMIC_DETAIL_CINEMATIC.md): current-cover hero, accessible prominent structured credits and native creator results using library-scoped PersonIds/Person filters rather than title SearchTerm. Cached People/detail metadata supports labelled offline results; auth/access denial does not expose saved remote views. Local/SMB creator relationships are not currently stored on Android; no missing credits are fabricated.

Final shell-432 shared/unit/build/lint gate passed:143 shared passes; Android full/F-Droid each171 total/170 passed/1 skipped, zero failures. Phone/tablet/2×-font synthetic previews inspected and confirmed after bounded layout fixes. Final fullDebug0.3.7/30700 APK **2e996b09e1ef4047dac4758e6da8c220677fd0a113b46a9dfb8bd39462f5a066** installed on S25 Ultra with matching installed checksum; lock screen prevented live acceptance. Actual owner-server folder/credit quality, physical paging/search/refresh/offline/recovery and broad native accessibility/parity remain open. [Android evidence and exact limits](../ANDROID_ENG_NOTES.md#android-catalogue-continuity-and-cinematic-details--2026-11-12--codex), [QA record](../local/ANDROID_JF_CONTINUITY_QA_20261112.json). Prior histories/checklists/T-02/T-03 remain preserved. No release or publication.



## Native cinematic details, creator discovery and owner-server evidence — 2026-11-12 | Codex

Implemented the approved structured Jellyfin cinematic surface and individually tappable credits. Person IDs/roles survive native decoding and path-free, account-scoped detail caches. Creator views use selected-library PersonIds/Person queries with automatic complete pagination, cached/offline coverage labels and stale-session rejection; title SearchTerm is not reused. Preserve primary Read/Continue, actual local progress, list/download/remove/sync/conflict behavior. Local/SMB creator metadata remains unavailable rather than fabricated.

Fresh143 shared tests passed and physical framework rebuilt. Final full iPhone11ProMax/iOS27.0.1 candidate:146definitions/150parameterized cases,149passed/1failed/0skipped; sole failure is known StoreKit finishing, independently reproduced in an SDK-only fixture. All148non-StoreKit-integration cases and the recovery integration passed. Native cinematic phone/tablet-width/accessibility renders confirmed after two bounded rounds;0new es-MX strings,398machine/4review still need fluent review. App rebuilt/launched normally. Temporary diagnostic code removed; no payment workaround or weakened assertion.

Actual owner-device read-only connection traversed682Books/7pages:110verified folders,111groups,1unresolved folder relationship;175credited Books/326person IDs and one successful detail/person sample. No archive/progress/server mutation. A separate10001-book/101-page fixture passed indexing,501groups,failed-server cached reopen and last-item offline search in5.27seconds (single bounded run, not performance-audit acceptance).

[Exact evidence, source manifests and remaining scope](../../iosApp/build/ios-next-20261112/README.md). Physical touch/VoiceOver/iPad, actual interruption/storage pressure/offline/reconnect, live two-client CBZ/PDF sync and release gates remain open. Native tool excludes physical device interaction sessions. No whole-feature/parity/release acceptance, automatic policy decision, commit or publication inferred. Earlier live-metadata-unverified statements are superseded only by the narrow real-server results above.

## Android unlocked S25 continuity follow-up — 2026-11-12 | Codex

Physical baseline verification now covers distinct real Series groups, bounded volume order, detail/Back restoration, automatic search paging beyond30, pull refresh and offline saved search/local page3 resume. This supersedes the earlier lock-screen limitation only for those observations. Testing found/fixed cached non-Comics views, cache-only Retry and covers stuck offline. Final fullDebug APK7131ce31db773eecb85b01ebb616fe434416a7af8da382351c6eb68f028fee9d installed/checksum matched; repeated cold offline fallback and reconnect restored metadata and actual covers while retaining Series. Downward cover peel advanced1/3→2/3. Final shell-504 gate:143 shared passes; each Android flavor171 total/170 passed/1 skipped; assembly/both lints pass.

[Android notes](../ANDROID_ENG_NOTES.md), [artifact-specific QA and remaining limits](../local/ANDROID_JF_DEVICE_QA_20261112.json). Radios/stay-awake restored; retained files/page preserved, no new archive transfer or intentional page advance. Normal resume can queue existing progress intent. Live Android creator navigation, broader accessibility/account/lifecycle/storage/two-client and whole-feature/native parity/release acceptance remain open. Native iOS evidence above remains independent. Historical/checklist states preserved; no publication.


## Approved Library header direction — 2026-11-12

Bryan approved **All · Series · Lists · Offline** at the top of Library with a one-tap **Unread only** toolbar toggle and a removable chip while active. This supersedes the earlier main-header placement of Unread and Downloaded. The Offline tab uses the existing cross-source readable-copy rules and unreachable-launch fallback. The existing ability to narrow an opened Series or List to local copies remains part of the contract. Unread only applies within each view and carries across tabs.

[Android and iOS handoff](../../../leaf-reader-design/docs/LIBRARY-OFFLINE-UNREAD.txt) and [approved Figma section](https://www.figma.com/design/fJAnpiOo1Mzr1rAVk09hRu?node-id=6-1638). Design approval is recorded. Implementation and candidate acceptance remain open.


## Android Offline/Unread header development checkpoint · 2026-11-13 UTC

The approved header is implemented in the isolated Android worktree at `21b3fac` plus uncommitted changes. All / Series / Lists / Offline, global Unread and removable chip are connected to native local/remote projections and detail filtering. Offline uses validated readable copies. List identity/membership/order and Continue availability/order are preserved. Compact S25 checks pass on exact debug `6d61e983…8fc69be` and optimized `ea8de8f0…d70d64` candidates for tabs, Unread, search/members/Back, peeled-cover preservation and scrolled tab/Menu restoration. Both flavor unit suites have 200 passes/one expected SMB-live skip, zero failures/errors, and both lints/assemblies pass.

This contributes bounded Android evidence to JF-A01/A02/A04/A08/A09. Existing parity ledger and whole-feature acceptance states remain open. Native iOS results keep their separately recorded scope. Adaptive/accessibility/localization, unavailable local fixtures, unreachable cold launch/reconnect, broader lifecycle/two-client/upgrade/privacy/licensing/release checks remain pending. [Android exact-source handoff and regression history](../local/android-offline-unread-20261112/README.md), [engineering coordination](../ANDROID_ENG_NOTES.md). Shared board stays In Progress while the Android slice awaits review/integration.


### Android header review correction · 2026-11-13 UTC

The continuing Android review corrected exact-query saved-collection lookup for 30-record pages and independent Unread, with stable pagination scope and honest partial coverage. It also keeps local Series Continue/totals/Add membership available when Unread hides every displayed issue. The new cache regression was reproduced before the fix. Both flavor suites now have 201 passes/one expected skip, zero failures/errors, with both lints and debug/optimized assemblies passing. Optimized `f58e73ba…4b28ccf` is installed/checksum verified and passes the existing compact S25 header/peel/restoration/search journey. [Updated exact-source review and evidence limits](../local/android-offline-unread-20261112/review-followup/README.md). New offline-collection and empty-Unread local Series physical fixtures remain pending. Prior parity ledger/checklist states and native iOS evidence retain their recorded scope. Shared card remains In Progress.


## Owner answers for T-02 and T-03 · 2026-11-13 02:04 UTC

Bryan selected “Ask which position to keep” for Q-019/T-02. When unsent device progress differs from the server position, retain both and ask which to keep. This supersedes historical pending-decision statements, while implementation and two-client/device acceptance remain open.

Bryan selected “Keep until manually removed” for Q-020/T-03. Read/Continue copies stay until manual removal. Explicit Downloads and device/Files/SMB originals remain protected. This authorizes the retention contract and its verification. Historical question/evidence records are preserved in Android engineering notes.

## Android owner-decision implementation checkpoint · 2026-11-13

Q-019/T-02 and Q-020/T-03 are implemented in the isolated Android candidate on reconciled3603b89 plus the frozen source in [final business verification](../local/android-eng-workthrough-20261113/final-business/verification.json). Shared144 tests pass; each Android flavor229total/228passed/one expected opt-in SMB-live skip; both lints0errors/87warnings. This is development evidence, not merged/shipped or whole-parity acceptance.

Three-way sync reuses shared JellyfinSyncPolicy and a durable server/user/URL baseline. Raw missing progress stays unknown. Fresh choices reject changed remote/device/revision/account/consent/page-count state; readback confirms writes before exact-revision acknowledgment. Native device/Jellyfin/defer choices remain outside Reader and retained detail exposes explicit sync/review. Room8→9 stores independent nullable completion; new imports explicitly start incomplete unless the server says read. Optional portable v1 completed matches the existing iOS field. Shared extreme-tick conversion clamps in Long before narrowing, preventing malformed values from wrapping to another page; iOS native execution of this shared arithmetic change was not run by this Android task.

Copies stay until manual removal. Remove download affects only managed Jellyfin archive/cover, keeps canonical history/link/list/pending work and never deletes server content. Re-download validates and rebinds the same row. Disk-reopen, rollback and commit-cancellation regressions pass. Local/SMB originals remain protected. Native synthetic removal verifies file absence with page/read/list/pending retained. A separate permanent library Delete keeps its existing intentional meaning.

[Native fixtures](../local/android-eng-workthrough-20261113/native-fixtures/) cover both conflict callbacks/defer, real large-text scrolling, local Series/list totals under empty Unread, missing-copy Offline, native XML import/grouping/editing and adaptive controls. Logical600/840dp renders are not physical Fold acceptance. Real two-client/server/account/upgrade/transfer/ENOSPC/TalkBack/fluent-localization and final release gates remain at their recorded scope. HTTP reads narrow concurrent-server races; this is not an atomic multi-client server transaction guarantee. Existing parity rows/history remain open unless their complete native acceptance is separately evidenced.
