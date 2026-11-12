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
