# Leaf Android Performance, Architecture & Best Practices Audit

## Purpose, timing and execution context

Saved November 12, 2026 from Bryan's supplied Android brief. This is a dedicated, occasional audit playbook, not a recurring engineering-note review requirement. Bryan wants this to be the next audit. **Status: phase1 analysis/baseline and the three prioritized backend fixes completed and regression-verified on12November2026. Controlled device After benchmarking and remaining UI/scale/adaptive work remain open.** Start when Bryan explicitly initiates the audit; do not automatically run it from the hourly notes monitor.

When started, use the existing Leaf checkout and its actual dependencies, source, supported OS versions, design system, approved feature contracts and release constraints. For the brief's initial file request, inspect the available repository files first; ask Bryan only for essential missing inputs. Read the respective engineering notes and preserve all existing work and checklist evidence.

Begin with analysis, an architecture diagram, the self-contained interactive HTML report and a concrete phased plan before rewriting code. Distinguish observed bottlenecks and measured baselines from hypotheses and expected improvements; never present projected "After" results as measured. Check whether `/design-diagram` is available and report its absence honestly rather than claiming it ran. Preserve the requested diagram deliverable even if an equivalent supported rendering workflow is needed.

Treat implementation prescriptions below as audit targets to validate against the actual platform/toolchain and profiling evidence, not automatic library migrations or unsupported API changes. Document justified deviations and compatibility constraints; do not add production analytics, change privacy behavior or publish as a side effect. Saving the original brief alone did not initiate execution. Bryan explicitly initiated the audit on12November2026; the dated execution checkpoint below records its scope and remaining phases.

## Owner-supplied audit brief

The following brief is preserved verbatim from “Act as a Staff” onward. The execution context above adapts its intake step to the already accessible Leaf repository.

Act as a Staff Android Performance & Architecture Engineer. I want you to audit and refactor my existing Android application to align with modern, industry-standard Android architecture while aggressively optimizing runtime performance—specifically around heavy image/data scrolling, network efficiency, adaptive layouts, and critical runtime bottlenecks.

### Core Mandate
1. **Preserve 100% of existing user-facing functionality, business logic, and UI behavior.** Do not drop features or change UX flows unless necessary to fix a performance bottleneck or adaptive layout defect (and flag it explicitly if you do).
2. **Execute in phases.** Start by analyzing my project structure, generating the architecture diagram and interactive HTML performance report, and proposing a concrete refactoring plan before rewriting files.

---

### Required Visual & Interactive Deliverables
Before or alongside refactoring the code, you MUST produce these two deliverables:

1. **Architecture Diagram (`/design-diagram`):**
   - Run `/design-diagram` to generate a clean, high-clarity architectural diagram illustrating the refactored **Unidirectional Data Flow (UDF)** and **Clean Architecture layers** (`UI Layer` -> `Domain Layer` -> `Data Layer` -> `Remote/Local Sources` + `Image/Cache Pipelines`).
   - Clearly label thread/dispatcher boundaries (`Main`, `IO`, `Default`), reactive state streams (`StateFlow`, `PagingData`), and caching tiers (Memory Cache, Disk Cache, Room/DataStore, HTTP Cache).

2. **Interactive HTML Performance & Quality Report (HTML Artifact):**
   - Generate a self-contained, single-file **HTML artifact** (with clean CSS and interactive tabs/filters) mapping out the **Critical Performance Hotspots** and **Adaptive Quality Gaps** across the app.
   - For each critical area (`Scrolling & UI Rendering`, `Image Pipeline`, `Network & Serialization`, `Memory & GC`, `Storage & DB`, `Cold Start & Build`), provide a side-by-side **Before vs. After** comparison showing:
     - **Bottleneck & Root Cause** (what blocks the main thread, bloats the heap, or stalls the network stack today)
     - **Refactored Implementation** (exact architectural/code fix)
     - **Code Diff / Pattern Comparison** (Before anti-pattern vs. After optimized pattern)
     - **Expected Frame-Time / Memory / Latency Impact** (e.g., preventing >16.6ms / >8.3ms frame drops at 60/120Hz, reducing bitmap heap allocations, cutting redundant payload bytes)
   - **Adaptive & Compose Quality Matrix:** Include a dedicated section showing how key screens behave across **Compact** (Phone), **Medium** (Foldable / Portrait Tablet), and **Expanded** (Landscape Tablet / Desktop) viewports—highlighting fixed-width or phone-locked anti-patterns vs. adaptive scaffold upgrades.

---

