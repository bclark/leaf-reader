# Android recovery device checks

The `fullRecoveryQa` variant exercises actual Android process death and preference-write failures without opening the user's Leaf sandbox. Its application ID is **net.digitalbc.leafreader.recoveryqa**, label **Leaf Recovery QA**, and backup is disabled. Never substitute the production application ID into these fault-injection commands.

## Existing test setup and scope

Leaf uses manual constructor injection, JUnit4/Robolectric local tests, Room and coroutines, with a Compose application UI and a native startup error dialog. There was no instrumented test source set or device-test framework dependency. This small ADB-driven harness reuses those dependencies and production restore/startup code. No DI, mocking, screenshot or instrumentation framework was added. Local tests continue to cover transaction failure, cancellation, persisted recovery states, privacy and migrations. The device harness adds controlled real process kills and real filesystem write denial.

The harness source/manifest live ONLY in app/src/recoveryQa. Release builds do not include its exported activity or pause controls. RecoveryQaActivity also checks its exact isolated package name before any seed operation. Seed creates one synthetic metadata-only comic/list and resets ONLY this disposable package's database/preferences. It never imports real comics, configures a server or uses production credentials.

## Build and install

Use a connected test phone, explicit device serial, and a free screen. Replace SERIAL in each command; Android SDK tools must be on PATH.

```sh
./gradlew :app:assembleFullRecoveryQa --stacktrace
adb -s SERIAL install -r app/build/outputs/apk/full/recoveryQa/app-full-recoveryQa.apk
```

## Kill at each commit boundary

Run seed with BEFORE_PREFS first:

```sh
adb -s SERIAL shell am start -n net.digitalbc.leafreader.recoveryqa/net.digitalbc.leafreader.qa.RecoveryQaActivity --es operation seed --es stage BEFORE_PREFS
```

Wait for the actual on-screen READY message. It checks that page index 12, list membership and the pending journal have committed; columns remain 2 before preference save. The pause has a 120-second timeout. Do not count a timeout or unread readiness screen as a process-kill pass.

```sh
adb -s SERIAL shell am force-stop net.digitalbc.leafreader.recoveryqa
adb -s SERIAL shell pidof net.digitalbc.leafreader.recoveryqa
adb -s SERIAL shell am start -n net.digitalbc.leafreader.recoveryqa/net.digitalbc.leafreader.MainActivity
```

pidof must return no PID after the stop. Observe normal library startup (six columns, synthetic page 13/20, one list item), then run the independent read-only verifier:

```sh
adb -s SERIAL shell am start -n net.digitalbc.leafreader.recoveryqa/net.digitalbc.leafreader.qa.RecoveryQaActivity --es operation verify
```

Require PASS, a new PID, page=12, slot=2, list=1, journal=0 and preferences=7/7. The verifier checks all seven values and never invokes recovery itself. UI page 13 is the one-based display of saved page index 12.

Repeat the complete sequence with stage AFTER_PREFS. READY must show columns=6 but journal=1; this holds after the actual preference commit and before journal deletion.

## Real preference-write failure and Retry

Seed BEFORE_PREFS and confirm READY, then force-stop the QA package. Record the original preference-directory mode:

```sh
adb -s SERIAL shell run-as net.digitalbc.leafreader.recoveryqa stat -c %a shared_prefs
adb -s SERIAL shell run-as net.digitalbc.leafreader.recoveryqa chmod 500 shared_prefs
adb -s SERIAL shell run-as net.digitalbc.leafreader.recoveryqa test -w shared_prefs
adb -s SERIAL shell am start -n net.digitalbc.leafreader.recoveryqa/net.digitalbc.leafreader.MainActivity
```

test -w must fail. Require the real startup dialog, with library/settings inaccessible. Tap Retry while denial remains and require that the dialog remains. Restore the EXACT original mode (771 on the recorded Samsung test; do not assume it on another device), then tap Retry again. Require normal library startup and a passing verifier. Force-stop/relaunch and verify once more to prove preferences persisted, rather than merely changing in-memory values.

This is real permission-denied I/O coverage, not a physical disk-full/ENOSPC test. Do not fill a user's device storage. If screen ownership changes or permission is needed to unlock it, pause taps; restore permissions before handing back control.

## Cleanup and evidence

Restore preference-directory permissions even after a failed/aborted run. Restore any temporary display/rotation settings. Remove only the disposable QA app when finished:

```sh
adb -s SERIAL uninstall net.digitalbc.leafreader.recoveryqa
```

Keep the user's net.digitalbc.leafreader installation/data intact. Record APK hash, device, readiness/result PIDs, stop/relaunch results, exact failure/retry observations and cleanup in ANDROID_ENG_NOTES.md and RELEASE_COMPLIANCE.md. Bind these debug QA results separately from minified/final/Play-signed acceptance. Preserve failed/partial attempts as such.
