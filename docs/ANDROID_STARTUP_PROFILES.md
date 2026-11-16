# Leaf Android startup profiles and benchmarks

The `profileQa` build type inherits the release-like Performance optimizations and existing development signing. Its separate `net.digitalbc.leafreader.benchmark` application ID and "Leaf Benchmark" label protect the normal Leaf install. It is nondebuggable/profileable. The Baseline Profile plugin creates an unminified producer variant for collection and a minified benchmark variant for measurement.

Generation is explicit, never part of ordinary assembly. Only the profileQa build type has a producer dependency; committing generated profiles into `app/src/main/generated/baselineProfiles` also lets Release/Performance consume them. Code namespaces are identical across application IDs. Never hand-author profile entries or treat generated rules as measured speedups.

## Fixtures and collection

Use a coordinated native/device window. Select one dedicated emulator or the separate benchmark app sandbox on an available QA phone. Never clear or reinstall the normal Leaf package. Device/account/auth/security/radio settings stay unchanged.

```sh
python3 scripts/create-r8-fixtures.py /tmp/LeafR8Fixtures
# Explicitly select the QA device. These files contain synthetic artwork only.
adb -s "$R8_TEST_SERIAL" push /tmp/LeafR8Fixtures /sdcard/Download/
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.leaf.qaFixtures=true
```

Set `ANDROID_SERIAL="$R8_TEST_SERIAL"` for Gradle when other devices are attached. Verify fixture byte hashes after staging; on scoped-storage devices use MediaStore or the system Files UI rather than changing storage permissions. With mergeIntoMain enabled, generation is aggregated, but the verified task graph contains only the two isolated profileQa flavor producers; no production app producer is configured. The test refuses to seed a nonempty library that lacks its synthetic fixtures. It imports the folder through Android's real SAF picker, using English test selectors, then exercises All, local Reader, a page turn, Continue and Library scrolling. No production automation hook, external server, authentication or private comic is involved. Setup happens outside measured blocks.

`startup` alone sets `includeInStartupProfile=true`. Reader/Continue/scroll collection is Baseline-only, preventing unrelated reading code from bloating startup DEX layout. Preserve the generated text, exact APK/maps/input identity and collection result. Confirm generated rules contain Leaf classes before committing them.

## Measurement

Rebuild the minified benchmark APK with the generated profile, then run `StartupBenchmark` on a physical device in a serial native window. Compare ten cold-start iterations without compilation and with the Baseline Profile required; missing profiles must fail, not silently fall back. Five separate frame iterations cover Reader/Continue/Library.

```sh
./gradlew :app:assembleFullBenchmarkProfileQa
./gradlew :macrobenchmark:connectedFullBenchmarkProfileQaAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.digitalbc.leafreader.macrobenchmark.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.leaf.qaFixtures=true
```

For the incremental Leaf-owned benefit, preserve a library-profile-only control APK before generation, then compare control and treatment with the same required-profile mode on the same populated QA install, preferably alternating A/B/A/B. None versus Require on the treatment alone includes existing dependency profiles and cannot isolate Leaf-owned gains. Preserve exact APK hashes, mappings, compilation configuration and iteration distributions; report inconclusive results honestly.

Task names must be checked against the configured plugin variants with `:macrobenchmark:tasks --all`. Use only the isolated benchmark application ID. Retain Macrobenchmark JSON and Perfetto traces with device/OS, compilation mode, thermal/network/battery and fixture state. An emulator run validates the harness/profile generation; it does not establish physical startup performance. StartupTimingMetric reports initial display here; no app-owned fully-drawn signal is added.

The synthetic workload is repeatable and offline but not representative of every reader library, remote cover workload or Series peel interaction. Minimum OS, broader physical devices, real SMB/Jellyfin, upgrades, accessibility and release gates remain separate. Do not disable benchmark errors or alter device settings to make a measurement pass.