### Pillar 1: Clean, Industry-Standard Architecture
Refactor the codebase toward Google's recommended Modern Android Development (MAD) architecture:
- **Unidirectional Data Flow (UDF) & MVVM/MVI:**
  - Separate UI strictly from business logic. Composables/Views must only observe immutable `StateFlow` UI states and emit user events/intents up to the `ViewModel`.
  - Eliminate business logic, direct network/DB calls, and mutable state leaks inside UI components.
- **Clean Layering & Dependency Injection:**
  - Enforce clear boundaries between **UI Layer** -> **Domain Layer** (UseCases only where complex or reusable business rules exist; avoid boilerplate pass-through UseCases) -> **Data Layer** (Repositories + Remote/Local Data Sources).
  - Use **Hilt** (or Koin if already present) for clean constructor injection and proper scoping (`@Singleton`, `@ViewModelScoped`).
- **Structured Concurrency:**
  - Replace any `GlobalScope`, unmanaged threads, or callback chains with lifecycle-aware `viewModelScope` and Kotlin `Flow`.
  - Ensure all I/O and heavy CPU work (JSON parsing, bitmap processing, sorting/filtering) is explicitly dispatched to `Dispatchers.IO` or `Dispatchers.Default`, never blocking `Dispatchers.Main`.

---

### Pillar 2: High-Performance Scrolling (Large Datasets & Heavy Images)
Audit all scrollable lists/grids (`LazyColumn`, `LazyVerticalGrid`, `LazyHorizontalGrid`, or `RecyclerView`) and apply these optimizations:
- **Pagination & Memory Footprint:**
  - Implement **Paging 3** (with `RemoteMediator` where offline caching applies) so large datasets stream in bounded chunks rather than loading into memory all at once.
- **Compose Recomposition & Layout Stability:**
  - Provide stable, unique `key` and `contentType` parameters on all `items()` blocks.
  - Ensure all UI state models passed into list items are immutable (`@Immutable` / `@Stable`, `kotlinx.collections.immutable` where needed) so unchanged items skip recomposition.
  - Defer state reads (use lambda-based modifiers like `Modifier.graphicsLayer { }` or `Modifier.offset { }` for scroll-driven animations) to avoid triggering recomposition on every scroll frame.
  - Eliminate nested scrollable containers in the same direction and heavy intrinsic measurements inside list items.
- **Image Loading Pipeline (Coil 3 / Glide):**
  - Downsample images strictly to the target container's exact pixel dimensions (`SizeResolver` / `precision(Precision.EXACT)`)—never decode full-resolution remote bitmaps into memory for thumbnails or feed cards.
  - Configure aggressive, bounded disk and memory caches (`ImageLoader` singleton), enable crossfade only where non-janky, and pre-fetch/cancel requests cleanly on fast scroll.
  - Enforce hardware bitmaps (`allowHardware(true)`) unless software bitmaps are strictly required.

---

### Pillar 3: Network Layer & Data Efficiency
Audit all API clients, repositories, and serialization logic:
- **OkHttp & Retrofit / Ktor Optimization:**
  - Ensure a single shared `OkHttpClient` instance with connection pooling, HTTP/2 multiplexing, and sensible connect/read/write timeouts.
  - Configure HTTP response caching (`Cache` interceptor) and respect `ETag` / `Cache-Control` headers to eliminate redundant network fetches.
  - Enable GZIP/Brotli compression and ensure `HttpLoggingInterceptor` is set to `NONE` in release builds (logging full payloads in memory destroys scroll and network performance).
- **Request Deduplication, Debouncing & Cancellation:**
  - Cancel in-flight requests automatically when a screen is navigated away from or a query changes (`flatMapLatest` for search/filtering).
  - Deduplicate identical concurrent network calls (e.g., multiple UI components requesting the same metadata simultaneously).
- **Serialization & Payload Efficiency:**
  - Use `kotlinx.serialization` (preferred for zero-reflection overhead) or Moshi codegen instead of reflection-heavy Gson.
  - Stream JSON parsing directly from `ResponseBody` rather than converting large payloads to intermediate `String` objects in memory.

---

### Pillar 4: Memory, Storage, Startup & Observability
Evaluate the codebase and apply targeted improvements across these high-impact areas:
- **Memory Management, Leak Prevention & GC Pressure:**
  - Eliminate object allocations inside hot scroll/draw paths (`onDraw`, `LazyList` item composition, `Modifier` chains) to prevent Garbage Collection (GC) pauses that cause visible scroll micro-stutters.
  - Audit for `Context` / `Activity` / `View` leaks in singletons, listeners, and coroutine scopes; enforce lifecycle-safe collection (`collectAsStateWithLifecycle` / `repeatOnLifecycle`).
