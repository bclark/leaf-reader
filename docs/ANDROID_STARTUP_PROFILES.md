# Leaf Android profile experiments

The separate `net.digitalbc.leafreader.benchmark` install (Leaf Benchmark) is nondebuggable/profileable and development-signed. `profileQa` inherits Release optimizations; the plugin creates unminified collection and minified measurement variants. It never replaces normal Leaf.

## Current decision

App-owned Baseline/Startup profiles are **QA-only**. `mergeIntoMain=false` stores the collected files in `app/src/fullProfileQa/generated/baselineProfiles`; Release, Performance and Fdroid do not consume them. Ordinary assembly never starts collection. Dependency profiles remain enabled, with explicit ProfileInstaller1.4.1 to support SDK37; transitive1.3.1 rejected the real required-profile test before any timings. See [official installer notes](https://developer.android.com/jetpack/androidx/releases/profileinstaller) and [variant scoping](https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles).

Matched-installer S25 A/B/A/B:10cold iterations per batch,20per condition, required-profile compilation,24synthetic comics. Library-only median112.426ms; app-profile median113.603ms. Batch medians: A1=113.878, B1=113.603, A2=111.941, B2=113.787ms. Descriptive bootstrap median-difference95% interval(control−candidate)−8.03..4.26ms includes zero, treats iterations independent, and is not broad causal evidence. The QA APK grew8,486,651→8,600,133bytes(+113,482). No demonstrated startup gain on this device justifies enabling these files in production.

All six physical cases pass: four matched A/B batches, candidate None10cold iterations and five candidate frame journeys. None median114.784ms is also inconclusive; do not attribute existing library-profile benefit to these Leaf files. Frames are observations without a matched frame control, not a smoothness improvement claim. Raw JSON/Perfetto, exact APK/maps/conditions and before/after normal-package identity are retained locally. Normal Leaf0.3.8 identity remained unchanged; no account/radio/security/normal-app data action. TTID only; no app-owned fully-drawn signal.

## Collection and smoke

Use an isolated worktree and a coordinated native/device window. Hold the shared native, Android functional, iOS functional QA and selected device leases; no competing Xcode. The separate iOS functional slot must also be excluded for collection/timing. Never clear/reinstall normal Leaf or change device settings to make a test pass.

```sh
: "${R8_TEST_SERIAL:?Set the dedicated QA serial first}"
python3 scripts/create-r8-fixtures.py /tmp/LeafR8Fixtures
adb -s "$R8_TEST_SERIAL" push /tmp/LeafR8Fixtures /sdcard/Download/
ANDROID_SERIAL="$R8_TEST_SERIAL" ./gradlew --no-parallel --max-workers=1 \
  :app:generateFullProfileQaBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.leaf.qaFixtures=true \
  -Pandroid.testInstrumentationRunnerArguments.class=net.digitalbc.leafreader.macrobenchmark.BaselineProfileGenerator
```

Verify fixture bytes. Where scoped storage denies direct writes, use normal MediaStore pending publication or the Files UI; do not alter storage permissions. The harness refuses unknown nonempty libraries, imports through real SAF and asserts24selected comics. Setup/import/count and the one-time system immersive hint happen outside collection/measurement.

Library resets to Series after process death, so Reader entry explicitly selects All and requires actual Reader page controls. Page1→2 and saved Continue page2 must be visible; a single tap targets the actionable card after bounded hierarchy stability, without screenshot readbacks inside frame measurement. Three-cold-restart fixture smoke exercises these destinations. In-app Back is covered; platform Back remains separate acceptance. Earlier ambiguous failures are retained, not promoted to product regressions or silently retried.

Only `startup` includes Startup Profile rules; Reader/Continue/scroll are Baseline-only. Accepted collection source7a66856582cf2a89c0fe9671b09714960687d7e6, all four leases, task-owned API37/16KB emulator,3/3tests,24verified fixtures, immutable input hashes unchanged. Both workloads stabilized at iteration7. Baseline21,968rules/2,008Leaf namespace rules, SHA256eb669a0065a0b4f823f9d2665a185dffa73efaad923c9e484dcdf17161512fc5; Startup18,726/1,376, SHA2564bfb4de91c2718580524379244b1d2dc3ff8741515c2608c4b283f0559fee3a5. Startup has zero ReaderScreen method entries. Earlier bdb1369 collection had separate iOS overlap; its exclusivity claim is superseded. No emulator latency claim.

## Measurement

```sh
./gradlew :app:assembleFullBenchmarkProfileQa :macrobenchmark:assembleFullBenchmarkProfileQa
ANDROID_SERIAL="$R8_TEST_SERIAL" ./gradlew --no-parallel --max-workers=1 \
  :macrobenchmark:connectedFullBenchmarkProfileQaAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.digitalbc.leafreader.macrobenchmark.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.leaf.qaFixtures=true
```

Preserve a zero-Leaf-profile control with the **same** installer, compiler, signing/packaging and app inputs. Alternate A/B/A/B with required-profile compilation; None versus Require alone conflates dependency profiles. Retain per-iteration JSON/traces and thermal/battery/OS/fixture conditions. Do not suppress unsupported-SDK, debuggable, missing-profile or benchmark errors. Trial expansion or promotion into Release requires new evidence and exact artifact review.

Synthetic CBZs are not representative of large/publisher artwork, remote covers, Series peel, every device or minimum OS. Real SMB/Jellyfin, upgrades, accessibility, licensing/privacy and release checklist gates remain separate. CI source defines both harness compilation and minified correctness smoke. The CI workflow remains disabled_manually; existing release workflows remain active and were not dispatched. No CI execution or timing acceptance is claimed.
