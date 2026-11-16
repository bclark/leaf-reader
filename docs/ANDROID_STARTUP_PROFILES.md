# Leaf Android startup profiles and benchmarks

The `profileQa` build type inherits the release-like Performance optimizations and existing development signing. Its separate `net.digitalbc.leafreader.benchmark` application ID and "Leaf Benchmark" label protect the normal Leaf install. It is nondebuggable/profileable. The Baseline Profile plugin creates an unminified producer variant for collection and a minified benchmark variant for measurement.

Generation is explicit, never part of ordinary assembly. Only Full profileQa has a producer dependency; committing generated profiles into `app/src/main/generated/baselineProfiles` also lets Release/Performance consume them. Code namespaces are identical across application IDs. Never hand-author profile entries or treat generated rules as measured speedups.

## Fixtures and collection

Use a coordinated native/device window. Select one dedicated emulator or the separate benchmark app sandbox on an available QA phone. Never clear or reinstall the normal Leaf package. Device/account/auth/security/radio settings stay unchanged.

```sh
: "${R8_TEST_SERIAL:?Set the dedicated QA serial first}"
python3 scripts/create-r8-fixtures.py /tmp/LeafR8Fixtures
# Explicitly select the QA device. These files contain synthetic artwork only.
adb -s "$R8_TEST_SERIAL" push /tmp/LeafR8Fixtures /sdcard/Download/
ANDROID_SERIAL="$R8_TEST_SERIAL" ./gradlew --no-parallel --max-workers=1 :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.leaf.qaFixtures=true \
  -Pandroid.testInstrumentationRunnerArguments.class=net.digitalbc.leafreader.macrobenchmark.BaselineProfileGenerator
```

Set `ANDROID_SERIAL="$R8_TEST_SERIAL"` for Gradle when other devices are attached. Verify fixture byte hashes after staging; on scoped-storage devices use MediaStore or the system Files UI rather than changing storage permissions. With mergeIntoMain enabled, generation is aggregated, but the verified task graph contains only the isolated Full profileQa producer; no production app producer is configured. The synthetic CBZ/Library/Reader workload uses shared code, so its main profile feeds both flavors. Fdroid has separate compile/minified smoke coverage; this collector does not cover flavor-specific RAR paths. The test refuses to seed a nonempty library that lacks its synthetic fixtures. It imports the folder through Android's real SAF picker, using English test selectors, then exercises All, local Reader, a page turn, Continue and Library scrolling. No production automation hook, external server, authentication or private comic is involved. Fixture import/count checks and first-run Android immersive-hint dismissal happen outside measured blocks. After each process restart, Reader entry explicitly selects All, since Library initially shows Series; matching Series titles are not accepted as Reader. Page controls must visibly move page1 to page2, saved Continue metadata must show page2, and the actionable card must settle before a single tap. These are bounded observable preconditions, not blind retries. The measurement harness checks hierarchy stability without screenshot readbacks, avoiding GPU capture work inside frame measurement. The separate fixtureJourney smoke repeats three cold restarts. In-app Back is exercised; platform Back remains separate acceptance.

`startup` alone sets `includeInStartupProfile=true`. Reader/Continue/scroll collection is Baseline-only, preventing unrelated reading code from bloating startup DEX layout. Preserve the generated text, exact APK/maps/input identity and collection result. Confirm generated rules contain Leaf classes before committing them.

## Measurement

Rebuild the minified benchmark APK with the generated profile, then run `StartupBenchmark` on a physical device in a serial native window. Compare ten cold-start iterations without compilation and with the Baseline Profile required; missing profiles must fail, not silently fall back. Five separate frame iterations cover Reader/Continue/Library.

```sh
./gradlew :app:assembleFullBenchmarkProfileQa
ANDROID_SERIAL="$R8_TEST_SERIAL" ./gradlew --no-parallel --max-workers=1 :macrobenchmark:connectedFullBenchmarkProfileQaAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.digitalbc.leafreader.macrobenchmark.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.leaf.qaFixtures=true
```

For the incremental Leaf-owned benefit, preserve a library-profile-only control APK before generation, then compare control and treatment with the same required-profile mode on the same populated QA install, preferably alternating A/B/A/B. None versus Require on the treatment alone includes existing dependency profiles and cannot isolate Leaf-owned gains. Preserve exact APK hashes, mappings, compilation configuration and iteration distributions; report inconclusive results honestly.

Task names must be checked against the configured plugin variants with `:macrobenchmark:tasks --all`. Use only the isolated benchmark application ID. Retain Macrobenchmark JSON and Perfetto traces with device/OS, compilation mode, thermal/network/battery and fixture state. An emulator run validates the harness/profile generation; it does not establish physical startup performance. StartupTimingMetric reports initial display here; no app-owned fully-drawn signal is added.

The synthetic workload is repeatable and offline but not representative of every reader library, remote cover workload or Series peel interaction. Minimum OS, broader physical devices, real SMB/Jellyfin, upgrades, accessibility and release gates remain separate. Do not disable benchmark errors or alter device settings to make a measurement pass.


## Collection evidence — 16 November 2026

Full QA collection source `bdb1369df379c8905824688fa494c126c75acf1b`, task-owned API37/16KB emulator, all three tests pass with zero failures/skips. Startup stabilized at iteration6; Reader/Continue/Library at iteration12. The independent smoke test also exercises three cold restarts. No physical timing result follows from collection.

Generated Baseline:21,953 rules,2,007 owned by the `net.digitalbc.leafreader` app namespace; SHA256 `1718742b88d4d2ac36a7040e63394ff13dc94354471b2263c1f797f9e024cfde`. Startup:18,711 rules,1,375 app rules; SHA256 `763ec6215255d7e618b27749941440738e5d41057b540370a054749b78165adc`. Startup has no `ReaderScreen` method entries; Baseline has31. Startup's Reader-package enum/settings references are legitimate cold Library configuration, not a Reader journey.

The preserved library-only control contains zero Leaf app entries and uses shipping R89.2.14. A fresh build at the exact producer configuration is byte-for-byte identical to the earlier control: APK SHA256 `89d0a4b4412a6de0442d87c5a543c73057d74e5139a1d3fcd35157978b94b589`. Test-only hierarchy-stability changes after collection do not change application code; physical measurements must use the current runner and record exact APK identities. Keep raw failed experiments separate from the accepted collection receipt. Package validation and physical comparison remain required.


Packaged-profile regression at `cc35b7c` plus those exact generated files passes: shared144, each flavor268pass/1expected live-SMB skip, both lints0errors/87existing warnings, both Release/Performance APKs, Full AAB and minified benchmark/runner builds. Shipping compiler remains R89.2.14. Profile candidate Full unsigned APK8,592,849bytes versus optimizer-only8,477,519; Fdroid4,567,599 versus4,453,101. The extra startup-layout/profile cost is real; keep this draft under review until the physical comparison supports it. No startup improvement is inferred from successful compilation. CI source also compiles each profile harness but runs no emulator timing; workflows remain disabled until separately authorized.


S25's first required-profile attempt exposed transitive ProfileInstaller1.3.1 rejecting SDK37 before any timings. Explicit1.4.1 is required; it supports the forward-compatible API31+ profile format and fixes method-flag transcoding. See [official release notes](https://developer.android.com/jetpack/androidx/releases/profileinstaller). Rebuild both control and treatment with1.4.1 before comparison; never compare across installer versions or suppress the unsupported-SDK error. Older3910e6d artifact sizes/results above are historical evidence, not final1.4.1 validation.