- **Local Persistence & Single Source of Truth (Room / DataStore):**
  - Replace `SharedPreferences` (which blocks the main thread on `.commit()` and initial load) with asynchronous **Jetpack DataStore**.
  - If using **Room** / SQLite, add missing database `@Index` annotations on frequently queried/sorted columns, use `@Transaction` for batch writes, and project lightweight data classes instead of loading heavy unused columns during list queries.
- **App Startup, Baseline Profiles & R8 Build Optimization:**
  - Defer non-critical SDK initializations out of `Application.onCreate()` using **App Startup (`Initializer`)** or lazy background initialization so cold start isn't blocked.
  - Generate/configure **Baseline Profiles** (for critical user journeys like cold startup and feed scrolling) and verify **R8 full mode** optimization + resource shrinking are enabled for release builds.
- **Performance Observability & Guardrails:**
  - Configure **StrictMode** (in debug builds) to catch accidental disk/network I/O on the main thread, **Compose Compiler Metrics/Reports** to verify composable stability/skippability, and **JankStats / Macrobenchmark** hooks for ongoing frame-time tracking.

---

### Pillar 5: Modern Jetpack Compose & Adaptive Layout Best Practices
Ensure the UI layer follows Google's **Adaptive App Quality** guidelines and modern Jetpack Compose design patterns:
- **Adaptive Layouts & Window Size Classes:**
  - Replace hardcoded screen orientation locks, rigid phone-only layouts, and manual `LocalConfiguration.current.screenWidthDp` checks with **`currentWindowAdaptiveInfo()`** and **`WindowSizeClass`** (`COMPACT`, `MEDIUM`, `EXPANDED`).
  - Adopt **Material 3 Adaptive** canonical layouts where appropriate—such as **`ListDetailPaneScaffold`** (for master-detail feeds/items on tablets/foldables) and **`NavigationSuiteScaffold`** (automatically switching between bottom navigation bar on compact phones and navigation rail/drawer on medium/expanded screens).
  - Support foldables and multi-window resizing gracefully (handling posture/hinge occlusion and preserving scroll/selection state across configuration changes and window resizes).
- **Modern Compose APIs & State Hoisting:**
  - Enforce strict **state hoisting** (stateless UI composables receiving state + lambdas; stateful screen-level composables connecting to `ViewModel`).
  - Follow **`Modifier`** conventions: accept `modifier: Modifier = Modifier` as the first optional parameter on every reusable composable, apply it to the root layout node, and avoid recreating heavy modifiers or lambdas inside composition.
  - Eliminate deprecated or anti-pattern APIs (replace raw `LaunchedEffect` flow collection with `collectAsStateWithLifecycle()`, use `derivedStateOf` only when inputs change faster than the derived output, and hoist `LazyListState` / `LazyGridState` cleanly).
- **System Integration & Accessibility:**
  - Enforce **Edge-to-Edge (`enableEdgeToEdge()`)** with proper `WindowInsets` (`safeDrawing`, `statusBars`, `navigationBars`, `ime`) handling so scrollable lists draw cleanly behind system bars without content clipping.
  - Support **Predictive Back** gestures and ensure proper semantics (`contentDescription`, `mergeDescendants`, minimum `48.dp` touch targets) across custom interactive components.

---

### How to Proceed
1. Ask me for my `build.gradle.kts` (app & project level), `libs.versions.toml` (if used), current package tree, and key files (`Application` class, Network module, main `Repository`, `ViewModel`, and primary list/grid `Screen`).
2. Once provided, generate the **`/design-diagram` architecture diagram** and the **HTML Before/After Critical Performance & Adaptive Quality Report** first.
3. Walk through the refactored code module-by-module with clear explanations of *why* each change improves stability, adaptive behavior, maintainability, or 60/120fps frame times.

## Execution checkpoint — 12 November 2026 | Codex

Bryan explicitly initiated the occasional audit while changing UI elements. Phase1 repository analysis and bounded measurement completed; production refactoring is not yet implemented.

Deliverables: [interactive Before/After report](ANDROID_PERFORMANCE_AUDIT_20261112.html), [current/proposed architecture](ANDROID_PERFORMANCE_ARCHITECTURE_20261112.html), [UI integration handoff](ANDROID_PERFORMANCE_UI_HANDOFF.md), [local capture protocol and queries](local/android-performance-20261112/README.md), [source/trace manifest](local/android-performance-20261112/manifest.json). /design-diagram was unavailable; equivalent inline SVG produced. Existing Leaf colors with offline font fallbacks are disclosed; no installed skill profile changed.

