# Android R8 regression checks

Release and Performance both use code/resource shrinking and the optimize default configuration. Performance is nondebuggable and development-signed. The standalone `r8smoke` APK instruments its own process, owns its runner dependencies, and loads target code through a package context. This avoids runner dependencies being excluded merely because their original libraries also appeared in the target before R8 removed them. It adds no app dependency or consumer rules.

The SMBJ event handlers are discovered through runtime `@Handler` annotations. Keep the annotated methods and descriptor types, permitting obfuscated names. Ordinary JVM tests exercise event dispatch and cleanup without sockets; they cannot prove R8 retention. The mapping gate and instrumentation checks cover that separate contract.

## Build and inspect

Coordinate the existing shared native lease with other Android/iOS work. From a clean task branch:

```sh
./gradlew :shared:jvmTest :app:testFullDebugUnitTest :app:testFdroidDebugUnitTest
./gradlew :app:assembleFullRelease :app:assembleFdroidRelease :app:bundleFullRelease
./gradlew :app:assembleFullPerformance :app:assembleFdroidPerformance :r8smoke:assemble
python3 scripts/r8-smoke-probes.py app/build/outputs/mapping/fullRelease/mapping.txt
python3 scripts/r8-smoke-probes.py app/build/outputs/mapping/fdroidRelease/mapping.txt
```

The probe generator fails if any of SMBJ's three required callbacks or their annotation/event classes is absent. It emits the actual obfuscated names for instrumentation, without introducing test roots into the application. Generated test assets depend on the matching target's R8 task, so stale mapping data is not silently accepted.

## Run on a dedicated QA emulator

These tests target the normal Performance app ID. **Do not point connected tests at an owner's existing install.** Use a disposable emulator with no accounts or private files and restrict device selection explicitly. When multiple devices are attached, prefer serial-scoped manual installation/instrumentation over an unqualified `connectedCheck`.

```sh
# Set this only to the dedicated QA emulator.
R8_TEST_SERIAL=emulator-5580
adb -s "$R8_TEST_SERIAL" install -r app/build/outputs/apk/full/performance/app-full-performance.apk
adb -s "$R8_TEST_SERIAL" install -r r8smoke/build/outputs/apk/full/performance/r8smoke-full-performance.apk
adb -s "$R8_TEST_SERIAL" shell am instrument -w -r \
  net.digitalbc.leafreader.r8smoke/androidx.test.runner.AndroidJUnitRunner
# Repeat using both fdroid APKs, preserving the first run's logs.
```

Require all three tests to pass: runtime callback annotations/descriptors in the actual target classloader, Library startup, and Full JNI ZIP/RAR4/RAR5 extraction/callbacks (SevenZip absent in F-Droid). The tiny libarchive RAR fixtures and their BSD notices ship only in the test APK. These checks do not establish a real SMB server lifecycle or full comic import/reader acceptance.

Retain APK checksums, source/input identities, maps, merged configuration, seeds/usage/resource reports and instrumentation output. Compare code/resource/native byte counts with the same compiler and signing/packaging mode. Do not compare an unsigned Release APK against a signed Performance APK as an optimization delta.

## Remaining acceptance

Real minified SMB disconnect/session/tree cleanup and reconnect; RAR4/RAR5 comic imports; populated Room migration and backup/restore; physical devices and minimum OS; network consent/offline/retry and native reading remain separate checks. No unit-test or empty-library smoke result closes these gates. The S25's existing library/accounts/progress and security/radio settings must be preserved.

## CI and release automation

The reusable Android minified smoke workflow has only `workflow_call`. Existing CI calls it for both flavors; the release publication job depends on it. It retains maps, APKs and test results even on failure. No workflow is enabled or dispatched by adding this source. Keep the owner's CI-disabled setting until separately authorized; local emulator acceptance is not a GitHub Actions run. CI uses disposable API35 emulators for correctness, not startup timing acceptance.
