# Android performance handoff for the UI revision

12 November 2026 · Companion to the [audit report](ANDROID_PERFORMANCE_AUDIT_20261112.html) and [architecture](ANDROID_PERFORMANCE_ARCHITECTURE_20261112.html).

Bryan is changing UI elements concurrently. The initial audit was read-only; the three backend fixes are now implemented. MainCatalogueGrid consumes prepared groups and LibraryScreen selects prepared Downloaded groups; no header/layout/visual redesign was applied. These are engineering constraints and integration points, not an approved new visual design.

The newly approved header direction is All / Series / Lists / Offline, with a one-tap Unread only toolbar toggle and removable chip ([shared contract](features/JELLYFIN_FIRST_LIBRARY.md#approved-library-header-direction--2026-11-12)). Preserve that product decision during UI integration; the existing internal filter enum is not a requirement for an Unread tab. The three performance fixes do not implement or replace the header design.

## Highest-value UI boundaries

1. **Prepared rows, no disk work in cards.** Current ComicCard calls File.length during first composition (LibraryScreen.kt around1973). Supply file-size text from asynchronous row state. Keep archive validation and availability decisions below UI.
2. **Keep prepared groups.** CatalogueProjector now prepares Series/Downloaded groups on Default and reuses them through scalar loading/freshness changes. MainCatalogueGrid consumes state.seriesGroups. Preserve this boundary; do not bring grouping/sorting back into composition. Local lists/filter/sort use LibraryProjection on Default.
3. **Stable identity and compatible reuse.** Existing keys are present. Preserve account/library/item keys, and add content types for cover/stack/list/status rows when integrating. Preserve scroll, selection, query, source and peeled-cover state through Back and resize.
4. **Preserve shared cover reuse.** The existing suspending loader now uses a scoped byte LRU/shared-flight cache beneath the repository API. Retained-remote art is preferred; failures retry and scope/content invalidation rejects late results. Keep offline→online producer keys. Target pixel dimensions/size buckets and the separate local-only cache are still UI/pipeline follow-up; disk/HTTP caching remains deferred.
5. **Animation reads at the leaf.** Keep per-frame transforms in graphicsLayer/offset lambdas. Header/search changes should not re-run whole-library processing. Do not mark large collection graphs @Immutable simply to chase recomposition counters.
6. **Window constraints and accessibility.** Density must fit actual available width after insets/padding/gaps. Preserve the user's preference when it fits. Keep48dp hit targets, font/localized expansion, IME/system insets and keyboard focus. Material adaptive scaffolds do not authorize new routes or rail/tab replacement.

## Coordination boundaries

UI-owned integration files: LibraryScreen.kt, MainCatalogueGrid.kt, CreatorScreen.kt, JellyfinDetailScreen.kt, SeriesDetailScreen.kt, ReadingListDetailScreen.kt and AppNavHost.kt. Re-read latest buffers before edits; no mechanical patch from this audit should overwrite the ongoing redesign.

Implemented backend work: CoverBitmapCache shares scoped requests with byte eviction and reference-counted cancellation; ComicFileAvailability reuses IO validation for unchanged file fingerprints while strict remote-copy Read forces validation; CatalogueProjector/LibraryProjection prepare rows on Default with mapLatest and generation rejection. Existing loader/readable/downloaded flow APIs remain. Shared observation uses its own process-lifetime scope, separate from finite sync jobs. Preserve the narrow grid integration and account/session, partial-index, source, reader/progress and backup invariants. See [implementation evidence](local/android-performance-20261112/implementation/README.md).

## How to compare the new UI

Baseline: installed fullDebug APK7131ce31db773eecb85b01ebb616fe434416a7af8da382351c6eb68f028fee9d on S25 Ultra. One20s six-swipe All-grid capture:625frames, gfxinfo52deadline misses(8.32%); UI doFrame p95/p99=5.43/9.70ms at captured8.33ms deadlines. These are debug/trace measurements, not release targets.

The current checkout may already contain newer UI changes. Do not compare a new layout to this baseline and attribute the entire delta to one backend optimization. Freeze one layout/data/cache/network setup, run repeated release-like measurements, then change one slice. Record first frame separately from catalogue-ready and image-ready latency.

Deterministic work-count fixtures now cover single-flight reuse, validation reuse,10k grouping and stale/cancelled work. Controlled device After frame/heap/network measurements remain pending, alongside medium/expanded/fold/multiwindow, TalkBack/keyboard, fluent es-MX and large retained-library IO. No audit result closes those gates.

## Current-candidate S25 verification · 12 November 2026

The898a8e1b scrolling APK is now installed/checksum verified. Compact All detail/Back, Series members/Back, upward scrolling and downward peel pass; A.D. After Death advances1/3→2/3 in place and keeps2/3 on Back. Series still has frame delays: warm gfxinfo45/307 deadline misses,p95/p99=29/89ms versus warm All22/313,14/18ms. Both Series traces capture41background decodes; this is not a request/cache-miss count. No isolated old/new gain or release benchmark is established. See [device protocol, SQL and limits](local/android-performance-20261112/device-followup/README.md).

Next: instrument target-size stack covers, decoded dimensions, cache outcomes and animation/rendering work on the frozen UI. Preserve scoped image keys/retries, prepared groups, downward peel and its selection. Remote content types and per-frame graphics-layer reads are already implemented; async file sizes, target-size integration, local-only byte cache and adaptive/accessibility work remain. The installed header still uses the older Unread tab/Downloaded control; approved Offline-tab/Unread-toggle integration remains open.

## Implemented cover follow-up · 12 November 2026

Main Library now derives remote cover width once from parent constraints/density/padding/gaps/columns, rounded up to64px buckets; all stack layers use the same bucket and image producers restart when that bucket changes. Preserve the one screen-level BoxWithConstraints boundary; do not move subcomposition into lazy cards. Creator/legacy/detail callers retain400px defaults. CoverImageDecoder bounds remote and retained-remote thumbnails with a sample pass and final scaling; local-only covers and reader pages are unchanged.

Cover authentication entry/return now runs on IO, preserving identity checks. Local system-trace counters contain no identifiers/URLs/payloads and collect only during enabled tracing. Current cache ceiling is unchanged. Final optimized S25 trace confirms124auth slices and56decodes off Main. Both90s passes still make56requests/43evictions, so cache churn remains. Gfxinfo improves in this sample while FrameTimeline p95/p99 slightly worsens; do not call this a universal frame-time win. [Exact evidence and implementation](local/android-performance-20261112/cover-followup/README.md).

Final compact art/scroll/peel/members/Back/detail checks pass; body/layout/gesture/timing/header design is preserved. Next UI priority is attribution of remaining measure/layout/draw stalls on a frozen design, repeated fixture benchmarking and warm cache re-entry. Async file-size state, local-only byte cache and the broader adaptive/accessibility matrix remain open; approved Offline-tab/Unread-toggle work remains pending.


## Isolated file-size follow-up · 2026-11-12 21:32 UTC

Bryan assigned the bounded file-size task on `21b3fac017885732c3f6b5dc6a7980790284ad66` in `codex/android-agent-bootstrap`. `ComicCard` now consumes path-keyed asynchronous size state; `File.length()` runs on IO. A changed path resets the state and cancels its producer; coroutine cancellation prevents a blocked old lookup from publishing after return. Missing/inaccessible files retain the empty-label fallback. Existing card text formatting, layout and Series peel code are unchanged. This is an uncommitted isolated change, not yet integrated into the live checkout.

Verification: shared JVM task passed with 143 cached passes; both Android debug unit suites executed with 192 total / 191 passed / 1 expected opt-in SMB-live skip / zero failures or errors per flavor. The two new tests cover a blocked lookup leaving its caller responsive, cancelled-result rejection, actual file bytes, missing/denied files and no file lookup for remote cards. `assembleFullDebug`, both debug lints (zero errors, 88 warnings each) and `git diff --check` passed. Debug APK SHA-256 `86af3939401215116700cd93b66134c920ce07d930decafa33ebfe90b5c6a95c` was built, not installed. Saved XML and lint reports are under this isolated checkout's `app/build/`.

Device rendering, peel/Back checks, actual Compose path-rebinding behavior and controlled frame-time measurements remain pending. Coroutine tests are not device/UI acceptance or proof of a general scrolling improvement. The earlier async-size follow-up statements remain historical; the remaining local-cache, header, adaptive/accessibility, JF and release gates stay open. No install, device/server action, commit, merge, push or release.


### Resumed file-size review · 2026-11-12 22:14 UTC

Local source review found no blocking issue in this slice. Source hashes, retained XML totals and fullDebug APK hash still match the preceding report. APK v2 signature and package/version/debuggable identity were rechecked; no build or tests rerun. [Exact-candidate device handoff and evidence](local/android-filesize-qa-20261112/README.md) are prepared locally. Device/Compose rebinding, rendering and peel/Back checks remain pending Bryan’s coordinated install window. Board checkpoints now succeed on the assigned Series-jank card; its broader In Progress state is retained. Agent is waiting for the device window; no device/install, commit, integration or release action.


## S25 file-size QA and repeated optimized profiling · 2026-11-12 23:10 UTC

Bryan cleared the device/build window. FullDebug APK 86af3939 was installed in place and checksum verified; same-source fullPerformance assembly then passed and APK 25f876fcfcf601b73cb05aadda099663d1570f917e8de71410f475507a777c3d is now installed/checksum verified on the S25 Ultra. Both use the existing Debug certificate. Source remains 21b3fac plus the uncommitted isolated file-size slice; no further production code change, dependency, cache-budget or gesture change.

Bounded device observations pass: the 167775191-byte retained copy displays 160.0 MB with pg 3/300 retained; remote cards keep their subtitle. Debug and optimized downward peel advance 1/3→2/3 in place and members/Back retain 2/3. Debug upward scroll and remote Details/Back pass. Only one local archive/no existing lists or missing/denied/empty fixtures are present: cross-file rebinding and those device cases remain unavailable. No reader opening, archive transfer, deliberate progress/server write or account/radio/security change. Normal catalogue refresh/indexing occurred during browsing.

Eight non-overlapping optimized system captures are retained: three alternating All/Series scroll pairs and two warm Series-entry captures. Current steady FrameTimeline p95 is All 11.12–11.93 ms / Series 15.58–17.36 ms. RenderThread p95 is All 6.65–7.41 ms / Series 9.41–10.39 ms. Each warm Series scroll still records 41 cover-network counter events / 42 evictions. Auth/decode stay off Main; no Main slice over 20 ms appears in these bounded windows. The old 25.02 ms layout stall was not reproduced. These are different current workloads, not a measured optimization gain; post-run memory snapshots do not prove a leak.

[Complete protocol, results, hashes, exclusions and next experiment](local/android-series-attribution-20261112/README.md); [file-size device evidence](local/android-filesize-qa-20261112/README.md). Failed config access and two conservatively invalidated overlapping attempts are preserved/excluded; valid trace bounds independently confirm no overlap. Device temporary traces were removed after host-copy checksum verification. All runners ended; device left at All/top with Downloaded off on optimized 25f876fc. Existing automated results remain 143 cached shared passes and 191 passed/1 expected skip per Android flavor; tests/lints were not rerun just for profiling.

Next: isolate a reversible stack-rendering/covered-rear-artwork experiment with pixel/full-motion and repeated stage/frame checks before retaining any change; overdraw remains a hypothesis. Census the visible/recent cover working set before changing budgets. Preserve peel, prepared groups, scoped image/auth checks and existing design. Header, local-cache, adaptive/accessibility, JF and release gates stay open. No commit, integration, upload or publication.


## Android approved Offline/Unread header implementation · 2026-11-13 UTC

Bryan assigned the next Android work and cleared the idle build/device window. Implemented All / Series / Lists / Offline tabs with an independent global Unread-only toggle, trailing removable chip and Menu grid/default-direction controls in the isolated `codex/android-agent-bootstrap` worktree at base `21b3fac017885732c3f6b5dc6a7980790284ad66`. The existing asynchronous file-size slice remains included. Offline consumes validated readable local copies, unknown remote progress remains unknown, and list/series identities, order and Continue scope are preserved. Local Edit sits above the local grid to keep the toolbar slot fixed. Large type uses the Leaf mark alone to preserve action space and awaits adaptive device review.

Final each-flavor unit results are 201 total / 200 passed / one expected SMB-live skip / zero failures or errors. Both debug lints pass with zero errors and 88 warnings each. FullDebug and same-source FullPerformance assemblies pass. Shared task earlier passed with 143 cached passes. The exact 17-file app manifest matches source after building. Final debug APK `6d61e983c834194f07525b371fceda0b2f8e4bc80a9504f8749d22a4f8fc69be` and optimized APK `ea8de8f02809cab46ddc9abca87666776a1a484b346226037dab57d824d70d64` were installed in place and checksum verified on S25 Ultra / Android 17. Existing Debug certificate, package and 0.3.7/30700 remain.

Compact physical checks pass for four tabs, persistent/clearable Unread, actual retained-copy metadata, search/member Back preservation and intentional downward peel. Testing found and fixed Menu/Back losing peeled-cover selection and tab return losing scrolled position. Final debug and optimized checks retain 2 / 3 across Menu/Back and exact first-cover bounds across scrolled Menu/Back and Series → Offline → Series. Context-tagged prepared rows avoid restoring a grid against stale rows, cached query pages survive re-entry, and cover selections retain scoped IDs. Intermediate failures and final evidence remain in the [consolidated QA record](local/android-offline-unread-20261112/README.md) and [source/artifact manifest](local/android-offline-unread-20261112/evidence.json).

Android slice is ready for exact-source review with bounded compact device evidence. Shared card remains In Progress. Await expanded/fold/large-type/TalkBack/keyboard/dark-mode/fluent es-MX, local-list/local-Series/failure fixtures, unreachable cold launch/reconnect, broader Jellyfin and final-candidate upgrade/privacy/licensing/release gates. Existing Series rendering/cache-churn findings remain open. This changed header requires a new frozen layout baseline before any future rendering experiment. Recommendation remains KEEP BUILDING. All release checklist history and owner questions remain preserved. Source is uncommitted and isolated.


## Android header source-review follow-up · 2026-11-13 UTC

Continuing Bryan’s assignment found two more Unread integration gaps. Saved collection traversal used the default 100-record cache query against online pages stored at limit 30 and missed entries cached under the UNREAD query. It now uses matching page size, prefers full saved membership, falls back to saved Unread membership when eligible, and stays within that query while paging. A targeted regression failed with expected first/second entries versus an empty result before the fix. It now covers both filtered-only and incomplete full caches, prevents pagination-scope mixing, and verifies local search/read/unknown handling and clearing Unread.

Local Series Continue/restart, totals and Add Comics membership now use the full scoped group even when Unread leaves zero displayed covers. The filtered grid shows the existing localized empty-result message. Physical all-started/all-completed local Series cases await an existing fixture.

Final each-flavor results are 202 total / 201 passed / one expected SMB-live skip / zero failures/errors. Both debug lints have zero errors/88 warnings, both assemblies and diff check pass. Source remains codex/android-agent-bootstrap at 21b3fac plus isolated uncommitted changes. FullDebug SHA-256 `f21ef3b561ec3cf086a6465fcaad1c8d81969e562fb9eac8e43eecef0fe30e1b`. Optimized SHA-256 `f58e73bad6b1ffccf1043014eb11282c47daa2bc2ce6d40202257bcb54b28ccf` installed/checksum verified, existing Debug certificate/0.3.7/30700.

Updated optimized S25 checks pass for peel/cover Menu return, exact scrolled Menu/tab return, all four tabs, Unread/chip, retained-copy metadata and search/member Back. [New source manifest, saved failure/pass reports and device assertions](local/android-offline-unread-20261112/review-followup/README.md). Prior candidates retain their historical evidence. Exact-source integration, unavailable physical fixtures, adaptive/accessibility/localization and broader Jellyfin/upgrade/privacy/licensing/release gates remain open. KEEP BUILDING. The broader shared card stays In Progress and Series performance/cache churn remains unresolved.