Installed fullDebug APK7131ce31…028fee9d independently checked on S25 Ultra. Controlled20s/6swipe All-grid run:625frames, gfxinfo52deadline misses(8.32%). Perfetto expected8.33ms; Main doFrame p95/p99/max5.43/9.70/13.58ms. FrameTimeline separately labels24App Deadline Missed; do not equate its classifications with gfxinfo or dropped-frame count. One process-cold/filesystem-warm launch:388.07ms to first frame, not catalogue-ready. Debug/tracing overhead, one run per journey,2startup systrace parse failures and no per-Kotlin-function/root-cause attribution are preserved limits. No numeric After improvement claimed.

Priorities PERF-01–08 are documented with source evidence, proposed patterns and verification requirements. Next backend slices: scoped byte-budgeted cover/in-flight reuse; shared file-availability validation avoiding archive opens on progress-only emissions; Default-dispatched catalogue/local-list projection with one ID map per revision. UI integration and release-like repeated measurements follow the current UI work. No automatic Hilt/Paging3/Coil/DataStore/HTTP-cache/hardware-bitmap/scaffold migration.

Final report browser checks pass at390/840/1440widths, including tabs/keyboard/filter/empty state/diagram/budget/no-JS/no remote resources/no page overflow and node-label bounds. Standalone diagram self_check.py --offline passes. QA screenshots inspected. No application changes/build/install, reader/progress/archive-transfer, settings/account/radio changes or publication. Trace servers closed; Wi-Fi/data1/1 and stay-awake0 verified unchanged. Keep all existing functional/device/release gates and owner policy decisions open.

## Three-fix implementation checkpoint — 12 November 2026 | Codex

Bryan explicitly requested all three prioritized fixes. PERF-01–03 are now implemented and regression-verified: scoped byte-LRU/shared cover flights, shared fingerprint availability with strict remote-copy Read validation, and Default/mapLatest catalogue/local-list preparation with memoized groups and ID-map backfill. The existing200-entry local-only thumbnail cache, disk/HTTP tiers, target-size integration and remaining PERF-04–08 scope are still follow-up.

Updated [interactive report/phased plan](ANDROID_PERFORMANCE_AUDIT_20261112.html), [baseline/implemented architecture](ANDROID_PERFORMANCE_ARCHITECTURE_20261112.html) and [UI handoff](ANDROID_PERFORMANCE_UI_HANDOFF.md). The only screen integration consumes prepared full/Downloaded groups; no parallel header/layout redesign. The newly approved Offline-tab/Unread-toggle direction is preserved. No automatic library/schema/policy migration.

Final shell-568 passed143shared tests, each Android flavor186total/185passed/1skipped/0failed, fullDebug assembly and both debug lints. Shell-573 browser/offline-SVG/whitespace checks pass. [Exact manifest and fixture/device limits](local/android-performance-20261112/implementation/evidence.json), [implementation record](local/android-performance-20261112/implementation/README.md). APK a9bdb0a5…4b1a69a6 installed/checksum verified on S25; keyguard prevented new-candidate live UI inspection. No controlled device After performance claim. Broad release/JF/accessibility/adaptive/lifecycle gates and owner decisions unchanged; no release/CI/commit/publication or shared/native iOS changes.

## S25 current-candidate verification — 12 November 2026

APK898a8e1b…121303dd now installed/checksum verified; compact All/Series scroll, comic detail/Back, Series members/Back and downward peel pass. Four bounded current-build traces recorded. Warm All gfxinfo22/313deadline misses,p95/p99=14/18ms; warm Series45/307,29/89ms. Series still needs investigation. Revisited Series captures41background decodes; this does not prove cache misses or the rendering root cause. Missing Series process-name metadata and partial FrameTimeline coverage are explicitly retained; no isolated old/new gain or release-performance acceptance. [Full protocol, SQL, raw results and limitations](local/android-performance-20261112/device-followup/README.md).

Next: instrument target-size stack image/cache behavior and attribute animation/rendering stalls on a frozen UI, preserving peel. Approved Offline-tab/header work, async file-size state, local-only cache sizing and adaptive/accessibility gates remain open. Prior unit/build/lint results are historical; no application source changed in this verification pass.

## Cover follow-up implemented — 12 November 2026

Added main-grid size buckets from actual parent constraints, bounded remote/retained thumbnail decoding, local system-trace diagnostics and a non-debuggable R8 performance variant. Cover authentication entry/return now runs on IO without caching credentials or weakening identity checks. Cache budget and peel/reader/header behavior are preserved.

Final faafa4a2…d1849ffd installed/checksum verified on S25; compact covers/scroll/peel/Series members/Back/comic detail checks pass. Final shell-623 passed both Android unit suites, debug/optimized assembly and both lints; Full190total/0failures/1skip. Final trace124auth reads/56decodes off Main; zero queried Main monitor waits. Mixed frame metrics and unchanged56requests/43evictions retain the need for further layout/draw attribution and repeated fixture/cache measurements. [Exact implementation, SQL and caveats](local/android-performance-20261112/cover-followup/README.md). Prior historical baselines and all broader release/JF/adaptive/accessibility gates remain unchanged.


