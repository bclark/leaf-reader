# Android development changeset · 12 November 2026

This changeset records the Android work accumulated since `da14e0f`. It is a development checkpoint, with no release publication or acceptance gate implied.

## Included

- Jellyfin comics in the main library, with account-scoped catalogue indexing, offline metadata, Series grouping, list references, details and creator lookup.
- Database migrations and durable backup/preference recovery, including a separate recovery-QA build.
- Shared Jellyfin policy/models/tests and ComicInfo metadata parsing used by Android.
- Shared cover-flight/byte caching, retained-file availability reuse, and background library/catalogue projection.
- Constraint-sized main-grid covers, bounded remote/retained thumbnail decoding, IO cover authentication, local trace diagnostics and an optimized local profiling variant.
- Android performance reports, architecture, UI handoff, recovery instructions, Jellyfin specification/research handoff, shared privacy wording and accumulated release evidence.

Existing native iOS, design, release-helper/skill and store-artwork changes are outside this checkpoint. Generated CocoaPods files are excluded. Shared privacy/release documents preserve the existing cross-platform entries. Local engineering notes, raw traces, APKs and private review assets stay uncommitted.

## Verification

The implementation pass completed shared JVM tests and the final Full/F-Droid Android unit tasks, fullDebug/fullPerformance assembly and both debug lints. The Full report contains190 tests, zero failures and one skip. Core reader UI source checksums still match the verified candidate when preparing this commit. Documentation-only commit preparation did not rerun those unchanged application checks.

Optimized APK `faafa4a27bba87ed466bca9b3477a032d6c9cac6d679aa7f34d1268dd1849ffd` was installed and checksum verified on the Samsung S25 Ultra. Compact All/Series scrolling, comic details/Back, Series members/Back and downward peel selection passed. Report interaction/offline/responsive checks and whitespace checks passed.

The latest trace records124 cover-authentication slices and56 decodes off Main. Mixed frame metrics and unchanged56 requests/43 evictions do not establish a general speedup or release-performance acceptance. Raw evidence remains private under `docs/local/android-performance-20261112/`; summarized results are embedded in the committed [report](ANDROID_PERFORMANCE_AUDIT_20261112.html).

## Still open

Remaining measure/layout/draw attribution, repeated controlled optimized benchmarks, warm-cache behavior, async file-size state, local-only cache sizing, approved Offline-tab/Unread-toggle integration, and broader adaptive/accessibility/Jellyfin/release gates.

See the [performance handoff](ANDROID_PERFORMANCE_UI_HANDOFF.md), [audit](ANDROID_BEST_PRACTICES_AUDIT.md), [Jellyfin contract](features/JELLYFIN_FIRST_LIBRARY.md) and [release evidence](../RELEASE_COMPLIANCE.md). Exact commit identity is recorded in the local Android engineering notes after the commit is created.