## S25 file-size QA and repeated optimized profiling · 2026-11-12 23:10 UTC

Bryan cleared the device/build window. FullDebug APK 86af3939 was installed in place and checksum verified; same-source fullPerformance assembly then passed and APK 25f876fcfcf601b73cb05aadda099663d1570f917e8de71410f475507a777c3d is now installed/checksum verified on the S25 Ultra. Both use the existing Debug certificate. Source remains 21b3fac plus the uncommitted isolated file-size slice; no further production code change, dependency, cache-budget or gesture change.

Bounded device observations pass: the 167775191-byte retained copy displays 160.0 MB with pg 3/300 retained; remote cards keep their subtitle. Debug and optimized downward peel advance 1/3→2/3 in place and members/Back retain 2/3. Debug upward scroll and remote Details/Back pass. Only one local archive/no existing lists or missing/denied/empty fixtures are present: cross-file rebinding and those device cases remain unavailable. No reader opening, archive transfer, deliberate progress/server write or account/radio/security change. Normal catalogue refresh/indexing occurred during browsing.

Eight non-overlapping optimized system captures are retained: three alternating All/Series scroll pairs and two warm Series-entry captures. Current steady FrameTimeline p95 is All 11.12–11.93 ms / Series 15.58–17.36 ms. RenderThread p95 is All 6.65–7.41 ms / Series 9.41–10.39 ms. Each warm Series scroll still records 41 cover-network counter events / 42 evictions. Auth/decode stay off Main; no Main slice over 20 ms appears in these bounded windows. The old 25.02 ms layout stall was not reproduced. These are different current workloads, not a measured optimization gain; post-run memory snapshots do not prove a leak.

[Complete protocol, results, hashes, exclusions and next experiment](local/android-series-attribution-20261112/README.md); [file-size device evidence](local/android-filesize-qa-20261112/README.md). Failed config access and two conservatively invalidated overlapping attempts are preserved/excluded; valid trace bounds independently confirm no overlap. Device temporary traces were removed after host-copy checksum verification. All runners ended; device left at All/top with Downloaded off on optimized 25f876fc. Existing automated results remain 143 cached shared passes and 191 passed/1 expected skip per Android flavor; tests/lints were not rerun just for profiling.

Next: isolate a reversible stack-rendering/covered-rear-artwork experiment with pixel/full-motion and repeated stage/frame checks before retaining any change; overdraw remains a hypothesis. Census the visible/recent cover working set before changing budgets. Preserve peel, prepared groups, scoped image/auth checks and existing design. Header, local-cache, adaptive/accessibility, JF and release gates stay open. No commit, integration, upload or publication.


## Isolated follow-through checkpoint · 2026-11-13

Android owner-policy/manual retention, Room8→9 completion independence, local cover-byte accounting, adaptive local fixtures and J-02 resolved-destination/redirect safeguards are recorded in the [performance handoff](ANDROID_PERFORMANCE_UI_HANDOFF.md), [compliance record](../RELEASE_COMPLIANCE.md) and live Android engineering notes. Current complete flavor suites243passes/one expected SMB-live skip and lints0errors87warnings; shared144JVM result is carried from unchanged sources. Hermetic/compact/synthetic evidence retains its limits.

Measured rear clipping, layer consolidation and texture preparation did not establish consistent benefit and were reverted. Halftone batching shows static cost reduction, but search/resize gates prevented retaining its first variants. Current closed/settled-only row refinement has automated checks; installed3821df96 native checks are waiting on the interrupted S25 window. [Protocol, negative results and source/artifact identity](local/android-eng-workthrough-20261113/halftone-search-guard/README.md). No universal speedup, cache-churn fix, broad adaptive/accessibility/physical acceptance or release candidate is established. Preserve intentional Series peel, whole-page reading, scoped auth/privacy, prepared groups and existing budgets.


## Resumed rendering acceptance outcome · 2026-11-13

The settled-row/search-guard experiment is rejected and fully restored after repeated17.66ms search outliers; static cost savings do not satisfy the complete interaction gate. [All samples, counter-controls, failed attempts and restoration](local/android-eng-workthrough-20261113/halftone-search-guard/README.md). No renderer optimization or cache-churn resolution is retained. Clean restored source freshly passes243tests/one expected skip per flavor and both lints0errors87warnings; rebuilt4899067f exactly matches installed/native-checked original. Restored downward peel/Back and retained-copy size/page pass on compact S25. Broad physical/adaptive/accessibility/performance/upgrade/privacy/licensing/release acceptance remains open; preserve the current design and intentional peel.


## Bitmap trial disposition and preserved acceptance limits · 2026-11-13

The QA-only bitmap shader improved repeated component drawing, and actual Library scroll medians also improved (All FrameTimeline11.38→8.47ms, Series18.27→14.81; RenderThread7.38→4.87 and10.24→7.57). It did not satisfy the complete interaction gate: warm search10.70/17.76/8.62ms versus current original11.90/10.87/10.52. Preserve the positive scroll evidence and valid outlier; no causal/universal regression is inferred from the small chronological sample. All20 current traces thermal0/no reported loss/overlap; fresh restored and historical five-pose comparisons show only dot AA differences max4RGB. Native search/navigation and filmed intentional peel/Back pass. [Decision, exact inputs/artifacts, excluded error-banner control and full limits](local/android-halftone-tile-library-20261113/README.md).

Three owned trial files restored after205hash checks; original4899067f installed/pulled verified,160MB/page3of300 retained, All/top/searchclosed and task-owned QA/temp cleanup complete. QA-only benchmark retained atd16e296; no production renderer optimization or resolved cache churn. Candidate243passes/one expected skip per flavor, lints0errors88warnings (one KTX style suggestion), optimizedbuild2m34; original production checks explicitly carried after exact restoration, shared144JVM carried unchanged. KEEP BUILDING; broader source/device/upgrade/privacy/licensing/release gates remain open.


## Observed artwork demand and remaining memory-policy gate · 2026-11-13

On exact original4899067f/clean8aee99f, native UI/source census finds6 front covers/18 requested cover roles at top, then9 fronts/24 roles after the established three upward swipes. Existing captured-PID original traces show384px decodes of786,432–918,528bytes (median907,776),41/40/40 cover-network events and41/41/41 evictions, with resident bytes near the configuration-derived16MiB cache target. Applying sampled sizes to24 roles estimates18–21.02MiB before hidden/prefetched work; this is role-based demand, not a complete unique-live bitmap or peak-memory measurement. [Counts, native snapshots, derivation, VM configuration and limits](local/android-series-demand-20261113/README.md).

No code/build/cache-budget/data/settings change. Owner/source hashes match, All/top/searchclosed and task-owned UI dump cleaned. Preserve full rear artwork and intentional peel. Next controlled task separates same-Series re-entry from alternating-tab traversal and measures active/prefetched demand/peak memory before any bounded budget A/B; ordinary misses for new content are not automatically a cache bug. No solved churn, universal improvement or new checklist closure. KEEP BUILDING.


## Same-Series reuse and deadline-aware trace analysis · 2026-11-13

Six unchanged-budget controls on original4899067f separate long same-Series traversal from nearby re-entry. Long windows still record40cover-network/41eviction events and1hit each without All-tab interleaving. Nearby windows each record45hits and no observed cover-network/decode/eviction events. The24-role geometric census is not a live-object/LRU-competition count and does not justify increasing memory by itself. Sampled RSS and lifetime high-water/gauge limits remain explicit in the [full report](local/android-series-reentry-20261113/README.md). No app/budget change.

Added [reusable frame SQL](../scripts/profile-frame-metrics.sql): captured-PID canonical frame/token/deadline matching, coverage, overrun with RenderThread-end correction, UI-to-RT elapsed completion, raw/canonical queue/jank flags and integrity. Validated26retained traces with100% matching/coverage, known-window checks and wrong-PID zero-frame check. Earlier actual-duration p95 numbers remain valid durations, not direct dropped-frame counts. The tile's problematic warm window still has deadline-overrun p9510.079ms versus original warm3.234–4.289, with127canonical BufferStuffing/5DroppedFrame/3AppDeadlineMissed records. Queue-latency concern and conservative rejection stand; no causal/universal conclusion or renderer reinstatement.

Original source/APK and160MB/page3of300 verified; finalAll/top/searchclosed and owned UI cleanup complete. No new app build/test/install; existing243pass/one expected skip per flavor and shared144JVM evidence explicitly carried. Next investigate production versus queue/IME/compositor pacing with matched deadline/presentation/input-response evidence; broader physical/integration/upgrade/privacy/licensing/release gates remain open. KEEP BUILDING.


## Frame-pacing wait attribution · 2026-11-13

Added [wait-attribution SQL](../scripts/profile-frame-waits.sql), validated against all 26 retained traces plus wrong-PID and full-concatenation checks. It separates clipped UI/RT scheduler states and outer queueBuffer spans, avoiding nested-scope double-counting. Each trace selects 20 timing-eligible tail frames with 100% scheduler coverage and nonoverlapping queue spans. [Full protocol/results/limits](local/android-frame-pacing-20261113/README.md).

In the rejected tile's problematic search window, RT elapsed across 20 selected frames is 157.769 ms: 39.756 ms Running, 117.295 ms sleeping and 0.717 ms runnable. All sleep lies within 123.963 ms outer queueBuffer spans and ends with kgsl-events wakeups. Inclusive Drawing elapsed is not all active paint CPU. This supports graphics-fence-related producer waiting, not exact GPU-job/driver/compositor/IME causality or direct input latency. Read-only device descriptors expose GPU-memory but no GPU render-stage/counter producer. No settings/backend/clock/cache change or new build/install. Prior renderer rejection and positive/mixed evidence remain intact. Original 205 app/build inputs unchanged; tests/lints/shared explicitly carried, not rerun.

Next bounded task uses repeated unchanged-runtime input/presentation/search-IME controls before another supported minimal app experiment. Preserve forest/gold/Anton/Archivo, whole-page reading, full rear art/intentional peel, privacy/auth and exact baseline guards. Broad device/integration/upgrade/privacy/licensing/release gates remain open; KEEP BUILDING, no checklist closure or production performance claim.


## Unchanged-runtime search/IME controls and coverage · 2026-11-13

Seven serialized S25 captures (one open/close pilot, three warmed and three fresh-process full flows) pass exact source/APK/PID/geometry/hash, thermal0, nonoverlap, no reported trace loss and final All/top/searchclosed checks. All 168 injected owner touch/key records have complete, consistent dispatch/handling/ACK timing. Full-flow ACK p95 is 7.411–9.126 ms; it is not display-response latency. Input-reader/frame/presentation coverage is 0/168. Production user/ro.debuggable0 device descriptors do not expose the separate input-event source; no privacy/settings workaround or nearest-frame latency substitute. Synthetic injected typing does not cover real IME composition or hardware touch-to-photon behavior. [Protocol, all samples/limits and exact evidence](local/android-input-presentation-20261113/README.md).

Added [input SQL](../scripts/profile-input-metrics.sql) with captured PID, event uniqueness, stage consistency/coverage and explicit presentation limits. [Wait SQL](../scripts/profile-frame-waits.sql) now reports covered/uncovered scheduler time: cold1 UI 98.144712% (2.474375 ms unknown), cold2 UI 99.436113% (0.783386 ms unknown), other new roles 100%. Incomplete thread-state durations remain unknown even when trace-loss counters are empty. All 26 earlier wait traces revalidated unchanged; all 33 pass queue/nonoverlap/bounds checks, plus wrong-PID tests. Original historical outputs/hashes preserved.

Original 4899067f and all 205 app/build inputs unchanged; no app build/install/cache/render/gesture/account/settings change. Existing Android 243 passes/one expected skip per flavor/shared 144/lints explicitly carried. No production speedup, solved churn, direct response-latency acceptance or reinstated renderer. Next attribute retained cold UI deadline outliers (maximum 28.038 ms overrun) before another supported minimal app experiment, keeping scheduler gaps explicit. Whole-page/full-art/peel and current design preserved. Broader integration/device/upgrade/privacy/licensing/release gates remain open; KEEP BUILDING.


## Search-clear delay isolated; native acceptance pending · 2026-11-13

The six retained cold/warm worst grid-return frames are predominantly active UI composition/measurement work, with identical 13-composition/29-text-layout structure. A distinct source-level delay also holds a cleared query for 300 ms before restoring its cached unfiltered view. One condition now limits that debounce to nonblank typing. No renderer, cache budget, identity, request-cancellation or saved-position policy changes. [Exact attribution, owned source delta and current verification limits](local/android-cold-ui-attribution-20261113/README.md).

Both flavor XML reports show 243 passes/one expected skip; lint/optimized artifact verification is ongoing. Original owner4899067f remains installed. No new native acceptance, direct display-response metric, solved layout cost/cache churn or release acceptance is inferred. Candidate/restored and search X/Back/reversal/motion/peel checks await a coordinated native window after an iOS QA run resumed during Android automated checks. Existing device/upgrade/privacy/licensing/release checklist history and limits remain open; KEEP BUILDING.

Verification follow-through: optimized candidate d24091f5 passes both flavor suites (243 passes/one expected SMB-live skip each), both lints (zero errors/87 warnings), and optimized assembly; shared144 reports match unchanged inputs with the task up-to-date. Existing Debug certificate/nondebuggable/profileable metadata verified. No candidate install or new S25 acceptance; original4899067f still hash verified. Native comparison and X/Back/reversal/motion/peel gates remain pending coordinated availability. The iOS session resumed after Android checks began in a process gap; that overlap is disclosed and no new performance capture was made. KEEP BUILDING.


## Search-clear native follow-through and readiness preparation · 2026-11-13

Candidate d24091f5 and original4899067f each complete three guarded fresh-process S25 controls, thermal0/full available ACK/canonical-frame coverage; overall p95 findings are mixed. Exploratory populated-layout phase is20–25ms versus323–330ms after controlled Back-UP. It supports removal of the coded300msblank-query debounce, not firstpixel/direct presentation latency, faster layout or universal scrolling improvement. Failed early >=8text-layout proxy and historical/matched queries are preserved. Candidate native X/Back/rapidreversal and peel1→2/MenuBack/membersBack retain2 pass with finite recordings/sample visual review. Exact source restored; no cache/renderer/account/settings change. [Evidence and limits](local/android-cold-ui-attribution-20261113/README.md).

The prior205-input manifest omitted12tracked build/config inputs;217current inputs now reconciled, extra12post-build only. Corrected64-file integration proposal includes native-symbol configuration and leaves live dirty checkout/iOS/CI/store untouched. Offline privacy/license/ELF preflight and unpublishedpolicy draft complete their bounded review scope. Refreshed Play inventory shows Alpha30700available, internal30600active; productioninactive with2optedin versus12for14continuousdays. Drafts/uploadcert/Publishingoverview remain unchecked. OnlyS25available; named KingSpawnVol02main-account real sync checks are owner-authorized and separate. [Remaining work and exact dependencies](ANDROID_REMAINING_WORK.md). Broad device/upgrade/privacy/licensing/transfer/integration/Play gates remain open; KEEP BUILDING, no checklist closure or publication.


## Live two-client follow-through and mounted search focus · 2026-11-13

Owner-authorized main-account KingSpawnVol02 browser/S25 checks expose an actual optimizedd240 crash on Reader→detail return with restored open search. Frozen R8 mapping identifies LibraryScreen327/requestFocus before the detail early-return, with no mounted field. Minimal fix moves autofocus into actual search AnimatedVisibility content. Optimizedb5f81126 passes the exact old crash path, search X/Back/reversal, durable pending crash/update recovery, defer/relaunch/review, both conflict choices, stale-choice rejection and independent browser device-choice readback. Final visible1/145/unplayed survives cold explicit sync; named367.5MB and original160MB/page3of300 copies retained. [Evidence and scope](local/android-real-sync-20261113/browser-resume/README.md).

Fresh Android243pass/one expected SMB-live skip per flavor, lints0errors/87warnings, optimizedbuild2m45; shared144 retained reports verified/up-to-date. Full217input pre-build freeze. No new performance traces or broad speedup claim for b5: d240's previous phase controls stay artifact-specific. Cached remote captions may lag explicit sync until metadata reload; local page/action/Unread and fresh conflict choices pass, caption freshness remains follow-up. Real network-loss/account-switch/storage interruption/minOS/Fold/accessibility/localization/upgrade/parity/release gates remain open. KEEP BUILDING; no whole checklist closure or publication.


## Caption freshness and real outage retry — 2026-11-13

Optimized2b59a888/runtimef3e5bb5 (217 prebuild-frozen inputs) fixes loaded detail/catalogue progress captions using authoritative server observations, with account/URL/generation and in-flight-response guards. Both flavors246pass/one expected SMB-live skip, lints0errors/87warnings; shared144 reports verified/up-to-date. Initial5124 native read/played/reset row checks and final2b59 scoped positive native check are separately retained. [Implementation and limits](local/android-sync-captions-20261113/IMPLEMENTATION.md).

Owner disconnected/reconnected S25. Actual no-default-network phase preserved local7 across cold Reader restart while independent server remained6; explicit retry after reconnection uploaded7 with fresh browser Resume7/unplayed. Restored fresh baseline6/unplayed in both clients; cold local6 and explicit sync6/no conflict pass. Other160MB/page3of300 copy unchanged; final All/top/searchclosed and Wi-Fi/data flags1/1 restored by owner. No agent radio/account change. One stale-snapshot sync was stopped by automatic review; fresh UI plus source acknowledgment/outbox checks supported approved guarded restoration. [Final assertions](local/android-sync-captions-20261113/validation-final.json).

Functional acceptance only: no new performance capture/universal speedup/cache-churn claim. Account switch, storage interruption, other physical devices, accessibility/localization, Play-delivered upgrade, combined-source integration, privacy/licensing/artwork/signing/Play gates remain open. KEEP BUILDING; no broad checklist closure or publication.
