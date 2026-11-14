# Release Compliance & Local Readiness Checklist

This document is the single source of truth for verified legal/compliance requirements, local build/test readiness, and remaining external release gates for **Leaf** (`net.digitalbc.leafreader`).

---

## 1. Current Android Release Candidate (`0.3.7` / `30700`)

- **Application ID:** `net.digitalbc.leafreader`
- **Version Name:** `0.3.7`
- **Version Code:** `30700` (exceeds Google Play's existing `30600` / `0.3.6` release; follows `MAJOR * 10000 + MINOR * 100 + PATCH`)
- **SDK Targets:** `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`, `buildToolsVersion = "36.1.0"`, `jvmTarget = "17"`
- **Source Baseline:** Local `main` merged with `origin/main` (`0711264778226735741de4c04059ee67f03eef98`, including PR #22 `e0ae5f7a21fd5e9100a75515672fcb69859fbe53` pointing the Android privacy policy link to `https://digitalbc.net/leaf/privacy/`)
- **Recoverable Pre-Work Checkpoint:** `refs/checkpoints/pre-android-handoff-20261107` (`dc3266672067cb23ecb05ae93d02cd86603673b7`, capturing the pre-merge `HEAD` `0f03e38b3bf803bb76663d499b0db394b1178a15` plus all uncommitted and untracked workspace files)

### Built Artifacts & SHA-256 Checksums (Updated Rebuild with Play Payments Policy Gating)

| Artifact | Path | Size | SHA-256 |
| --- | --- | --- | --- |
| Play Release Bundle (`fullRelease` AAB, unsigned) | `app/build/outputs/bundle/fullRelease/app-full-release.aab` | 11,024,394 bytes (11 MB) | `a49786f53a5fa9babfb43d06f2749ce51136f396d31bdef8d9afd3dbdad81461` |
| Play Release APK (`fullRelease` APK, unsigned) | `app/build/outputs/apk/full/release/app-full-release-unsigned.apk` | 8,303,554 bytes (7.9 MB) | `12d45fcbb654b62f101db3a4a65f7bc032a6230525020a3a910516c41b4c6da0` |
| F-Droid Release APK (`fdroidRelease` APK, unsigned) | `app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk` | 3,961,022 bytes (3.8 MB) | `b970b43efbd52bf58d7df7e0a46e331ec173f61e86635c34442af69e087892f0` |
| Full Debug APK (`fullDebug`) | `app/build/outputs/apk/full/debug/app-full-debug.apk` | 29,684,056 bytes (28 MB) | `b86a883f0a64d5a3eed341512c030dee8f82c1327c2db2a7b805e71a32609809` |
| F-Droid Debug APK (`fdroidDebug`) | `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk` | 23,614,402 bytes (23 MB) | `43a4dbf801c8fa9eeff367c76cf09f711bd51ea65e7092796026b3ba67114d21` |
| R8 Mapping (`fullRelease`) | `app/build/outputs/mapping/fullRelease/mapping.txt` | 38,845,148 bytes (37 MB) | `3ff08db4fcbefc65000bed092caae05a63e70fb8f987a215cfa9c384ddfec54b` |
| R8 Mapping (`fdroidRelease`) | `app/build/outputs/mapping/fdroidRelease/mapping.txt` | 37,508,026 bytes (36 MB) | `4c02dbfeff7c92b477760deab9d5078ac424a1d33f8452cab88313522fc8eea2` |

---

## 2. Verified Legal, Privacy & Packaging Compliance

- [x] **Primary Source Code License:** The project code is licensed under the **Mozilla Public License 2.0 (MPL-2.0)**, preserving inherited MPL-2.0 code from upstream Chika and modifications (`LICENSE`).
- [x] **Upstream Attribution:** Explicit attribution to upstream Chika (by Chakra / batunii) is retained across `README.md`, `THIRD_PARTY_NOTICES.md`, `PRIVACY.md`, `MenuScreen.kt` (Android), and `MenuView.swift` (iOS).
- [x] **Published Privacy Policy Link (`PR #22`):** `MenuScreen.kt` (`PRIVACY_URL`) points to `https://digitalbc.net/leaf/privacy/`, matching the published Android privacy policy.
- [x] **Google Play Payments Policy Gating (Ko-fi Omission in Play Build):**
  - Gated external donation links via `R.bool.enable_external_tips` (`false` in `app/src/main/res/values/bools.xml`, `true` in `app/src/fdroid/res/values/bools.xml`).
  - In the Google Play release (`full` flavor), the "Tip on Ko-fi" section is omitted from `MenuScreen.kt`, preventing rejection under Google Play's Payments policy (which forbids external donation links/webviews). The F-Droid release retains the option.
  - Attribution to the original upstream project (`github.com/batunii/chika`) is preserved across all builds.
- [x] **Exclusion of Packaged AI Model & LiteRT Runtime:**
  - Upstream `manga_panel_detector_int8.tflite` (`leoxs22/manga-panel-detector-yolo26n`) carries an **AGPL-3.0** license due to Ultralytics YOLO weights, and panel-by-panel guided reading is disabled in whole-page mode (`EXPERIMENT_DISABLE_PANEL_READING = true`).
  - Removed `com.google.ai.edge.litert:litert` (`libs.tensorflow.lite`) from `app/build.gradle.kts` and `gradle/libs.versions.toml`.
  - Excluded `*.tflite` from Android asset and resource packaging via `androidResources.ignoreAssetsPatterns` and `packaging.resources.excludes` in `app/build.gradle.kts`.
  - Replaced `MlPanelDetector.kt` with a dependency-free whole-page fallback (`tryCreate(...) = null`, `detect(...) = listOf(Panel.FULL_PAGE)`).
  - **Binary Inspection Proof:** `unzip -l app/build/outputs/bundle/fullRelease/app-full-release.aab` contains **0** entries matching `tflite`, `tensorflow`, or `litert`. `base/assets/` contains only `THIRD_PARTY_NOTICES.md` and `sevenzipjbinding-*.properties`.
- [x] **Cloud Backup Disabled & Database-Only Device Transfer:**
  - `app/src/main/res/xml/backup_rules.xml` excludes `root`, `file`, `database`, `sharedpref`, and `external` under `<full-backup-content>`.
  - `app/src/main/res/xml/data_extraction_rules.xml` excludes `root`, `file`, `database`, `sharedpref`, and `external` under `<cloud-backup>`, while retaining `<device-transfer><include domain="database" path="." /></device-transfer>` so only the local Room database transfers during direct device-to-device migration (excluding SMB credentials in `SharedPreferences` and comic archives in `files/comics/`).
  - Verified compiled XML trees inside `app-full-release-unsigned.apk` via `aapt2 dump xmltree`.
  - Users can explicitly export and restore a portable JSON backup (`BackupManager.kt`) containing reading progress, reading lists, and non-sensitive preferences (strictly excluding SMB passwords, server hosts, and sandbox file paths).
- [x] **16 KB Page-Size Compatibility (ELF & Runtime):**
  - Inspected all 8 native `.so` libraries packaged in `app-full-release.aab` (`lib7-Zip-JBinding.so` and `libandroidx.graphics.path.so` across `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`) using `xcrun llvm-objdump -p`: every `LOAD` segment has `align 2**14` (`0x4000` = 16 KB).
  - Exercised the candidate on a 16 KB page-size Android emulator (`Pixel_10_Pro`, `google_apis_playstore_ps16k/arm64-v8a`, Linux `6.12.81-android16`, `getconf PAGE_SIZE = 16384`).
- [x] **Third-Party Dependency Inventory (`THIRD_PARTY_NOTICES.md` & `app/src/main/assets/THIRD_PARTY_NOTICES.md`):**
  - AndroidX / Compose / Kotlin / Coroutines / Room (Apache-2.0)
  - Apache Commons Compress 1.27.1 (Apache-2.0)
  - SMBJ 0.13.0 (Apache-2.0)
  - 7-Zip-JBinding-4Android Release-16.02-2.03 (LGPL-2.1 + unRAR restriction; `full` flavor only, excluded from `fdroid`)
  - ZIPFoundation 0.9.19 (iOS, MIT) & libarchive / SwiftArchive 1.0.0 (iOS, BSD)
  - Anton & Archivo Fonts (SIL OFL-1.1)
- [x] **F-Droid Build Flavors:** `full` and `fdroid` product flavors build cleanly; `7-Zip-JBinding-4Android` is completely excluded from the `fdroid` dependency graph and APK (`app-fdroid-release-unsigned.apk` has zero native `.so` libraries beyond `libandroidx.graphics.path.so`).
- [x] **Store Metadata & Assets:**
  - Added `fastlane/metadata/android/en-US/changelogs/30700.txt`.
  - Updated `short_description.txt` (70 chars, within 80 limit) and `full_description.txt` to accurately reflect whole-page/spread reading, CBZ/CBR/PDF archives, and offline privacy without overclaiming active ML models.
  - Generated compliant 1024×500 24-bit RGB `featureGraphic.png` in `fastlane/metadata/android/en-US/images/` using project fonts and palette.

---

## 3. Verification & Test Evidence (2026-11-07)

### Toolchain
- **JDK:** OpenJDK `27.0.1.0.2` (Temurin; Gradle daemon configured for JVM 21 via `gradle/gradle-daemon-jvm.properties`, Kotlin `jvmTarget = 17`)
- **Gradle / AGP / Kotlin:** Gradle `9.4.1`, Android Gradle Plugin `9.2.1`, Kotlin `2.2.10`
- **Android SDK:** `compileSdk = 36`, `targetSdk = 36`, `buildToolsVersion = "36.1.0"`

### Automated Unit Tests & Lint
- [x] **Shared KMP Unit Tests (`./gradlew :shared:jvmTest`):** **105 passed, 0 skipped, 0 failed** (panel decoding, ordering, planning, gap-filling, reliability, camera math, format detection, natural sorting, SMB support).
- [x] **Android Unit Tests (`./gradlew :app:testFullDebugUnitTest :app:testFdroidDebugUnitTest`):** **51 passed, 1 skipped, 0 failed** per flavor (`BackupManagerTest`, `ComicArchiveFactoryTest`, `PdfComicArchiveTest`, `ComicMetadataParserTest`, `ReaderSpreadCalculatorTest`, `SmbModelsTest`, `SmbErrorsTest`).
- [x] **Live SMB Smoke Test (`LEAF_SMB_LIVE_TEST=1 ./gradlew :app:testFullDebugUnitTest --tests '*SmbLiveTest' --rerun-tasks`):** **1 passed, 0 failed (`3.623s`)** — verified live SMB share connection, directory listing, file download, and transfer cancellation without leaving partial files.
- [x] **Android Lint (`./gradlew :app:lintFullDebug :app:lintFdroidDebug`):** **Passed (0 errors)**.

### 16 KB Emulator Acceptance Checks (`Pixel_10_Pro`, `PAGE_SIZE = 16384`)
- [x] **In-Place Upgrade (`30300` -> `30700`):** Upgraded existing `0.3.3` (`30300`) installation in-place (`adb install -r`). Verified all 4 existing comics (`ColdPdfProof.pdf`, `MixedSheetProof.cbz`, `SheetProof.cbz`, `WideSheetProof.cbz`), reading progress (`lastPage`), cover thumbnails, and reader layout preferences survived intact, with `series` metadata backfilled cleanly.
- [x] **Whole-Page Reading, Orientation & Two-Page Spreads:** Opened `WideSheetProof.cbz`, navigated pages (`pg 3/12` -> `pg 4/12`), rotated to landscape (`2856x1280`) to verify two-page spread rendering (`pages 4–5`), and rotated back to portrait.
- [x] **Offline Reading & Persistence Across Restart:** Disabled Wi-Fi and cellular data (`svc wifi disable; svc data disable`), force-stopped and relaunched Leaf offline, and verified both in the Library UI (`33% · pg 4/12`) and in SQLite (`comics.db`: `lastPage = 3`) that reading progress persisted and local comics open without network connectivity.
- [x] **Menu, Privacy Link & SMB Error Handling:** Verified `VERSION 0.3.7`, `Privacy policy — Leaf collects no data.` (`https://digitalbc.net/leaf/privacy/`), `Open-source licenses & notices.`, and `SAVE & TEST CONNECTION` offline/unconfigured error handling (`✕ Check the server address, port, share and folder.`) with immediate UI recovery.
- [x] **Clean Install of R8-Minified Release Candidate:** Clean-installed `/tmp/app-full-release-30700-test-signed.apk` (`app-full-release-unsigned.apk` signed with debug keystore) on the 16 KB page-size emulator and verified clean launch and rendering of the empty library state (`YOUR STACK IS EMPTY` / `OPEN A COMIC`).

---

## 4. Google Play Console Declarations Guide

Use these exact responses when filling out the App Content forms in Google Play Console:

### 1. Data Safety Questionnaire
- **Does your app collect or share any of the required user data types?** -> **No**
- **Data Collection:** None.
- **Data Sharing:** None.
- *Justification:* All comics, metadata, cover thumbnails, reading history, and app preferences remain strictly local on the user's device in app-private storage. Automatic cloud backup is disabled; direct device-to-device migration transfers only the local database; and SMB connections connect directly from the user's device to their LAN/NAS server without intermediary servers.

### 2. App Access
- **All functionality is available without special access restrictions** -> Select **All functionality is available without special access**. (No login credentials, two-factor authentication, or memberships are required).

### 3. Ads
- **Does your app contain ads?** -> **No**

### 4. Content Rating (IARC)
- **Category:** Utility, Productivity, Communication, or Other
- **Violence, Sex, Profanity, Crude Humor:** None built into the app (it is a generic document/comic reader).
- **Online interactions:** Does not facilitate user-to-user communication or sharing.
- **Physical location:** Does not share physical location.
- **Digital purchases:** Does not allow users to purchase digital goods.

### 5. Target Audience & Content
- **Target Age Group:** 13 and older (or 18 and older). Do **not** declare children under 13 to avoid COPPA / Families Policy requirements.
- **Appeal to Children:** "No" (store listing is not designed to appeal primarily to children).

### 6. Government Apps / Financial Features / Health
- **Is your app developed by or on behalf of a government?** -> **No**
- **Financial Features:** None -> Select **My app doesn't provide any financial features**.
- **Health / Medical Features:** None.

---

## 5. Remaining External Gates & Next Steps for Bryan

1. **Independent Upload Keystore Backup:**
   - Securely back up the upload keystore file and its password/alias credentials outside the single Mac development machine (e.g., an encrypted vault or offline password manager).
2. **Production Keystore Signing:**
   - Supply `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` to rebuild a production-signed AAB, or sign `app-full-release.aab` using `jarsigner` / `apksigner`.
   - Verify that the signing certificate SHA-256 matches the Upload Certificate registered in Google Play Console (under *Setup > App Integrity*).
3. **Submit Staged Console Changes:**
   - Submit the staged Privacy Policy URL (`https://digitalbc.net/leaf/privacy/`).
   - Upload the 512×512 icon, 1024×500 `featureGraphic.png`, and phone screenshots from `fastlane/metadata/android/en-US/images/`.
   - Fill out the App Content questionnaires using the declarations guide above.
4. **Qualifying Closed Test (12 Testers / 14 Days):**
   - Create the closed testing release track and upload the signed `0.3.7` (`30700`) AAB.
   - Invite at least 12 testers via email list / Google Group.
   - **Crucial:** Existing internal testers must opt out of the internal test before opting into the closed test.
   - Maintain continuous opt-in for at least 14 days and record qualifying dates.
5. **Real Physical Device Upgrade QA:**
   - Install the Play-delivered build on a physical device currently running `0.3.6` (`30600`) to confirm that Google Play's App Signing key seamlessly applies the upgrade over the existing installation without database or progress loss.


---

## 6. iOS local candidate — 7 November 2026

**Local candidate built, tested, signed and exported; device acceptance is recorded below with explicit failures and blocked checks. This is not App Store release clearance.**

### Source preservation and decisions

- Checkout: `/Users/bryanclark/Developer/leaf-reader`, branch `main`, HEAD `a0780e8a650003f0014d021c98acf4cf32438165`. One worktree; no staged changes at start. Locally recorded `origin/main` is `0711264778226735741de4c04059ee67f03eef98`; HEAD is two commits ahead, zero behind. No Git fetch was performed, so this is not a statement about current remote branch state. A later read-only GitHub file request checked the published privacy policy only. The merge already includes that baseline; no additional merge was needed.
- Before any edits, preserved all **75 changed/untracked files** (including Android work, StoreKit, UI, SMB, catalogs, the review CSV, drafts and screenshot evidence) in `/private/tmp/leaf-ios-handoff-20261107T163737Z/preserved-work.tar.gz`. SHA-256: `ab83763d96320efb7da3d0c2dbcb28cdb4e10ea02b1ad6fca3d8979b7425fd25`.
- The same evidence directory contains `HEAD.txt`, `status.txt`, `staged.patch`, `working.patch`, `history.txt`, `worktrees.txt` and per-file `manifest.json`. Recover in a separate checkout at the recorded HEAD, then overlay the preservation archive; do not reset or overwrite an unrelated checkout. Private ignored credentials/signing settings are deliberately not copied into repository evidence.
- Candidate application source is the preserved dirty tree, not HEAD alone. No app code, product IDs, prices, localization mechanism, model exclusion, CI configuration, or build number was changed by this verification pass. Source/configuration/test input manifest: `candidate-inputs.json`, SHA-256 `0436616549fadab78ebd45a6b507ad646676dffbb83987e727126eb58e0bd2ae`.
- Concurrent external edits were detected after building: `app/build.gradle.kts` and `gradle/libs.versions.toml` gained Android OkHttp/mockwebserver dependencies. This handoff did not make or revert them. iOS app/test/shared source hashes remain unchanged; only the unrelated catalog additions differ in the initial candidate-input manifest. `concurrent-android-changes.patch` and `final-candidate-inputs.json` retain the distinction. Final manifest SHA-256: `aae1cec8edccfd040150b6213d4c33145bc6154f32eb3ec936193d5e6849bb54`.
- Keep version `1.0`, build `2026110601` for this local candidate. Whether that build number is unused in App Store Connect remains unverified. Generated Xcode project/workspace/Pods remain uncommitted. The existing generated workspace was built; regeneration was unnecessary because project inputs were unchanged.

### Release archive, export and local trust

Release archive and local development IPA export **PASS**. Xcode 27.1 (`27A9275`), iOS/iOS Simulator SDK 27.1, Swift 6.4 (`swiftlang-6.4.0.34.1`), Leaf scheme, Release configuration, arm64; iOS minimum 16.0; bundle `net.digitalbc.leafreader`, version 1.0, build 2026110601. The simulator acceptance build uses Release with signing disabled. Native automated tests used Debug from the same unchanged application source.

Durable local evidence directory: `iosApp/build/handoff-20261107/` (ignored by Git). Original execution paths were under `/private/tmp/leaf-ios-handoff-20261107T163737Z/`; logs retain those paths. This directory includes the recovery archive, diff, source manifest, resolved dependency locks, logs, test results, signed archive, IPA, export settings and checksums. `commands.txt` records actual commands; `reproduce.sh` documents the framework → XcodeGen → CocoaPods order for a fresh reconstruction. Preserve the private local signing setup separately; no signing keys are included.

| Artifact | Durable local path | SHA-256 |
| --- | --- | --- |
| Archive ZIP | `iosApp/build/handoff-20261107/Leaf-2026110601.xcarchive.zip` | `9667888bf077a4128546579ff501bba220bbf94349cfc233b02ae858a828b71b` |
| Development IPA (12,670,532 bytes) | `iosApp/build/handoff-20261107/export/Leaf.ipa` | `10d65e9214a6ee35caebe502cefa9670fe10020b2d398004562bf20f6d4adb08` |
| Archived app executable | `iosApp/build/handoff-20261107/Leaf-2026110601.xcarchive/Products/Applications/Leaf.app/Leaf` | `09abea359142495b15de07d76855bd1ebf259ea6521b597e3ca6e74b6d9c9910` |

Actual successful archive command, run from `iosApp`:

```sh
xcodebuild -workspace Leaf.xcworkspace -scheme Leaf -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath /private/tmp/leaf-ios-handoff-20261107T163737Z/Leaf-2026110601.xcarchive \
  -derivedDataPath /Users/bryanclark/Library/Developer/Xcode/DerivedData/Leaf-excjxovsdfjwfpfvlpptclhrbgaw \
  -disableAutomaticPackageResolution archive
```

The generated prebuild phase invokes `./gradlew :shared:linkReleaseFrameworkIosArm64` (or `IosSimulatorArm64` for simulator). `archive.log` records success. Main’s existing appearance/reader/SMB sources remain included; native appearance, reader, series and SMB regression tests passed. No merge/reversion removed the current-main improvements.

Local export command:

```sh
xcodebuild -exportArchive \
  -archivePath /private/tmp/leaf-ios-handoff-20261107T163737Z/Leaf-2026110601.xcarchive \
  -exportOptionsPlist /private/tmp/leaf-ios-handoff-20261107T163737Z/ExportOptions.plist \
  -exportPath /private/tmp/leaf-ios-handoff-20261107T163737Z/export
```

Successful export settings: `method=debugging`, `destination=export`, `signingStyle=automatic`, `teamID=X5G6SNJM8H`, `stripSwiftSymbols=true`, `thinning=<none>`. No `-allowProvisioningUpdates` or upload option was used. The first manual-signing export attempt failed because the existing profile is Xcode-managed; switching export style to match that existing profile succeeded. Both attempts and settings are retained; this was a local tooling mismatch, not an app defect. The initial sandboxed simulator build also failed to access the workspace; retry with local access succeeded.

Archive/IPA inspection (`archive-inspection.json`, `artifact-checksums.json`) verifies **zero** `.tflite`, `.storekit`, test-bundle, CBZ/CBR/PDF fixture entries; `en.lproj` and `es-MX.lproj` are present; `ITSAppUsesNonExemptEncryption` is absent. The TensorFlowLite runtime remains, consistent with the existing model-only exclusion. Both archive app and extracted IPA pass `codesign --verify --deep --strict`.

Signing identity: Apple Development: Bryan Clark (`NC8SGSMCDD`), team `X5G6SNJM8H`. Embedded existing profile `454ba596-63e0-4a24-81ad-3577590a5b7e` (“iOS Team Provisioning Profile: *”) expires 31 October 2027 02:07:29 UTC and covers 8 registered devices. App entitlements contain `X5G6SNJM8H.net.digitalbc.leafreader`, team identifier and `get-task-allow=true`. This is **development signing**, not App Store distribution signing. Raw local profile/device data stays in ignored local evidence, not this document.

Physical-device install and launch **PASS**: the exact archived Release app was installed in place and launched using `devicectl` on Bryan’s iPhone (iPhone 11 Pro Max, iOS 27.0.1), without an uninstall or erase operation. `device-install.json` and `device-launch.json` report success. The immediately preceding app was this session’s Debug test build; this is **not** controlled prior-release upgrade evidence and does not establish library migration from a known older signed release. TestFlight/storefront trust remains separately blocked.

Final linked-binary inspection confirms CommonCrypto cryptor/HMAC/PBKDF imports in Leaf and AES128CCM/reference AES symbols in the shipped AMSMB2 framework (`app-shipped-crypto-symbols.txt`, `smb-shipped-crypto-symbols.txt`). Thus the encryption question is supported by final-binary evidence, not only dependency source.

### Fresh tests and tips evidence

| Check | Fresh outcome | Environment and evidence |
| --- | --- | --- |
| Shared KMP JVM suite | PASS: 105 tests, 0 failures/errors/skips | `./gradlew :shared:jvmTest --rerun-tasks --stacktrace`; `shared-tests.log`, `shared-test-results/` in the evidence directory |
| Xcode build | PASS | `BuildProject` MCP, Leaf scheme, Bryan’s iPhone; copied log in `xcode-build/` |
| Native iOS suite | PASS: 85 tests, 0 failures/skips/not-run | `RunAllTests` MCP, Leaf scheme, Bryan’s iPhone, iOS 27.0.1, 7 Nov 11:38:42 EST; summary, console and `.xcresult` copied to `native-tests/` |
| Tip state handling | PASS: 11 deterministic tests within the 85 | Cancellation/error busy-state release, pending persistence and explicit escape, verification failure, deduplication/races, delayed completion, quiet recovery, restricted payments and retry |
| Local StoreKit integration | PASS: 2 tests within the 85 | `SKTestSession`, dialogs disabled, local `Tests/LeafTips.storekit`; all tiers plus repeated small tip, Ask to Buy approval, refund, unfinished recovery; fixture es_MX/MEX names verified. No real-money purchase and no live storefront validation |
| Localization runtime | PASS: 5 tests within the 85 | Compiled strings/permission text, plurals 0/1/2/11/1000, Spanish font glyphs, Foundation language matching, Unicode backup round trip |
| Live SMB transport | PASS | Mac harness using current iOS transport sources and existing private `.env`: authenticated root/nested browsing, 142,054,782-byte download, cancellation at 8,388,608 bytes with no later writes, reconnect. `smb-live-retry.log`; private names/credentials not included |

Actual live command, from repository root: `bash iosApp/ci/smb-spike/run.sh /Users/bryanclark/Developer/leaf-reader/.env`. Its first sandboxed attempt failed (`sandbox_apply: Operation not permitted`); the retry with local access passed. The harness uses temporary staging and removes it. This is transport evidence on the Mac, not an iOS Local Network permission or native Files/SMB UI acceptance result. Mock SMB tests in the native suite cover short reads, disconnect cleanup, cancellation, failed replacement preservation, partial batches and retry-related state.

The prior 85 distinct passes and 6 November archives remain **historical** in [tips implementation](docs/IOS_TIPS_IMPLEMENTATION.md) and [localization QA](docs/localization/QA_RESULTS.md); the fresh 85 above came from one complete run. Test counts are not added across repeated runs.

Source inspection confirms `TipStore.swift` maps fetched `Product.displayName`/`displayPrice`, finishes recognized verified consumables, keeps quiet unfinished recovery, and exposes `allowAnotherTip()` with an explicit separate-charge warning in `TipJarView.swift`. In-memory acknowledgment deduplication is best effort; there is no exactly-once guarantee across crashes/restarts. Recovery grants no features and does not present a blocking purchase sheet. The three `net.digitalbc.leafreader.tip.{small,medium,large}` identifiers remain proposals; confirmed USD base amounts remain $1.99/$4.99/$9.99, with live Apple configuration unverified.

### Acceptance outcomes and remaining device gates

Fresh simulator acceptance uses the same source in a Release arm64 simulator build, ordinary Leaf scheme, version 1.0/build 2026110601. No StoreKit fixture is attached to this standalone app. Detailed outcomes and reproduction steps are in `iosApp/build/handoff-20261107/device-qa/RESULTS.md`; device-specific screenshots/hierarchies are retained alongside it; these are engineering evidence using synthetic pages, not approved store screenshots.

| Check | Outcome | Evidence/limitation and next action |
| --- | --- | --- |
| Physical iPhone Release install/launch | PASS | iPhone 11 Pro Max, iOS 27.0.1; exact archive app installed in place and launched; development trust only |
| Clean simulator installation | PASS | Isolated iPhone Duo, iOS 27.1; English library displayed |
| Open/read/resume and progress after restart | PASS, controlled seeded comic | Three synthetic pages copied into Documents/Comics; page 2 survived background/termination/relaunch and reopen. This does not prove Files picker import |
| Native format imports | PASS, automated API tests | CBZ/misnamed ZIP, PDF, awaited RAR conversion, cancellation and failure cleanup within fresh native suite; end-to-end Files UI remains separate |
| Files picker import UI | BLOCKED | Picker opens but On My iPhone exposes no fixture location in isolated runtime; do not count seeded files as UI imports. Retest from a Files-accessible controlled archive |
| Duo landscape | BLOCKED for coverage | Device reports landscape but app remains portrait; cause (runtime presentation versus app behavior) is unverified. Captured twice; ordinary-device UI fallback was rejected by the tool. Verify on an eligible conventional iPhone/iPad |
| Compact library controls | FAIL, reproducible visual defect | After a comic is present and EDIT appears, library column choices 2/3/4 clip in the narrow Duo layout. `05-en-restart-library-column-clipping.png` and hierarchy show narrow text bounds. Preserve source in this verification pass; fix layout and recheck before release |
| es-MX application rendering | PASS for sampled surfaces | Launch preferences render Spanish; this is separate from region/storefront and fluent approval |
| Largest-text tips explanation/unavailable/retry/close | PASS for sampled simulator state | Text remains readable by scrolling; Retry and Close work. No purchase was invoked; product-card/success/pending UI at large text still requires configured simulation or sandbox |
| Native Settings language switch | BLOCKED | Settings → Apps is empty on isolated restricted runtime; screenshot retained. Foundation matching tests and launch preferences do not substitute for this check |
| Known prior-release signed upgrade/data migration | BLOCKED | No controlled previous signed release/library baseline was established. Current in-place Debug-to-Release install is not this test. Next: select recorded prior build, seed controlled data, snapshot it, then upgrade and compare |
| iPad simulator clean installation/launch | PASS | iPad Pro 13-inch (M5), iOS 26.0 (23A343); same Release simulator app; screenshot `11-ipad-clean-library.png` confirms English library |
| iPad simulator reader/rotation/tips/large-text interaction | BLOCKED | Device tool rejects iOS 26 iPad and lists only iOS 27.1 Duo simulators; simctl installation/launch/screenshots work but no supported touch session. Retest on an eligible iPad runtime/device |
| Physical iPad acceptance | BLOCKED | No physical iPad connected |
| Physical iPhone interactive UI/orientation fallback | BLOCKED | Non-workspace device session rejected connected physical iPhone; tool eligible list exposed only Duo simulators. devicectl installation/launch and native tests remain valid separate evidence |
| Offline/network-disabled acceptance | BLOCKED for full matrix | Local reading and imports use local data, but a controlled network-disabled device run was not established; verify with networking unavailable and restore connectivity afterward |
| Native SMB permission/browse/import/error matrix | BLOCKED for device UI coverage | Mac live transport and mocked native cleanup checks pass; real iOS allow/deny, bad credentials, server shutdown/Wi-Fi loss, large-file import/memory and retry UI still need a controlled device/server session |
| Full VoiceOver/large-text/compact-phone matrix | BLOCKED | Sampled hierarchy/text checks are not a spoken VoiceOver audit or all-screen accessibility coverage; run the outstanding matrix |
| Published iOS privacy policy currency | FAIL relative to candidate | Live 5 November policy omits tips and still describes bundled model; local update is prepared. Review and separately authorize publication |
| Fluent translations, glossary, listing and screenshot rights approval | BLOCKED | No fluent reviewer/owner approval supplied; drafts and existing reviewer fields preserved |

### Localization, store drafts and review decisions

- Existing [305-row review sheet](docs/localization/ES_MX_REVIEW.csv) is unchanged: 300 nonempty app keys + 5 InfoPlist keys, all represented, no missing Spanish or context, no flat placeholder or plural-substitution descriptor mismatches. Empty extracted key has no copy. `localization-audit.json` records the checks, including normalization of CSV escape spelling for comparison only. Review-sheet SHA-256: `1659234bc632e089bd2fdd08cb8b9e4a9f3543d986d366bf576156dc2212cfd2`.
- All 305 approvals remain **Pending**. No reviewer has been identified; no translation was promoted to human-approved. Reviewer questions remain: Mexican usage of **cómic**, **lista de lectura**, **respaldo**, **propina**; tone, gender/plurals, placeholders, truncation and large-text layouts on actual screens. No verified copy gap justified rewriting the sheet or creating a duplicate.
- Native per-app language support is implemented through English development language + es-MX catalogs and ordinary system matching. Source has no custom locale/bundle override or in-app language selector. Localized labels, errors, accessibility labels and case wrappers were inspected; StoreKit product strings intentionally use verbatim display. The en_US_POSIX locale in MenuView formats backup filenames, not app language. Runtime matching tests pass; owner approval and actual Settings language-switch acceptance are separate gates.
- Preserved [English iOS listing](docs/localization/IOS_LISTING_EN_DRAFT.md), [Spanish listing](docs/localization/IOS_LISTING_ES_MX_DRAFT.md), [English support](docs/localization/IOS_SUPPORT_EN_DRAFT.md), [Spanish support](docs/localization/IOS_SUPPORT_ES_MX_DRAFT.md), [Spanish privacy](docs/localization/IOS_PRIVACY_ES_MX_DRAFT.md), and [prose review](docs/localization/ES_MX_PROSE_REVIEW.json). Feature claims match whole-page/spread reading, CBZ/CBR/PDF, Files/SMB import, local progress, lists, backups without comic files and optional tips. No panel-detection or approved-Spanish claim was added.
- Metadata character counts (name/subtitle/promotion/keywords): English 18/20/147/59; Spanish 22/24/150/65. `metadata-audit.json` records values. Account-entry validation and owner approval remain pending.
- iOS points to the repository [privacy policy](PRIVACY.md), not the Android-hosted policy. The local policy describes StoreKit recovery, optional SMB and device-only Keychain; the local privacy manifest declares no tracking/collected data and UserDefaults reason CA92.1. **Published-policy mismatch found:** a read-only GitHub connector request on 7 November returned the live policy dated 5 November, still claiming a bundled model and omitting optional StoreKit tips. The existing local 6 November policy addresses these points. Keep the local draft; owner review and separately authorized publication are required before release. Final App Store privacy answers remain pending.
- Existing [screenshot manifest](docs/localization/screenshots/manifest.json) and images were preserved. They explicitly describe 6 November source `0f03e38` plus dirty changes, and are historical engineering evidence, not fresh screenshots of this candidate. Synthetic QA content is identified there. Owner rights clearance for any final published assets remains pending.

### Encryption inventory and questions for qualified review

The app’s encryption declaration remains **unset**. No exemption or legal classification is asserted.

| Component | Resolved version/revision | Observed capability/configuration |
| --- | --- | --- |
| AMSMB2 | 4.0.3; `1726aaaf7adf63d7d1d2a0c5d1b0e635028215c0` | Dynamic framework; compiles libsmb2 native library |
| libsmb2 | `aff9fa6ba9f41cfd3c15d184554601ec3f6d8d03` (fresh cached-checkout `git ls-tree` verification) | Native AES128CCM sealing, AES-CMAC/HMAC-SHA256 signing, NTLM MD4/HMAC-MD5. Apple AES block path calls CommonCrypto; third-party CCM/protocol logic remains |
| SwiftArchive/libarchive-for-swift | 1.0.0; `e8b70b7f0e15108a828362fe696e63898d5be916` | Vendored libarchive headers identify 3.8.7; arm64 binary contains cryptor, PBKDF and hash symbols using CommonCrypto |
| ZIPFoundation | 0.9.20; `22787ffb59de99e5dc1fbfe80b19c97a904ad48d` | Existing CBZ ZIP read/write dependency |
| TensorFlowLite pods | 2.17.0 | Existing runtime dependencies retained; excluding model resources does not remove these runtimes |

Evidence: copied `Package.resolved`, `Podfile.lock`, `crypto-source-inventory.txt`, `libarchive-crypto-symbols.txt`; libarchive arm64 static binary SHA-256 `569b614b2a1fd03b93f0751a2da89f0786cfeb0f9edf2e33a052715ddddf7fa7`. `NativeSmbSession.connect()` passes `requireEncryption` to AMSMB2; default `false` is not an absence-of-capability finding. CbrReader enables libarchive formats and does not provide a passphrase UI. Static-library capabilities do not alone prove every routine survives the final link; final bundle inspection is recorded separately.

Qualified-review questions: Which encryption classification applies to this exact shipped combination? What reporting/documentation, if any, is required? Which intended territories (including any territory-specific requirements) are approved? Does bundled libarchive’s compiled cryptographic capability affect the answer despite no password UI? What LGPL corresponding-source/replacement and distribution obligations remain for AMSMB2/libsmb2? Owner must supply the territory decision; do not copy the older `false` declaration or infer exemption from the default. See existing [SMB dependency evidence](docs/SMB_IOS_SPIKE_RESULTS.md).

### External account gates

No authenticated App Store Connect connector/browser session is available in this tool environment. On 7 November 2026, the stated banking recheck deadline (7 Nov 13:15 UTC, 24 hours after 6 Nov 13:15 UTC) had passed, but elapsed time is not evidence of readiness.

| Gate | Outcome | Next action |
| --- | --- | --- |
| Banking update status | BLOCKED: no authenticated account observation | Owner reads current status and records timestamp, without exposing financial details |
| Paid Apps Agreement | BLOCKED: current status unobserved | Read current agreement readiness |
| Tax readiness | BLOCKED: current status unobserved | Read current readiness separately from banking |
| Existing products/types/prices/sale availability | BLOCKED: proposed IDs unconfirmed | Read existing records; align code only after confirming them; create no products under this handoff |
| US and Mexico live sandbox/storefront | BLOCKED: account/products/test access unverified | Test with fixture disabled using authorized sandbox accounts; record actual prices and product metadata |
| TestFlight/distribution | BLOCKED: separate upload authorization required | Do not upload to bypass any gate |

No upload, publishing, real-money purchase, CI enablement, product creation, banking/agreement/tax mutation, or legal declaration was performed. This handoff made no remote repository changes. Local completion is assessed separately from these external gates.


### Final handoff status and retained questions

**Local deliverable complete:** preserved checkout, fresh tests, signed Release archive, local development IPA, resource/signature inspection, physical install/launch, available simulator acceptance, live Mac SMB transport, catalog/draft review and encryption inventory. **Release acceptance incomplete:** one reproduced compact-layout defect, published-policy mismatch and the individually blocked device/account/storefront/review gates above. No app source fix is claimed by this verification-only pass.

Decisions: preserve current main and existing uncommitted implementation; no redundant merge; retain the existing build number/product proposals; retain native system language matching; leave all translations unapproved; leave encryption unset; keep drafts unpublished; distinguish simulation, Mac transport, device tests and distribution evidence. Only this readiness document and pointers in the two existing tips/localization evidence documents were edited by this handoff. Concurrent Android work was preserved separately.

Questions needing owner/qualified-review answers: Who will review Mexican Spanish and the four glossary terms? Is native per-app language selection approved? Are the existing three product records, types, USD base prices and sale availability confirmed? What are current banking, Paid Apps and tax statuses (with observation timestamps)? Which prior signed build and controlled library should be used for upgrade QA? Which territories and encryption/licensing classification are approved? Are listing copy, screenshot rights and privacy-policy publication approved? The asynchronous reviewer/language question had no answer at report completion; no approval was inferred.

The final working-tree checkpoint is `iosApp/build/handoff-20261107/final-work.tar.gz`, with per-file manifest and SHA-256 in `final-checkpoint.json`; final unstaged/staged diffs are `final-working.patch` / `final-staged.patch`, and final status is `final-status.txt`. HEAD remains the recorded commit; no commit was created. The archive itself is fixed to the unchanged iOS/shared source verified above; remaining differences include preserved unrelated Android work and these evidence updates. Created simulator sessions were closed and isolated simulators shut down, retaining only controlled test data for reproduction.


## 7. iOS replacement local candidate — 7 November 2026

Bryan authorized appearance verification, compact-library layout correction, targeted device QA and a replacement local Release candidate. The replacement is built/exported and inspected; it is **not cleared for App Store release**. No upload, publication, legal declaration, product/account mutation or commit was performed.

### Source and artifacts

- Source is isolated at `/private/tmp/leaf-replacement-20261107`: base `a0780e8a650003f0014d021c98acf4cf32438165`, preserved original candidate inputs, historical Android Gradle configuration required by the saved version catalog, and three corrected UI files. No shared Jellyfin source is present. Concurrent working-tree Android/Jellyfin work was preserved.
- Changes: adaptive backgrounds and selected/Add/search foregrounds in both add-comics sheets; library status/edit and display controls split into rows, with fit-based stacking, scaled Archivo control text, 44pt minimum targets and column accessibility labels/value/selected traits. [Scoped diff](iosApp/build/replacement-20261107/ui-fixes.patch).
- Reconstruct using `git archive` of the base and overlay [candidate-inputs.tar.gz](iosApp/build/replacement-20261107/candidate-inputs.tar.gz). [Manifest](iosApp/build/replacement-20261107/candidate-inputs.json) contains 114 files; SHA-256 `79ea8662b5e78d6b2364b016208a5d15261b0c346480dcc2a554eea9f4f160e0`. All inputs still matched after builds. Private `Local.xcconfig` and SDK machine configuration are deliberately excluded; reuse the existing private signing setup. Generated project/workspace/Pods remain uncommitted.
- Version/build `1.0/2026110601` is retained for this local replacement, identified by the hashes below. No claim that the number is unused in App Store Connect.
- [Release archive](iosApp/build/replacement-20261107/Leaf-2026110601.xcarchive), [development IPA](iosApp/build/replacement-20261107/export/Leaf.ipa), [archive/export log](iosApp/build/replacement-20261107/archive-export.log), [inspection](iosApp/build/replacement-20261107/artifact-inspection.json). IPA SHA-256 `569487682b57c780d2d453c16a1fcee4cb35898e891339412966e1c54197a19c` (12,676,079 bytes); app executable SHA-256 `9d894dc16f14a6dc84dc89d1ddad74ce15709bcbe6fcca86df05a84300b742dd`.
- Signature verification passed; existing development profile has `get-task-allow=true`. No model, StoreKit fixture, test bundle or comic/PDF fixture in app/IPA. English and es-MX present; `ITSAppUsesNonExemptEncryption` absent; TensorFlowLite runtime retained. This is development signing, not distribution validation.

### Verification and remaining limits

| Check | Outcome | Evidence / limits |
| --- | --- | --- |
| Scoped shared JVM suite and frameworks | PASS | 105 tests, 17 suites, zero failures/errors/skips; both device/simulator frameworks built. [Summary](iosApp/build/replacement-20261107/shared-test-summary.json) |
| Release simulator arm64 and signed archive/export | PASS | Exact scoped source, retained command scripts/logs. Initial universal simulator attempt failed x86_64 linking; corrected explicit arm64 build succeeded. |
| Native regression rerun | BLOCKED, no new pass | Test bundle compiled but physical runner launch failed Pseudo Terminal Setup Error / Operation not permitted. Terminal-backed retry denied at openpty. Prior 85 native passes are historical, not this rerun. |
| Selection sheets | PASS for tested states | Both sheets Light/Dark/AMOLED selected/unselected; list live System switch; search/clear/Add/empty behavior. Full cross-product matrix and physical-sheet/spoken VoiceOver checks remain open. |
| Compact library controls | PASS for tested states | Empty/populated with EDIT, standard/AX5, English/es-MX, edit mode and selection interactions; iPad orientation/large-text samples. All search/list state combinations not exhausted. |
| Reader resume and backup UI | PASS, controlled data | Page 2→3 survives terminate/relaunch/reopen. Native Files backup export/restore preview/Apply Most Recent restored same comic/list/preferences. Not divergent restore acceptance. |
| Import and offline matrix | PARTIAL/BLOCKED | Picker opens; controlled-container fixture writes denied. No CBZ/CBR/PDF Files import pass. Seeded clone reading does not substitute. Forced-network-disconnected reading and SMB adverse scenarios unverified. |
| Rotation | PARTIAL | iPad portrait/landscape passes. Duo reports landscape while app remains portrait; unresolved runtime/app distinction. |
| Additional usability findings | OPEN | AMOLED custom control lacks Switch/Button/value semantics; Spanish compact reader top title/page status squeezes to ~1.3pt. Broader fixed-size typography remains separate work. |

[Detailed device report](iosApp/build/replacement-20261107/device-qa/RESULTS.md) includes 30 named screenshot/hierarchy pairs, controlled fixture provenance, limits and new defects. Main engineer reviewed corrected populated-header and light/dark sheet screenshots. Sampled rendered list-row contrast: light primary 14.97:1 / secondary 5.79:1; dark primary 15.53:1 / secondary 5.92:1, screenshot RGB interpreted as sRGB without ICC conversion. This is bounded evidence, not app-wide accessibility certification.

Initial build failures and their resolutions are retained: external Gradle stop command; missing historical Android build configuration in the narrow source overlay; simulator x86_64 request. Device fixture writes were denied after stalling; supported clones of prior controlled simulators enabled populated checks without modifying originals. Created QA sessions were ended and runtimes shut down.

Existing account, distribution, controlled previous-TestFlight upgrade, Spanish fluent review, licensing/export, privacy-publication and store approval gates remain open. The prior approved TestFlight 1.0 (2026110501) is the known preferred upgrade baseline, subject to availability; no downgrade or TestFlight upload was performed. Coordination and subsequent decisions remain in local `docs/IOS_ENG_NOTES.md`.

- 2026-11-07 | iOS engineer (Codex) | VERIFIED: Exact archived replacement Release app installed in place on the connected iPhone and launched successfully via devicectl. No uninstall/data reset. This establishes development install/launch, not controlled previous-TestFlight migration or physical-sheet acceptance. Extracted exported IPA also passed `codesign --verify --deep --strict`.


## 8. iOS follow-up local candidate — 7 November 2026

The two defects observed in section 7 were corrected and verified on the scoped Release simulator app: Spanish reader title/page status now occupy their own row; AMOLED exposes a native Toggle accessibility representation. Prior library/sheet fixes are retained. Evidence directory: `iosApp/build/followup-20261107/`; [source scope](iosApp/build/followup-20261107/candidate-scope.json), [manifest](iosApp/build/followup-20261107/candidate-inputs.json), reconstructable `candidate-inputs.tar.gz`, and [two-file diff](iosApp/build/followup-20261107/followup-ui.patch).

Base remains `a0780e8a650003f0014d021c98acf4cf32438165` plus 114 preserved inputs, including the previously explained historical Android Gradle build-support exception. Jellyfin is excluded. All isolated inputs still match after archive. Version 1.0/build 2026110601 is another local replacement, not a unique uploaded build or distribution clearance.

- Xcode workspace build and isolated arm64 Release simulator build passed. Signed archive and development IPA export passed.
- IPA SHA-256: `4f4bfffd0e6ae6599dcceabf91c8931ba979caed789b2f3a02b4503a183beb6c`; executable SHA-256: `6e7120c4d82c0fc66017789c70abaa25cd0e6dcbf1076826fa9d546773f30cdb`; manifest SHA-256: `ce218268cade62d4336b200abff48fbdcdf47d5acd1a799ee564786ad193139f`.
- [Artifact inspection](iosApp/build/followup-20261107/artifact-inspection.json): archive and extracted IPA strict signatures pass; no model/StoreKit fixture/test/comic/PDF resources; en and es-MX present; development profile; encryption declaration still unset.
- Exact archived app installed in place and launched on the physical iPhone, preserving data. Native test retry is ongoing and may temporarily replace that app; final installed identity will be recorded in engineering notes.
- Runtime targeted PASS: Spanish compact title/page status at standard and AX5 settings, and AMOLED Switch value 0→1 with theme response. Main engineer reviewed screenshots 02 and 04. This is accessibility hierarchy evidence, not spoken VoiceOver approval.
- Native test reporting discrepancy: Xcode MCP returned an 85-pass result list, but its new xcresult and console show simulator launch failure (Invalid connectionUUID), zero tests executed. The fresh xcresult governs; **no pass is attributed to that attempt**. Retry pending. Previous 105 shared passes apply to unchanged scoped shared inputs; no shared code was changed in this follow-up.

Release gates remain open. All 305 Spanish review approvals are still pending; reviewer availability is already known. Published privacy policy was rechecked and still has the 5 November bundled-model wording without tips; existing corrected local draft remains unpublished. Read-only host Chrome discovery confirms App Store Connect tabs exist, but page inspection is blocked by disabled Apple Events JavaScript; banking/agreement/tax/product/storefront statuses remain unobserved. Existing encryption/licensing/territory decisions and listing/privacy/screenshot approvals are not inferred. No account mutation, legal declaration, upload, publication or real-money purchase occurred. Final verification results and questions are maintained in [IOS_ENG_NOTES.md](docs/IOS_ENG_NOTES.md).


### Follow-up final verification

Fresh physical-phone native xcresult confirms **85 passed, 0 failed, 0 skipped**. Retained initial84/85 StoreKit unfinished-fixture precondition failure, isolated1/1 retry and final85/85 run; no code/test edits were made between them. This is a possible intermittent fixture issue, not a diagnosed fix. Native tests used the current workspace with all114manifest inputs identical but additional shared Jellyfin sources outside the manifest; the archived Release remains the isolated source. See `native-final-summary.json`, `phone-first.xcresult`, `recovery-retry.xcresult`, `phone-final.xcresult`, `workspace-input-comparison.json`.

Real native Files picker import and reader rendering passed for small synthetic CBZ, converted CBR and PDF on the scoped Release simulator. Evidence: [device QA results](iosApp/build/followup-20261107/device-qa/RESULTS.md). Local fixture servers stopped before imports; no user data changed. Forced-offline and spoken VoiceOver remain unverified. A same-basename replacement briefly retained stale library page-count metadata until reader open; tracked in engineering notes. The exact archived Release was restored in place on the physical iPhone after testing and launched successfully (`phone-final-install.log`, `phone-final-launch.log`). Remaining human/account/legal/publication gates above are unchanged.


## Internal iOS build cut — 8 November 2026

Fresh local Release archive and development IPA: **1.0 (2026110801)**. Evidence directory `iosApp/build/internal-20261108/` contains source overlay/manifest, reconstruction notes, exact archive/export script, logs and `verification.json`. Source is the preserved isolated follow-up candidate: base `a0780e8` plus 114 manifest inputs (SHA256 `ce218268cade62d4336b200abff48fbdcdf47d5acd1a799ee564786ad193139f`), including the historical Android Gradle build-support exception. Command-line `CURRENT_PROJECT_VERSION=2026110801` changes build metadata only. Current main `5b380e8` is not asserted as the exact source; later shared Jellyfin is excluded.

Fresh verification: archive/export succeeded; archive and extracted IPA passed strict deep signature verification; no model/StoreKit/comic/PDF fixture resources; English/es-MX present; encryption declaration absent; no source-manifest drift. Existing development profile, eight provisioned devices, expires 2027-10-31. IPA SHA256 `ffcd9122dcbeae46a29346ed566d02577c12bf07a88cf0fd1dc6bb57392c5355` at `iosApp/build/internal-20261108/export/Leaf.ipa`.

No device install or runtime tests performed for this new binary. Earlier shared/native/targeted QA retains its recorded scope and intermittency qualifications. Not an App Store distribution artifact, TestFlight upload or build-number availability claim. Existing metadata-refresh, device/accessibility, Spanish human review, account/product/storefront, licensing/export and public-content gates remain open. No commit, push or upload.


## Authorized TestFlight upload — 8 November 2026 UTC

Bryan explicitly requested uploading the new candidate to TestFlight. Existing Xcode account/signing assets successfully produced an App Store distribution export for **Leaf 1.0 (2026110801)**. Strict signature verification passed, `get-task-allow=false`, no device-limited provisioning list. Pre-upload distribution export SHA256 `95250c1a69b2eb0d7bd0500a275e12f02f9f9046e5f70861d44ebdc82773a977`; upload used the same archive via Xcode's upload export (do not assume its separately generated signed package has identical bytes).

**Verified upload result:** `Uploading “Leaf.ipa” is complete`, `Uploaded package is processing`, `Upload succeeded`, `Uploaded Leaf`, exit 0. See `iosApp/build/internal-20261108/testflight-upload.log`; final server response logged November 7 at 21:29 local EST (November 8 02:29 UTC). Distribution export/signature evidence and option plists are in that directory. No credentials created/replaced, provisioning-update flag used, new agreement accepted, or App Store publication performed.

**Still pending:** Apple processing completion, TestFlight visibility/tester eligibility and any required export-compliance resolution. Chrome's existing session failed authentication and redirected to `https://appstoreconnect.apple.com/login?targetUrl=%2Fapps&authResult=FAILED`; signed-in-looking stale page chrome was not accepted as current access. User must restore that session for browser verification. No tester-group assignment or beta review submission is claimed. Encryption declaration remains unset; no exemption answer supplied and no compliance gate waived. Prior source/test/QA qualifications remain in force. Do not re-upload this build merely because the browser session expired: Xcode explicitly confirmed receipt and processing.


## iOS Jellyfin M1 feature candidate — 8 November 2026

Bryan requested Jellyfin support. A separate, uncommitted working-tree implementation adds authenticated browsing and foreground CBZ/PDF downloads for offline reading. It includes the previously pending replacement fixes and uses current shared Jellyfin helpers. It is **not** part of uploaded build 1.0 (2026110801); that archive/source record remains unchanged. No new version, archive/export, upload or publication.

Evidence: `iosApp/build/jellyfin-20261108/` retains candidate input hashes/source state, review patch, fresh native results and UI-tool blockers. Native non-StoreKit-integration regression: **97/97 pass**, including nine Jellyfin tests and loopback HTTP fixtures. Xcode build succeeds; shared JVM task succeeds using cached 114-test results. Full-suite attempt stalled and was stopped; the prior StoreKit issue is not cleared. Actual Jellyfin-server compatibility, visual end-to-end acceptance, low-storage/interruption device matrix, minimum-OS HTTP behavior and fluent Spanish review remain pending. Progress sync is disabled. See the current Jellyfin checkpoint and IOS-Q-014 in local `docs/IOS_ENG_NOTES.md` for completed work, decisions and limitations. Encryption/legal/account/publication gates remain unchanged.


## iOS source checkpoint — 8 November 2026

The pending Jellyfin M1 implementation, inline download progress, readable/collision-safe filenames, replacement-reader fixes, localization and warning cleanup are being committed as a source checkpoint. This supersedes the earlier working-tree-only status; it does not change the source of uploaded build 2026110801 or constitute a new archive/upload.

Latest validation: BuildProject including test targets passed; 100/100 selected non-StoreKit-integration tests passed on the physical iPhone, independently verified from the fresh xcresult. The updated StoreKit recovery test separately passed 1/1. The StoreKit repeated-tip/delayed-approval integration failure remains unresolved; these are not full-suite clearance. Four configuration/package/toolchain warnings remain visible, and TensorFlowLiteSwift's deprecation may recur on a clean dependency build. Evidence remains local in `iosApp/build/warnings-20261108/`, with prior Jellyfin naming evidence in `iosApp/build/jellyfin-naming-20261108/`. Actual personal-server compatibility, visual/accessibility acceptance and all existing release gates retain their prior scope.


## iOS 1.0 next candidate preparation — 8 November 2026

**Not ready to upload; signed candidate creation is blocked by unverified build-number uniqueness.** Base commit `68b2a10be0de3878294a955947f51c46447f2ddc` plus scoped XML entity/landscape-cover fixes and regression tests. No commit created. Marketing 1.0 unchanged; proposed 2026110802 is **unassigned**, not an artifact identity. ASC read-only build-list request failed credential lookup; no empty-list/uniqueness claim. Existing uploaded 2026110801 remains unchanged.

Preserved current Jellyfin, consumable tips, localization, unrelated dirty work and model exclusion. Encryption declaration remains unset pending qualified SMB export review. No upload, submission, legal declaration or account mutation. Reconstruction/evidence directory: `iosApp/build/candidate-20261108/`; archive recipe fails closed without a valid ASC uniqueness response and only performs local archive/export. No new archive/export/signature/resource-inspection pass is claimed.

| Check | Fresh result | Evidence/limit |
|---|---|---|
| Shared JVM tests | 116 passed, 0 failures/errors/skips | shared-summary.json, shared-test-results, shared-jvm.log |
| BuildProject | PASS | Xcode Debug device build after parser/native-test changes |
| Final complete native suite | **108 passed, 1 failed, 0 skipped** | native-final.xcresult and native-final-summary.json; iPhone 11 Pro Max/iOS 27.0.1 |
| New XML regressions | PASS | Named/numeric entities, supplementary Unicode, CDATA, one-pass decoding, invalid references; native archive/persistence/grouping/manual-edit preservation |
| New cover regressions | Six cases PASS | Four proposed widths and portrait/landscape gutter pixels; device acceptance separately recorded in cover/RESULTS.md |
| StoreKit repeated-tip/delayed approval | **FAIL / cause not established** | First direct tip acknowledged, unfinished count remained 1 for five seconds; delayed-approval section not reached in failed run |
| StoreKit recovery | PASS in final full run | Does not clear repeated-purchase failure or live sandbox gates |
| ASC number / signed artifact | BLOCKED | Missing configured-profile credentials; no checked build assigned or artifact created |

Intermediate full 108/108 and isolated StoreKit 1/1 passes do not replace the final 108/109 failure. Detailed immediate failure, uncertain underlying cause and payment-lifecycle risk: `STOREKIT_INVESTIGATION.md`. Production StoreKit and test assertions are unchanged. Recommend no upload pending resolution/classification and remaining release gates.

Candidate listing comparison recorded in local `docs/IOS_APP_STORE_LISTING.md`: candidate includes optional personal-server Jellyfin foreground downloads, remote progress sync remains off. Draft description updated to 1687 characters. Screenshot source chooser matches new source scope, not the previously uploaded binary. Original archive metadata/landscape issues were reproduced; the old screenshot demo's manual name/page-order workarounds do not prove fixes. Preserve Pepper&Carrot/David Revoy/CC BY 4.0 attribution and modification disclosure. Final all-image visual QA, ASC slots/selected binary, Spanish review, live servers/storefront, controlled upgrade/device/accessibility, public-policy and encryption/licensing gates remain open. Coordination: local `docs/IOS_ENG_NOTES.md` IOS-Q-015/016/017 and IOS-02/03/04/05/11.


### Evidence checkpoint — 8 November 2026 | Codex

Final source overlay restored after controlled cover regression comparison. Base `68b2a10be0de3878294a955947f51c46447f2ddc`; 132-input manifest SHA-256 `479ff96297c3d8ab0fc7ef93479a290b9faa7871227ac795a19590d1925f5419`. Rechecked all inputs: no drift. Overlay archive SHA-256 `6603d70eed5c49f7ecf84f62383ef8afeaa8de6a5b8da39ccfc743054e18bb48`. See `iosApp/build/candidate-20261108/{candidate-inputs.json,candidate-inputs.tar.gz,candidate.patch,source-checksums.json,REPRODUCE.md}`. No new commit/build number is implied by this manifest. Scoped diff whitespace check passed.

Cover regression red/green verification: original image-only layout failed all six cases; restored fix passed all six on Duo simulator, with successful simulator BuildProject. Final source matches the source of the physical 108/109 native run. These selected passes do not clear StoreKit.

Listing visual sample personally inspected: `review/iphone/04-sources.jpg`, `review/iphone/01-library.jpg`, `review/duo/06-series-outer.jpg`. Source chooser visibly includes Files/SMB/Jellyfin; library/series show portrait-page demo covers and manually normalized “Pepper and Carrot”. Samples agree with the README's demo modifications and candidate feature scope. This is not a full 13-image visual signoff or final-binary acceptance. No screenshots changed.


### Final preparation result — 8 November 2026 | Codex

Final source additionally fixes the same landscape-image sizing bug in SeriesDetailView.swift and ReadingListDetailView.swift (nine cover sites total). The earlier source checkpoint is superseded: `intermediate-inputs-before-detail-fix.json` preserves manifest `479ff962…`; the final **132-input manifest SHA-256 is `ed3356618548b2bb003b03212fdf80b60da62934108457734c3fc4eccb17109f`**, overlay archive `3ae809b83afd34237137c32eefd8068fa2492804fcaf6b643f8be59b495ea8b5`, scoped patch `d314a1f802f329b28a868623e1dcfe9f97f76018318636c19526f2273df64178`. Base commit remains **68b2a10be0de3878294a955947f51c46447f2ddc**, plus seven scoped dirty source/test files. Source hashes rechecked after all builds/tests: no drift; diff whitespace check passes. The interim overlay archive was replaced by the final overlay; use the final checksums for reconstruction.

**Fresh exact-final full native run: 108/109, one failure, zero skips**, independently retained in `iosApp/build/candidate-20261108/native-exact-final.xcresult`, summary and console. Same StoreKit failure on first direct purchase, before Ask to Buy; all 100 unfinished-count samples equal 1. This is a second fresh full-run reproduction after the earlier intermediate green run. No root-cause/fix claim. Shared result remains 116/116; no shared changes since that run. Final affected simulator suites 26/26, and controlled cover regression red0/6 → green6/6. These counts are overlapping evidence, not additive totals.

**IOS-Q-015/016 bounded runtime evidence:** original unchanged archives with fresh filenames display Pepper&Carrot correctly in library and series-detail header. Duo outer library passes 2/3/4-column visual checks and neighboring-card navigation. Final series-detail landscape covers remain inside cards. Parent personally inspected final-series-detail-screenshot.png; device-interaction subagent reports the full bounded sequence in `cover/RESULTS.md`. Existing cached incorrect metadata remains unchanged and requires reimport/manual editing; manual user corrections and legitimate literal text are preserved. Inner display, fold/unfold, other devices and dedicated original-landscape reading-list detail visual check remain NOT RUN. Keep broader IOS-08/11 acceptance open.

**Signed candidate remains BLOCKED, not completed.** Marketing version **1.0** preserved; **2026110802 remains proposed/unassigned**, with no successful ASC uniqueness check. No new Release archive/IPA exists. Debug simulator evidence at the old default 2026110601 is not the requested candidate. `asc-number-check-status.json`, local-only archive recipe and REPRODUCE.md record the exact remaining steps. No response to IOS-Q-017 is assumed, and no permission for commit/upload is inferred. No encryption value added; all existing unrelated work, Jellyfin/tips/localization/model exclusion preserved.

Recommendation: **do not upload**. Next inputs/actions: restore ASC access or provide actual unused-number confirmation; create/inspect the signed candidate; resolve or evidence-based classify StoreKit finishing failure; complete remaining release gates. No commit, TestFlight upload or App Store submission performed. Final listing comparison and sampled screenshots remain recorded in local IOS_APP_STORE_LISTING.md; no listing publication or screenshot modification.


## Signed local candidate and StoreKit isolation — 8 November 2026 | Codex | VERIFIED

Bryan authorized continuing the next preparation steps. **IOS-Q-017 RESOLVED:** existing keychain-backed ASC access worked on retry without configuration/credential changes. Complete read-only iOS build list returned only 2026110501 and 2026110801 (VALID, no next page); exact 1.0/2026110802 query returned data[]/total0. Successful responses supersede the earlier credential block. Assigned **1.0 (2026110802)** for local QA; no upload or remote reservation.

**IOS-02/03/04 — new local artifact completed:** base commit **68b2a10be0de3878294a955947f51c46447f2ddc** plus the same seven scoped dirty source/test files. 132 source inputs unchanged; manifest includes the assigned version/build and has SHA-256 **526f1b97c4c6982c379041c2f7e8f4e59cd4d515462a676e9cd67521257d8766**. Reconstruct from `iosApp/build/candidate-20261108-2026110802/candidate-inputs.tar.gz`; exact commands, locks, tooling, hashes and limitations in its README.md. No commit created; final distribution-source approval remains distinct from this reproducible local deliverable.

Signed Release archive, development export and App Store distribution **local export PASS**. Development IPA SHA-256 `8bc60ac0790cce7f881e49b31315afab4b6974856238eed16c4784830aee7fcd`; distribution IPA SHA-256 `1bc192dc0bc7dc5c94ae02164ac3482b364776afdc7d8b8676cee3ed4f6bd86b`. Strict deep signatures pass for archive and both extracted IPAs; English/es-MX present; no tflite/StoreKit/test/comic/PDF fixtures; encryption declaration absent. Development get-task-allow=true/device-limited; distribution get-task-allow=false/no device list. Temporary diagnostic strings absent. Xcode 27.1/27A9275, XcodeGen 2.46.0, CocoaPods 1.17.0; app minimum remains iOS 16. Source hashes show no drift. Initial sandbox archive workspace-access failure and successful retry both retained; dependency/toolchain/source warnings recorded, not suppressed.

**IOS-06 PASS for this artifact:** exact archived Release app installed in place and launched on connected iPhone 11 Pro Max/iOS 27.0.1; device-install.json/device-launch.json. No uninstall/erase. This replaces the Debug test build, not a controlled previous-TestFlight upgrade; IOS-07 and broader UI matrix remain open. No new post-install purchases or server connections performed.

**IOS-05 remains OPEN/FAIL; diagnosis improved.** Uninstrumented current full suite 108/109, one failure, zero skips. Two full diagnostic runs with console logging passed 109/109, but removing logging immediately restored the failure. In-memory instrumentation shows transaction 143.finish returned in about 4.2 ms, cancellation=false, yet verified transaction 143 remained unfinished after all 100 samples over 5 seconds in environment Xcode. A temporary direct Product.purchase→verified transaction.finish test, bypassing TipStore/StoreKitTipClient, reproduced the same failure (diagnostic run 109/110; original integration passed afterward). Evidence now localizes the observed inconsistency below Leaf's tip-state machinery in the Xcode StoreKit environment. Precise framework/service cause remains unproven; production/sandbox implications are unknown. No speculative production delay/retry or weakened assertion added. All temporary instrumentation/reproducer code restored; original 109-test suite and assertions remain. Shared 116/116 evidence retained for unchanged source. [Detailed investigation](iosApp/build/storekit-followup-20261108/INVESTIGATION.md), unlogged/memory-trace/direct-storekit xcresults and diagnostic patches retained.

**IOS-13 newly VERIFIED partial account evidence:** complete read-only IAP list contains only `net.digitalbc.leafreader.tip.small`, type CONSUMABLE, state MISSING_METADATA. Medium/large IDs absent; this is a separate product-readiness blocker, not the cause of the local .storekit fixture failure. No IAP creation, edits, prices, agreements or financial details changed. Approved USD base amounts stay $1.99/$4.99/$9.99. **IOS-Q-018 — OPEN:** production catalog completion needs explicit scoped authorization before creating missing consumables/updating small-tip metadata; inspect exact existing fields/localizations/review assets first, avoid duplicate IDs, retain approved amounts. Live US/Mexico sandbox remains untested; business readiness not inferred from API access.

Updated supporting records: [RELEASE_COMPLIANCE.md](RELEASE_COMPLIANCE.md), [IOS_TIPS_IMPLEMENTATION.md](docs/IOS_TIPS_IMPLEMENTATION.md) and [IOS_APP_STORE_LISTING.md](docs/IOS_APP_STORE_LISTING.md). Listing now identifies this signed local Jellyfin-inclusive candidate separately from uploaded 2026110801; prior screenshot/sample/Duo qualifications unchanged. The earlier statements that no signed candidate exists are historical and superseded by this entry.

**Recommendation: local QA only; do not upload yet.** Resolve production-tip catalog/live sandbox acceptance, retain StoreKit local failure qualification, complete remaining device/upgrade/Spanish/accessibility/public-policy and SMB export/licensing gates. Encryption remains unset. No commit, TestFlight upload, App Store submission, product mutation or unrelated cleanup performed.


## IAP verification attempt — 9 November 2026 UTC | Codex | PARTIAL/BLOCKED

Bryan approved the verify-and-sandbox plan using existing **1.0 (2026110802)**, without rebuilding/uploading. This supersedes the earlier notes-only limit only for that bounded verification. Bryan reports all IAP setup completed: the old small-only/MISSING_METADATA snapshot is historical, not a reason to recreate products. Current product verification could not complete: app lookup resolved Leaf to numeric ASC app ID 6830333334, but subsequent product reads repeatedly failed existing CLI credential resolution. Failed/empty command output is not evidence of an empty catalog. No credentials requested or changed; Bryan clarified no OARealm information is needed.

Successful read-only sandbox list returned zero testers (total 0, no next page) in the configured API team. Owner was asked to create/sign into appropriate US and Mexico sandbox testers directly, without sharing credentials; completion is not assumed. Business web query had no cached session: Paid Apps Agreement, banking and tax remain individually unverified. No agreement/account change performed.

Delegated device verification confirms the physical iPhone 11 Pro Max has developer-built **1.0 (2026110802)** installed. The interaction tool rejects that physical device and offers only simulators. Thus real catalog prices, purchase environment, all purchase/repeat/cancel/recovery/deferred cases and final-candidate review screenshot remain NOT RUN/NOT CAPTURED. No simulator substitution or runtime pass claimed. Next execution requires working authorized ASC reads plus owner-assisted physical sandbox sign-in/interaction or restored device UI tooling. UI success will not by itself prove an empty unfinished-transaction queue.

IOS-Q-018 remains pending verification; IOS-13/14 remain open. Original native 108/109 and shared 116/116 evidence unchanged, no suite rerun. Candidate source/artifact identities unchanged; encryption unset. No app/source change, build, installation, purchase, remote mutation, commit or upload. Recommendation remains hold upload.

[Verification matrix and resume steps](iosApp/build/iap-sandbox-20261109/RESULTS.md).


## IAP catalog verified on retry — 9 November 2026 UTC | Codex | VERIFIED / SANDBOX PENDING

Read-only direct ASC API requests succeeded without credential/configuration changes after the higher-level command failed. This supersedes the preceding product-read block and the older small-only catalog snapshot. Full product response contains exactly three entries, no next page, matching all intended `net.digitalbc.leafreader.tip.*` IDs and CONSUMABLE type.

| Product | Verified current US customer price | Availability | ASC state | Localizations | Review screenshot |
| --- | --- | --- | --- | --- | --- |
| Small tip | USD 1.99 | USA, CAN, MEX, GBR | MISSING_METADATA | en-US only | None attached |
| Medium tip | USD 4.99 | USA, CAN, MEX, GBR | MISSING_METADATA | en-US only | None attached |
| Large tip | USD 9.99 | USA, CAN, MEX, GBR | MISSING_METADATA | en-US only | None attached |

All three have review notes describing the actual menu path, optional consumable support, no unlocks and no Leaf account requirement. Price schedules have a single current US manual price with no start/end boundary. US base territory independently returned for medium/large; small's separate base-territory request failed, but its US manual customer price is verified. New-territory automatic availability is false for all three. Configured territory availability does not establish localized currency retrieval on a device. Do not change the owner's English copy or selected territories just to match older draft recommendations.

**Concrete remaining work:** capture the real tip picker on the existing candidate and attach the approved review screenshot to each product; confirm Apple's resulting metadata state. Missing screenshots are verified gaps, not proof that they are the only reason for MISSING_METADATA. Spanish product localizations are absent: existing proposed names are Propina pequeña / Propina mediana / Propina grande, with description “Apoya el desarrollo de Leaf. No desbloquea nada.” Retain these as pending fluent review rather than publish unapproved copy. No remote corrections made under the verification plan; it requires review of concrete corrections first.

Sandbox list was independently retried and again returned zero testers, total 0/no next page in the configured API team. Tester creation via the available web command requires an authenticated web session and a tester email/password; neither was supplied or fabricated. No OARealm information or credentials requested. Owner-assisted next step is to open Library → menu → Support Leaf → Leave a Tip and report the visible state; an asynchronous prompt is pending, with no response assumed. Only confirm a purchase after the Apple UI explicitly identifies Sandbox. No real catalog UI, purchase, repeat/cancel/recovery/deferred scenario or final review screenshot has yet been verified. Business agreement/banking/tax and physical UI tooling blocks remain as previously recorded.

Candidate remains 1.0 (2026110802); historical native 108/109 and shared 116/116 unchanged. No build, install, purchase, account/product mutation, commit or upload. Encryption unset. Preserve concurrent Android-led visual changes: they are newer unbuilt working-tree edits and are not in this signed candidate. IOS-Q-018 is now partial VERIFIED for IDs/types/USD prices/territories/localizations/missing screenshots, rather than wholly blocked by API access; IOS-13/14 and upload recommendation remain open/hold.


## Authorized TestFlight upload — 9 November 2026 | Codex | UPLOAD VERIFIED

Bryan requested getting a TestFlight build up after the current UI work. This authorizes the new beta upload and supersedes earlier hold/no-upload recommendations for this action; it does not close remaining acceptance or legal/account gates. **Leaf 1.0 (2026110901)** archived and uploaded successfully using existing Xcode signing/account assets. Apple acknowledged “Uploading Leaf.ipa is complete”, “Uploaded package is processing” and “Upload succeeded” at **2026-11-09 13:24:59 UTC**; xcodebuild exited 0. No App Store publication, beta review submission, tester invitation or manual notification performed.

Source: base `68b2a10be0de3878294a955947f51c46447f2ddc` plus preserved working-tree changes, including optional Jellyfin, metadata/cover corrections, Android-led library alignment and IOS-UI-A01–A04. No source commit or version-default edit; explicit archive overrides set 1.0/2026110901. 132 saved source inputs showed no drift after archive/export/upload. Manifest SHA-256 `ec3bdbce6a9a34c3b0bd1cc6b50b8de3910e521b4a6a237b0d3f6d3b2f7a05f9`. Complete ASC list and exact-number query verified 2026110901 unused before archiving. Earlier 2026110802 remained local and was not uploaded.

Validation: fresh selected native run on physical iPhone 11 Pro Max/iOS 27.0.1 **54/54 passed**, zero failed/skipped, independently read from xcresult. Includes appearance, reader/Metal, library, metadata and localization tests. This is not a full-suite pass or manual VoiceOver/touch acceptance; historical StoreKit finishing failure remains qualified. Shared JVM invocation succeeded UP-TO-DATE with **116 cached cases**, not a new execution. Owner-reported sandbox/UI success remains recorded separately. Current accessibility/theme/device matrix remains open.

Release archive and App Store distribution export PASS; strict deep archive/export signatures PASS. Distribution get-task-allow=false, no provisioned-device list; en/es-MX present; model, StoreKit, test and comic/PDF fixtures absent. Local distribution IPA SHA-256 `633a9bfc6ee722840d2afba18f0d3f7337e91b26decf87c82f4149d7fe777619` (15437813 bytes). Upload was a separate export from the same archive; this checksum is not asserted as the transmitted package's checksum. Initial sandbox workspace-access failure retained, successful authorized retry retained. No credentials/signing identity replaced and no provisioning-update flag used.

Export compliance: declaration key remains absent. Read-only ASC query returned no app encryption declarations, and older uploaded 2026110801 still reports MISSING_EXPORT_COMPLIANCE. Preserve Bryan's reported questionnaire completion; this does not establish linkage to the new build. No exemption/non-exempt answer, France availability, legal classification or declaration mutation performed. First post-upload exact-build query returned no rows while Xcode reported processing; that is not an upload failure and is not grounds to re-upload. Final processing/tester eligibility is recorded by subsequent status evidence.

Evidence: `iosApp/build/testflight-20261109/` contains README/reproduction commands, source overlay/manifest, ASC queries, archive/export/upload logs, archive/distribution inspections, native xcresult/summary and cached shared results. [Evidence README](iosApp/build/testflight-20261109/README.md), [upload log](iosApp/build/testflight-20261109/testflight-upload.log), [native results](iosApp/build/testflight-20261109/native-tests.json). Existing source, release gates and historical evidence preserved.

Final status check (2026-11-09T13:26:23.852759+00:00): second exact-build ASC query still returned no rows; Xcode upload receipt remains VERIFIED and Apple processing/TestFlight visibility remains PENDING. Do not re-upload 2026110901. Evidence: asc-build-status.json. No new build-specific export-compliance or tester-eligibility result available yet.


## Local iOS release workflow tooling — 2026-11-10T20:39:15.000763+00:00

Added [the release workflow](docs/IOS_RELEASE.md), repository `deploy-ios` skill and `scripts/release_ios.py` with isolated source snapshots, fresh full-test gates, signed archive/export inspection and a separate exact-IPA upload step. Setup validation: **27/27 offline tooling tests pass** (mocked Xcode/Apple operations), disposable current-source XcodeGen generation passes, scoped whitespace check passes. No app runtime/StoreKit pass is claimed. [Verification evidence](iosApp/build/release-workflow-20261110/verification.json), [test log](iosApp/build/release-workflow-20261110/tests.log).

Read-only Apple state verified on November 10: **1.0 (2026110902)** is the selected READY_FOR_DISTRIBUTION/READY_FOR_SALE version, build VALID and `usesNonExemptEncryption=false`. This supersedes missing-export snapshots for that exact build only. Future export classification remains factual, scoped and authorized; source Info.plist declaration remains unset. Historical engineering/acceptance evidence and existing dirty changes are preserved. No actual release preparation, new build number, app compile/archive, upload, metadata/export mutation, submission, publication, credential change, commit or push occurred during this tooling task.


## Local Android release workflow tooling — 2026-11-10T21:08:25.177116+00:00

Added [the Android release workflow](docs/ANDROID_RELEASE.md), extended the existing repository/personal `deploy-play` skill, and added `scripts/release_android.py` with isolated timestamped source attempts, fresh full-test gates, version-code/certificate evidence, signed artifact inspection and a local testing-upload preview. GitHub tag publication is preserved as an explicit separate mode. No production/open-track initial upload preview is permitted; promote the same tested Play artifact only after separate authorization and post-Play acceptance.

**Setup verification:** 30/30 Android offline helper tests and 27/27 existing iOS helper tests pass (57/57 combined; mocked Gradle/signing/remote operations). YAML frontmatter/interface checks and repository whitespace checks pass. The standard skill validator could not run because available Python interpreters lack PyYAML; no dependency installed. [Verification record](app/build/release-workflow-20261110/verification.json), [test log](app/build/release-workflow-20261110/tests.log). No actual Android compilation, signing or final-binary acceptance is claimed.

**Release blockers remain:** source version still 0.3.7/30700; current schema-5 revision-column upgrade defect remains unresolved and blocks helper builds. Pre-upload gates require populated v4/v5 Room fixtures, export/restore and local minified checks. Play-app-signing in-place upgrade acceptance is recorded separately after the authorized testing upload, avoiding a circular prerequisite. Live Play state/certificate/track, official bundletool, existing signing access, device checks and post-Play eligibility/feedback still need verification for a real release. No Play CLI configured; authorized remote steps are browser-driven. Owner-reported Alpha 0.3.7 is not freshly verified by this tooling work.

No real candidate, version increment, app build, signing, credential change, upload, metadata/declaration/settings change, submission, rollout, commit, tag, push or CI setting change performed. Historical acceptance/checklist evidence and unrelated changes remain preserved.

## Android schema-6 migration follow-through — 2026-11-10

Codex personally implemented and tested working-tree changes on da14e0fdcd01bbf9a8cebc9b4c73de000caf1380 (app version remains 0.3.7/30700). Room version 6 registers a preserving 5→6 migration for both v5 layouts: missing revision receives default 1; existing values remain intact. Five normal Room upgrade tests cover populated v4, earlier v5 pending progress/read/unread and revised v5 revision 9, with database close/reopen, comic/link/list preservation, pending row equality, outbox writes and cascade deletion.

Fresh commands passed using --rerun-tasks --no-build-cache: :shared:jvmTest --stacktrace (4 tasks executed), then :app:testFullDebugUnitTest :app:testFdroidDebugUnitTest --stacktrace (54 tasks executed). git diff --check passed. Evidence: shared/build/test-results/jvmTest/ and app/build/test-results/{testFullDebugUnitTest,testFdroidDebugUnitTest}/; IDE conversation shell-2/shell-3 logs. Per-suite XML totals were not independently inspected.

The preceding unresolved migration finding is superseded for this working-tree source. These are local regression results, not signed/minified or Play-delivered upgrade acceptance; other release and sync gates remain open. No release artifact, commit, signing or store action. See [Android engineering notes](docs/ANDROID_ENG_NOTES.md) for coordination and remaining scope.

## Android Jellyfin sync regression evidence — 2026-11-10

Codex added nine/replaced three sync regression cases in LibraryRepositoryJellyfinTest.kt on the existing working tree (base da14e0fdcd01bbf9a8cebc9b4c73de000caf1380; app 0.3.7/30700). Request barriers cover ordinary saves, explicit read/unread and bulk/per-comic serialization, asserting revisions, HTTP payloads/order and simulated final state. Production browse/disconnect and reconnect/account-switch tests observe actual OkHttp call cancellation and retained old-account intent while delivering only the new account's item. Disk close/reopen retains local progress and pending unread intent; injected SQLite outbox-insert failure rolls back page/slot with no network request. A failed played-status request retains the row for a complete retry.

The focused suite passed. Fresh :shared:jvmTest and both :app:testFullDebugUnitTest/:app:testFdroidDebugUnitTest passed with --stacktrace --rerun-tasks --no-build-cache (4 and 54 tasks executed respectively). git diff --check passed. Evidence: shared/build/test-results/jvmTest/, app/build/test-results/{testFullDebugUnitTest,testFdroidDebugUnitTest}/ and IDE shell-9/10/11 logs. XML totals not independently inspected.

No production sync edit was needed. These hermetic regressions address the specified automated gaps, not real Jellyfin interoperability, OS process-death acceptance, signed/minified device checks, backup or Play delivery. Cancellation cannot retract already accepted server writes. All broader release gates and historical evidence remain preserved. See [Android engineering notes](docs/ANDROID_ENG_NOTES.md) for the detailed scope. No configured live server, release artifact, signing, commit or store action.

## Physical Android minified smoke checks — 2026-11-10

Codex built current dirty source on da14e0fdcd01bbf9a8cebc9b4c73de000caf1380 (0.3.7/30700 fullRelease), with R8/resource shrinking and the existing development signing identity matching the connected Samsung SM-F976U1 RFGL62WDESD. Signature verified and adb install -r succeeded without data clearing. APK SHA-256: 1b42ece6bf4e68dc8b9b28f784c7f36dd83f21d7462fca897d13b4524bc98af3; non-debuggable release configuration, development certificate, not upload/Play signing.

Bounded observed passes: existing visible library/progress/preferences retained; native Files import and rendering of local three-page CBZ; page turn; airplane-mode force-stop/relaunch and same-page reading; landscape spread and return to exact single-page position. Connectivity, original rotation lock and landscape layout restored. [Detailed local device report](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md) records artifact identity, commands, observations, retained QA fixture and remaining checks. Build/install logs and UI screenshots remain in the IDE conversation.

This is partial C-02/C-03 evidence, not completion of those gates or J-06–J-08. Live Jellyfin verification awaits Q-017's designated comic/account; real-content/large archives, PDF/CBR, import failures, fold/unfold, accessibility, backup and final/Play-signed acceptance remain open. No deliberate live progress/browse/import test, version bump, commit, release tag, historical-artifact replacement or store action. Coordination recorded in [Android notes](docs/ANDROID_ENG_NOTES.md).

## Live King Spawn PDF/progress verification — 2026-11-10 local / 2026-11-11 UTC

Q-017 is answered: Bryan logged in on the phone and designated King Spawn - Vol. 01 for the described live tests. Restored the unchanged minified fullRelease QA APK after an IDE debug run, with adb install -r (shell-46 Success); saved session/library preserved. Same development-signed, non-debuggable 0.3.7/30700 artifact SHA-256 1b42ece6bf4e68dc8b9b28f784c7f36dd83f21d7462fca897d13b4524bc98af3; Samsung RFGL62WDESD unfolded at 2256×2504.

Personally verified: selected item downloaded as a 172-page PDF (~1.4 GB); cover/next page/page 12 rendered; offline page 12 survived force-stop/relaunch; fresh Jellyfin folder listing reported PAGE 12 after reconnect without reopening/saving the reader. Explicit read produced a fresh READ badge; explicit unread removed READ/PAGE on another fresh server listing and reset local progress. King Spawn remains downloaded and unread at the beginning. Airplane mode is confirmed disabled; the user remains logged in.

[Detailed device evidence](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md) records scope, steps and UI/source tracing. Readback is a fresh production response displayed by Leaf, not a second-client/raw JSON verification. This supersedes the pending designated-item/live-flow statements above only at this tested scope. Account switch/in-flight cancellation on a live server, failures, CBR, backup, fold-transition continuity/accessibility, exact final signing and Play upgrade remain open; do not close whole J-06–J-08/C-01–C-04 gates.

A separate title-normalization defect was observed and recorded: local import drops “. 01” from the extensionless server title because LibraryRepository strips the last period suffix unconditionally. No source fix/rebuild was mixed into these results. [Android notes](docs/ANDROID_ENG_NOTES.md) retain the finding and Q-017 resolution. No new build, key change, commit, tag or store action.

## Android Jellyfin title fix — 2026-11-11 UTC

Working-tree source now removes only a case-insensitive suffix matching the Jellyfin item's known file extension. Numeric/dotted extensionless titles remain intact. A four-case mock download/import regression test verifies persisted titles and retained remote names; existing import/link/re-import coverage is preserved.

Personally run :shared:jvmTest --stacktrace succeeded with up-to-date results. Both :app:testFullDebugUnitTest and :app:testFdroidDebugUnitTest --stacktrace executed successfully (54 tasks: 15 executed, 3 cached, 36 up-to-date); git diff --check passed. Evidence: IDE shell-55/shell-56 logs and Android test-result directories. No independently inspected suite totals claimed.

This fixes future imports only; existing saved titles are not rewritten. No new release APK, deployment or live repair was performed. The preceding live-device evidence still applies to its recorded older APK, not this source change. See [Android notes](docs/ANDROID_ENG_NOTES.md) for the bounded completion record; broader acceptance remains open.

## Populated Android backup acceptance — 2026-11-11 UTC: FAIL

New local QA build/install: minified, non-debuggable development-signed fullRelease 0.3.7/30700, APK SHA-256 c89bf6971af700047cd7e5903ff53b2cfd88360e649b860b2eb45c84515e71d4; shell-62 build and shell-65 in-place install succeeded. Source includes the preceding title fix. This is separate from the older hash's smoke/live results and is not upload/Play-signed acceptance.

Native export/OVERWRITE restore of 22 comics, 2 lists and 7 preferences recovered the changed synthetic progress, grid and deleted temporary list across restart. Actual export fields/data excluded credentials/server IDs/remote links and URL/host/sandbox-path strings. Full re-export comparison nevertheless FAILED: two Blue Book - Vol entries with identical title and 140 pages reused the same local target, overwriting its original progress/timestamp. Preview reported both as matched. C-04 remains blocked/open by ambiguous matching; source not fixed in this QA pass.

The affected local progress was repaired from the untouched baseline via a single-record native restore. A third export matched the complete baseline except export timestamp; temporary QA list then removed, original Cool stuff/4 items retained. [Detailed local report](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md) and [Android notes](docs/ANDROID_ENG_NOTES.md) preserve exact observations/repair and next work: safe progress/list identity matching, ambiguity reporting, accurate preview and duplicate-record regressions, then repeat device acceptance. Interruption/missing-file/cross-device and other strategies remain pending. No commit/upload/store action.

## Backup duplicate-matching fix verified — 2026-11-11 UTC

The preceding data-loss blocker is fixed at the tested scope: ambiguous local matches and competing payload records are skipped; preview/apply share the matcher; ambiguous list references are skipped without removing existing membership. Preview reports skipped counts. v1 export format/privacy exclusions remain unchanged. Indistinguishable records are intentionally not restored automatically.

Shared/full/fdroid test command passed (shell-75; shared retained); both Android BackupManagerTest XML suites contain 13 passing cases each, including three new regression tests. New minified fullRelease QA APK SHA-256 60beb0c4a4f1e3152207271116a933c12b16eabafb0a911e98fd04b9e8f97c88 built successfully (shell-78), signature verified with existing development identity, and installed in place (shell-82), preserving data.

On Samsung RFGL62WDESD, same original 22-comic backup preview reported 20 safe matches/2 skipped comics/0 skipped list items. Native OVERWRITE restore, restart and re-export preserved both Blue Book records' distinct progress; entire portable snapshot matched baseline except export timestamp, including all comics, 7 settings and 2 lists. Grid 2→6 and recreated QA list verified. Temporary list removed afterward, original 4-item list retained; no repair required. [Device report](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md) and [Android notes](docs/ANDROID_ENG_NOTES.md) preserve evidence/history.

C-04 remains partial for interruption, missing-file/cross-device and device testing of other conflict strategies. This development-signed QA result is not final upload/Play acceptance. No commit/upload/store action.

## Backup failure/retry hardening — 2026-11-11 UTC

Reproduced and fixed preference changes surviving a failed DB restore. Preferences now commit together after successful Room transaction completion; preference-save failure reports that the library was restored and instructs retry. Added five tests for injected DB failure/rollback/reopen/retry, deterministic mid-write caller cancellation/reopen, missing library records/archive paths, truncated input and failed preference commit/retry.

Shared/full/fdroid verification succeeded (shell-90; shared retained); final complete Android suites rerun successfully after test-only additions/correction (shell-96). Personally inspected both final BackupManagerTest XML suites: 18 cases each, 0 failures/errors/skips. Minified development-key fullRelease build passed R8/release lint-vital (shell-93); output SHA-256 661aa24ee8274acfb73073afb6235ccf24b76c84e90fc2c166f254f0f54a45ed, version 0.3.7/30700. This new APK was not installed/device-tested; preceding phone acceptance remains bound to 60beb0c4... .

C-04 remains partial: coroutine cancellation and SQLite failure/reopen checks do not establish OS process-kill recovery. DB and preferences remain separate commits; a kill between them needs retry of the same backup. Missing-file tests confirm metadata-only/skip behavior, not archive-byte recovery or physical cross-device re-import. [Android notes](docs/ANDROID_ENG_NOTES.md) retain exact repro/test/build/limitation evidence and next work. No phone/server operation, install, commit/upload/store action.

## Durable backup recovery and re-import QA — 2026-11-11 UTC

Room schema 7 now journals only pending non-sensitive preferences in the same transaction as restored progress/lists. Startup completes that preference write before opening the library, clears it after success, and keeps retry available after failure. Recovery is idempotent, never replays reading progress, and cannot be overwritten by a competing restore. Populated v4/v5/v6 upgrades to v7 preserve prior records/revisions; export remains v1.

shell-100 shared/full/fdroid test command passed; per-flavor recovery (4) and migration (10) suites inspected with zero failures/errors/skips. Tests reproduce durable crash-boundary states through injected failure and DB/repository/settings recreation; no literal timed OS process kill is claimed. New development-signed minified fullRelease APK SHA-256 0357efbcaf7e95be47ba6f8807cc7c42322ccb7ef74451e7115671ec6d53a232 passed R8/lint-vital (shell-101), signature verification and in-place install (shell-105), version 0.3.7/30700.

Phone missing/re-import check passed using only the synthetic fixture: absent-comic restore skipped its progress/list item; native Files re-import plus reapplying the original backup recovered pg 2/3 and membership across restart. Fresh export matched all 22 comics, both lists and 7 preferences, excluding export timestamp and fixture's new dateAdded. Original list retained, temporary QA list removed, restored page rendered. Exact artifact/steps/comparison in [device report](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md) and [Android notes](docs/ANDROID_ENG_NOTES.md).

C-04 remains partial for literal timed OS-kill/storage-failure/UI recovery, actual cross-device transfer and other broader acceptance. This local QA build is not final upload/Play-signed acceptance. No commit/upload/store action.

## Real process-kill QA — 2026-11-11 UTC | two passes, Retry incomplete

Added a separate debug-based recoveryQa package/sandbox; no production-source fault hook or new dependency. QA artifact SHA-256 35275dbe922bed88964b8b7eacfeb71be352941b233e2513770b2f5fa3331c82 built/installed separately (shell-112/115). On Samsung RFGL62WDESD, literal force-stop/no-PID/relaunch tests passed at BOTH real commit boundaries: database committed/preferences unsaved and preferences saved/journal uncleared. Normal production startup recovered all7 preference values, retained page12/slot2 and list membership, and cleared the journal. Independent verifier performs no recovery. Exact PIDs/assertions in [device report](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md).

Real QA preference-write denial displayed the startup error dialog, but Retry completion was interrupted by device/foreground changes and the Fold disconnected. Permissions771 and temporary stay-awake0 were restored and read back before disconnect. Isolated QA app remains installed pending reconnect/cleanup; production app/data were not changed. Q-018 in [Android notes](docs/ANDROID_ENG_NOTES.md) tracks remaining device choice/Retry work.

Shared/full/fdroid regression command succeeded on retained results (shell-137). Rebuilt fullRelease/lint-vital passed (shell-138), retaining exact prior APK SHA-256 0357efbcaf7e95be47ba6f8807cc7c42322ccb7ef74451e7115671ec6d53a232; release manifest contains no QA component. These debug QA passes are not minified/Play-signed kill acceptance. [Reproduction protocol](docs/ANDROID_RECOVERY_QA.md) records guardrails/commands. C-04 remains partial for Retry/I/O/ENOSPC, cross-device and final acceptance. No commit/upload/store action.

## Preference-write Retry verified and QA removed — 2026-11-11

Original Fold RFGL62WDESD reconnected; Q-018 resolved. In the isolated QA sandbox, clean READY pid32243 confirmed committed progress/list/journal with preferences unsaved. Making only QA shared_prefs unwritable (771→500, test -w failed) produced the real startup recovery dialog. Retry while writes remained blocked kept the dialog/library gate. Restoring771 and tapping Retry opened the recovered library; independent verifier passed page12/slot2/list1/journal0/all7preferences (pid2522). Force-stop/relaunch and a second verifier pass (pid12514) confirmed persistence.

Removed only net.digitalbc.leafreader.recoveryqa (Success), restored original display stay-awake0 with readback, and returned to production Leaf: grid6, synthetic pg2/3, original Cool stuff/4items. No production reinstall/data clear/source edit/build/test rerun/commit/upload/store action. [Device report](docs/local/ANDROID_DEVICE_ACCEPTANCE_20261110.md) and [Android notes](docs/ANDROID_ENG_NOTES.md) preserve exact scope/history. Earlier pending Retry and cleanup are superseded; C-04 remains partial for actual ENOSPC/cross-device/final/Play gates. These passes use the separate debug QA artifact, not a new minified/final candidate.

## JF-01 Android metadata foundation — 2026-11-11 | development only

Implemented shared server/account/item identity plus optional detail metadata, Android authenticated detail retrieval and repository account/session validation. Missing metadata remains absent; list/detail parsing shares media-source fallback. Detail reads do not download archives, reconcile progress or flush pending writes. Existing shared JellyfinItem constructor, schema and sync/cache behavior are unchanged.

VERIFIED: shared JVM/full-debug/F-Droid-debug tests passed in shell-178; final repository additions passed both Android tasks in shell-179 (14 executed/40 up-to-date). Added 3 shared, 4 client and 2 repository tests using deterministic local fixtures, including held-response disconnect and wrong-account rejection. No live server/device actions or iOS build; no new APK/release candidate, signing, upload or publication. Final-candidate evidence above remains scoped to its exact artifacts.

[Shared JF-01 implementation record](docs/features/JELLYFIN_FIRST_LIBRARY.md) and [Android notes](docs/ANDROID_ENG_NOTES.md) retain scope, next UI work and open Q-019/Q-020 (conflict/retention). Shared-model/doc changes are recorded in [iOS notes](docs/IOS_ENG_NOTES.md). JF-A02 has a metadata foundation only; all new-feature parity acceptance rows remain not accepted and existing release gates stay unchanged.

## JF-01 Android catalogue/detail UI — 2026-11-11 | development only

Added comic-to-detail navigation, independent cover loading/book fallback, separate batch-selection checkboxes, retained browser/list state, optional metadata, server-labelled reading status, loading/errors/retry and explicit Download/cancel using the existing importer. Detail request generation/account checks prevent stale display; account-switch retry does not fetch the old item for another account. Cover HTTP work is cancellable. Direct Read/Continue and read-triggered retrieval remain outstanding; existing explicit-download retention/sync behavior is unchanged.

VERIFIED shared/full/F-Droid tests and full-debug assembly (shell-187), final ViewModel/client tests and both debug lints/full-debug assembly (shell-188), then final presentation assembly/lints through IDE build. Five new state tests and one unresponsive-cover cancellation test. Bounded synthetic Compose renders checked dark phone, light tablet,2× text auth error and separate catalogue checkboxes. Dark text/Retry contrast defect found by renders was fixed and confirmed; an initial IDE resource-cache failure was resolved by IDE build. No physical-device or real-cover/download/read acceptance is claimed.

[Android notes](docs/ANDROID_ENG_NOTES.md) and [shared feature record](docs/features/JELLYFIN_FIRST_LIBRARY.md) retain evidence, failed intermediate compile attempts and limits; shared-document update recorded in [iOS notes](docs/IOS_ENG_NOTES.md). All JF-A01–A11 acceptance rows remain open, Q-019/Q-020 choices pending. No installation, personal-server requests, iOS build, release candidate/signing/upload/publication or release-gate closure.

## JF-01 Android retained-copy Read/Continue — 2026-11-11 | development only

Detail now opens an existing scoped local comic in the native reader using saved page/slot state, independent of remote metadata success. Availability/open requests check presence/size, archive opening, recorded page count and first-page bytes on IO, without credentials/network. Invalid copies preserve the original records/files/progress and expose retry; no repair, replacement, eviction or new download-on-read policy. Navigation is consumed once and active-account isolation is checked.

VERIFIED shell-191 shared/full/F-Droid unit tasks, debug assembly and both lints; final IDE build reran both Android suites/assembly/lints, each flavor134 passed/1 skipped/0 failed. Two new repository and four new ViewModel tests cover offline lookup, invalid-copy preservation, metadata failure/disconnect, revalidation, account isolation and repeated Read/resume. Synthetic phone2×-text/tablet Continue-with-metadata-error and light-tablet remote Download previews inspected. Bounded archive checks do not prove every page decodes or that the server edition matches. No physical-device/real-server/install acceptance.

[Android notes](docs/ANDROID_ENG_NOTES.md), [shared contract record](docs/features/JELLYFIN_FIRST_LIBRARY.md) and [iOS coordination](docs/IOS_ENG_NOTES.md) retain scope and remaining work. JF-A03 and all complete feature/release acceptance remain open; pending conflict/retention choices unchanged. No release artifact promotion, signing, upload, publication or commit.

## Fold JF-01 installation/check — 2026-11-11 | partial development QA

On the connected Fold, catalogue→designated King Spawn - Vol.01 detail worked with an existing PDF/DOWNLOADED link. The initially installed page still showed Available in My Comics, identifying the preceding detail build. Nested-folder traversal also exposed inherited/lost scroll positions; this finding remains open and is distinct from detail-back acceptance.

Rebuilt fullDebug successfully and installed in place with adb -s RFGL62WDESD install -r (Success, shell-320). APK SHA256 a9ba016212fd2ac9686a012b83ec934238bd28554bf3c23b04d274a65b25f91d; package0.3.7/30700, DEBUGGABLE. Launch/library retained downloaded content and observed existing progress/list entries; no uninstall or data reset. This debug artifact is separate from earlier minified/final/Play evidence.

Update reset navigation; requested the owner reopen King Spawn's Jellyfin detail. Latest observed state is local library; the newly installed Read→reader→back check remains PENDING. No reader/page change, archive transfer, radio/permission/display change, source edit, signing-identity change, commit or publication. Normal configured-server browsing occurred; automatic pre-existing outbox behavior was not independently traced. [Android notes](docs/ANDROID_ENG_NOTES.md) retain targeted-device/tool limitation and exact observations. No JF acceptance row or release gate closed.

## Fold direct local Read/back completion — 2026-11-11 | bounded debug-device pass

Installed base.apk checksum personally read on Fold and matches a9ba016212fd2ac9686a012b83ec934238bd28554bf3c23b04d274a65b25f91d (fullDebug0.3.7/30700). Actual Jellyfin King Spawn - Vol.01 detail showed real cover/title/series and Read, device page1of172. Read opened the saved PDF cover with no transfer UI; Back returned to the same detail/page, and another Back retained the exact catalogue row positions/DOWNLOADED marker. No deliberate page advance or mark-read/unread; advanced Continue was not physically exercised.

This resolves the preceding pending direct-Read/back subcheck at this artifact/screen scope. Initial user-opened My Comics reader was distinguished from the personally exercised Jellyfin route. Real screenshots and hierarchies inspected; [Android notes](docs/ANDROID_ENG_NOTES.md) retain exact bounds, device and limitations. Phone left at the designated catalogue entry.

Folder-to-folder scroll leakage/lost parent position remains open. Light-detail status-bar content is white over the light background in the device capture and needs correction. No source fix or whole JF/release acceptance claimed. No new build/tests/install, radio/permission/display changes, archive transfer, signing, commit or publication; ordinary existing server/outbox behavior was not separately traced. Offline/lifecycle/fold/accessibility and broader transfer/error acceptance remain open.

## JF-01 connected-default Android entry — 2026-11-11 | development only

Connected new app sessions now enter Jellyfin, automatically opening the sole Books collection or showing collection selection when ambiguous. Local-only startup remains My Comics. Source actions expose My Comics, Libraries and Settings even during error/loading; loaded browser state survives reentry and changed-account responses are discarded. Automatic catalogue reads suppress progress reconciliation and pending-outbox flush; prior explicit browsing/import behavior remains unchanged. No new conflict, retention or archive-download policy.

VERIFIED shell-331 shared JVM, both Android unit suites, debug assembly and both debug lints; final strengthened regression reran both Android suites/assembly through IDE,140 passed/1 skipped/0 failed per flavor. Added three shared, five ViewModel and one repository tests. Light/dark/error/large-font Compose previews inspected. FullDebug0.3.7/30700 APK SHA256 f02a28711578b7ff335fca86e36119b33ecf9bce4601782ef14f069aafe2aa1f installed in place on Fold (targeted adb install -r, shell-334 Success). Device lock screen prevented cold-start/source/back observation; owner unlock requested, physical check PENDING. Prior a9ba Read/back evidence is retained for its original artifact only.

[Android notes](docs/ANDROID_ENG_NOTES.md), [shared record](docs/features/JELLYFIN_FIRST_LIBRARY.md), [iOS coordination](docs/IOS_ENG_NOTES.md) and local [privacy candidate wording](PRIVACY.md) updated, preserving concurrent iOS changes. Full feature/release gates remain open, including per-folder scroll restoration, light-detail status-bar contrast and unified downloaded filtering. No native iOS build, minified/Play acceptance, signing change, hosted policy publication, commit, upload or release.

## Galaxy S25 Ultra connected-entry verification — 2026-11-11 | development-device pass

At Bryan's request switched from locked Fold to Galaxy S25 Ultra SM-S938U, Android17, portrait1440×3120. In-place install shell-342 succeeded; installed base.apk SHA256 independently verified f02a28711578b7ff335fca86e36119b33ecf9bce4601782ef14f069aafe2aa1f (preceding fullDebug0.3.7/30700). Force-stop/relaunch reported COLD/Status ok and opened configured Comics directly with real covers. My Comics→hardware Back and Settings→toolbar Back restored identical catalogue rows; Libraries→Comics succeeded. Local library retained content and visible lists/status controls. Device left at Comics; no reader/download initiated or settings changed.

Supersedes pending connected-startup/source/back subcheck using S25 Ultra, without attributing it to Fold or whole JF acceptance. Existing configured account/dark-AMOLED portrait only; local-only/offline/account transitions/accessibility remain unverified. Explicit browse traffic/outbox was not instrumented. Earlier automatic-landing fixture no-write test remains distinct evidence. No source/build/test rerun, signing change, minified/Play acceptance, commit/upload/release. [Android record](docs/ANDROID_ENG_NOTES.md), [shared record](docs/features/JELLYFIN_FIRST_LIBRARY.md), [iOS coordination](docs/IOS_ENG_NOTES.md) updated; open defects and release gates preserved.

## JF-01 connected views and UI corrections — 2026-11-11 | development only

Implemented connected All/Series/Unread with recursive metadata paging/search and honest missing-state/series fallbacks, cross-source readable Downloaded, preserved Leaf Lists plus labelled BoxSet discovery/book membership with selected-library ancestry validation. Per-view state and account invalidation preserve browsing. Remote Read uses cancellable full retained import and validated-copy navigation. No new sync winner, transient-cache/eviction policy or server collection writes.

Folder list states are scoped by server/account/parent; details now extend forest behind status icons with safe horizontal insets. MainActivity adjustResize and one parent keyboard inset protect connected search. Fixed ViewModel observer initialization, cross-account downloaded markers and cancellation while blocked on HTTP headers/body. A stalled-response regression proves cancellation publishes no comic/navigation; the successful fixture proves validated import precedes one consumed navigation.

VERIFIED final shell-360 full gate: shared JVM, full/F-Droid debug units, full-debug assembly and both debug lints pass. Latest summaries131 shared passed;148 Android passed/1 skipped each. Added3 shared/8 Android tests. Initial assertions/cancellation and teardown failures were fixed and are retained in [Android evidence](docs/ANDROID_ENG_NOTES.md). Final presentation/inset assembly/lints pass; light-tablet detail and dark2×-text error renders inspected, including corrected secondary action contrast.

FullDebug0.3.7/30700 final APK SHA256 c929d40361e5fe91a354062a3bd64a0b4d6e709eb5d7917c296efbe428cd2e30 installed in place on S25 Ultra (shell-362 Success; installed checksum matches). Base source da14e0fdcd01bbf9a8cebc9b4c73de000caf1380 plus working tree. New-flow physical acceptance is PENDING because the phone is locked; no private comic download/reader action or security/preference change initiated. Existing metadata/cover traffic on attempted launch was not instrumented. Older artifact/device passes are not transferred to this candidate.

[QA record](docs/local/ANDROID_JF_VIEWS_QA_20261111.json), [shared record](docs/features/JELLYFIN_FIRST_LIBRARY.md), [iOS coordination](docs/IOS_ENG_NOTES.md) and [privacy candidate](PRIVACY.md) updated. Full-feature/release checkboxes remain open, including live collections, offline/device/accessibility/lifecycle/failure matrix and global conflict/retention questions. No native iOS build, final/minified/Play acceptance, signing change, commit/upload/release or hosted publication.

## JF-01 main-library integration correction — 2026-11-11 | development only

LibraryScreen is now the normal start destination for both connected and local-only users. Existing main-grid ComicCard renders remote metadata/cover requests directly, retaining native header, column controls, search and filters; no fake ComicEntity/file records or bulk archive import. The separate connected-screen route is retired from normal flow, with old task routes redirected to the main library. Detail/validated Read/download behavior is reused. Downloaded exposes readable cross-source copies; native Leaf lists and remote collections stay labelled. Remote Series currently offers actual-metadata group covers/drill-down with loaded counts, not the native local peel-stack animation or a complete index before paging finishes.

VERIFIED full gates shell-368/final370: shared JVM, both Android debug test suites, full assembly and both debug lints pass. Shared131 pass; Android149 pass/1 skipped each. New empty-library fixture verifies one metadata GET/cover identity and no local comic/link/outbox insertion. Main-card390dp/default/2×-text previews inspected. git diff --check passes. [Android evidence](docs/ANDROID_ENG_NOTES.md).

Final fullDebug0.3.7/30700 APK f70ff9c16598e7bfc27c0c0b3b95daa49396a6da064df09971bd1c38963f6b98 installed in place on S25 Ultra, shell-372 Success; installed checksum matches. Personally observed cold-launch empty LIGHT main library with established controls. Connected remote-cover verification is PENDING owner sign-in; no agent data clear, credentials, theme/security change, private archive transfer or reader action. Fresh setup was owner-prepared. This is bounded local-startup evidence, not completion of the fresh connected-grid milestone or full JF/release acceptance. [Shared contract](docs/features/JELLYFIN_FIRST_LIBRARY.md) now explicitly requires the existing cover-grid surface on both platforms; [iOS coordination](docs/IOS_ENG_NOTES.md) updated. Prior separate-screen artifacts/results remain historical. No native iOS build, policy decision, commit/upload/release or publication.

## JF-01 Series / mixed lists / grouped downloads — 2026-11-11 | development only

Implemented the four authorized follow-through slices in the existing Android library: native remote stacks/peel with conservative scoped run identity and cross-page metadata index; editable mixed Leaf list references; separate read-only Jellyfin collections; Downloaded-only retaining Series/list grouping. Room8 preserves list identity/order while remote membership survives import and download removal. Private metadata caching is separate from archive availability; authentication/permission failures do not expose cached catalogue pages. Portable backup supports scoped remote membership without credentials or filesystem paths.

VERIFIED shell-385 complete gate: shared JVM, full/fdroid debug tests, fullDebug assembly and both debug lints passed. Final list filter/count layout/status copy assembled successfully in shell-390; git diff --check passed. Current fullDebug0.3.7/30700 APK SHA256 **de175f1e92c5d92678845d46c100d7032d0a3ba0154bc5eff2f0ba61c90e731a**, installed in place shell-392 Success. Final S25 startup preserves authenticated main covers/existing download; Series+Downloaded preserves Unsorted grouping with1 retained copy. Pre-layout-fix build shell-385/386 also demonstrated peel1→2, remote long-press list creation and a two-member mixed list filtered to one copy without losing membership. Temporary QA list removed; no archive transfer, reader action, server mutation or user-data reset initiated. Those earlier checks do not establish a separately captured APK checksum.

[QA evidence](docs/local/ANDROID_JF_SERIES_LISTS_QA_20261111.json), [Android notes](docs/ANDROID_ENG_NOTES.md), [shared feature record](docs/features/JELLYFIN_FIRST_LIBRARY.md). All724 real server records lack supported reliable run identity and remain Unsorted. No compatible live collections returned. Empty-Downloaded acceptance unavailable in the current populated owner state; physical offline/account-switch/accessibility, final layout re-render, compact badge polish, native iOS parity and broader JF/release gates remain open. Q-019/T-02 and Q-020/T-03 unchanged. No release/signing/store/hosted-policy action.

## Android JF-01 launch-offline fallback — 2026-11-11 | development only

Implemented bounded fresh startup discovery and first-catalogue checks, temporary Downloaded fallback, non-blocking connection/auth/access messages and explicit Retry without logout, deleting cached/local data or interrupting the reader. Existing readable cross-source copies and grouped Series/list detail interactions remain available. Retry preserves local browsing while pending and removes only the automatic fallback after a real network result.

VERIFIED shell-401 complete shared/Android test/build/lint gate with --max-workers=2. Android HTML summaries each160 total/159 passed/1 skipped/0 failures. Focused timeout/controller/repository checks and 390dp/2× text notice preview passed; git diff --check passed before this documentation append. Earlier shell-398 attempt was interrupted after lint analysis failures; final rerun passed.

FullDebug0.3.7/30700 APK SHA256 **6a8e0b2f2f58da2a3d9e151fd8a60b028d72b1ecaaf39d56e06ddf290aed4e0f**, in-place S25 install shell-406 Success. Exact-candidate physical offline cold-launch/Retry/reconnection remains PENDING owner unlock; normal wake/swipe still showed keyguard. Wi-Fi/mobile data were not changed. No live reader/server writes, archive transfer, credentials or data clearing performed. Empty/populated offline runtime, lifecycle/accessibility and iOS parity remain open. [QA record](docs/local/ANDROID_JF_LAUNCH_FALLBACK_QA_20261111.json), [Android notes](docs/ANDROID_ENG_NOTES.md). No broad JF/release gate, policy choice, signing, commit or publication.


## iOS main-library development evidence — 2026-11-11 | Codex

Implemented connected main-grid/Series, scoped catalogue cache, mixed Leaf references/read-only compatible collections, full retained download-on-Read, optional onboarding and explained offline fallback. Physical iPhone11ProMax/iOS27.0.1:143 non-StoreKit-integration cases passed;30 overlapping affected cases passed after final list-unlink fix; final app launched. StoreKit integration reproduced acknowledgment timeout and was stopped during stalled cleanup. This is development evidence, not JF/release acceptance. See [native implementation and limitations](docs/features/JELLYFIN_FIRST_LIBRARY_IOS_FOUNDATION.md) and the linked engineering notes for exact manifest, evidence and open work. No release/hosted policy publication or automatic conflict/retention decision.


## Native catalogue continuity follow-through — 2026-11-12 | Codex

Implemented verified-folder fallback, progressive cache-first full metadata indexing, scoped paginated online search, pull/foreground refresh, conservative complete-snapshot reconciliation, historical grouping for retained copies, offline collection cache, live Series membership, remote-first mixed-list Continue and progress-preserving remove/redownload. No raw-path grouping, archive prefetch, server scan, automatic progress conflict winner or eviction policy. Concurrent Android/shared folder identity work preserved.

Final combined verification:140 shared tests passed with six Gradle tasks executed;140 native definitions/144 parameterized cases passed on attached iPhone11ProMax/iOS27.0.1 with no runtime warnings; Xcode build/normal launch succeeded. Two StoreKit integrations remain excluded/unresolved. Live-server metadata inspection was blocked by debugger/CoreDevice access; fixture results do not establish owner-server grouping or full acceptance. See [engineering evidence, manifest and precise remaining work](docs/IOS_ENG_NOTES.md#catalogue-continuity-implementation-and-phone-regression--2026-11-12--codex). All release/JF gate states preserved; no commit or publication.

## Android catalogue continuity and cinematic detail candidate — 2026-11-12 | development only

Implemented scoped verified-folder Series fallback, persistent progressive cache-first metadata index, automatic query paging/scoped search, offline cached search, pull/throttled foreground refresh and conservative complete-pass-only metadata removal. Retained files, list history/progress and local offline covers are preserved. Cinematic Jellyfin details now expose structured accessible creator links and full-library PersonIds/Person lookup, with cached relationship/detail provenance and account/denial guards. No scan/archive prefetch, new progress-conflict/retention policy, native iOS source change or server mutation is part of this slice. Late metadata backfill cannot overwrite an intervening manual Series edit.

VERIFIED final shell-432 shared/Android tests, fullDebug assembly and both debug lints:143 shared passes; each Android flavor171 total/170 passed/1 skipped, zero failures. Bounded390dp dark/840dp light/390dp2×-font preview confirmation passed after layout fixes. Fluent Spanish, full accessibility/themes/adaptive behavior and actual metadata/creator quality remain open; resource translations alone do not close those gates.

FullDebug0.3.7/30700 APK SHA256 **2e996b09e1ef4047dac4758e6da8c220677fd0a113b46a9dfb8bd39462f5a066**, in-place S25 Ultra install shell-433 Success, independent installed checksum matches. Launch returned success; keyguard/short screen timeout prevented live UI, actual folder/People, offline cold launch/reader/retry and paging/refresh/interaction acceptance. Radios/security settings unchanged; no data clearing, transfer or reader action initiated. [QA/source hashes](docs/local/ANDROID_JF_CONTINUITY_QA_20261112.json), [implementation and limitations](docs/ANDROID_ENG_NOTES.md#android-catalogue-continuity-and-cinematic-details--2026-11-12--codex). Candidate repository privacy wording updated; no hosted publication. All broad JF/release gates, Q-019/T-02 and Q-020/T-03 unchanged. No commit, release signing, upload or store action.


## iOS ordered-work verification and distribution reconciliation — 12 November 2026 | Codex

The current uncommitted feature candidate implements cinematic Jellyfin details and scoped whole-library creator discovery, with cached structured credits/results, bounded artwork, adaptive typography and preserved explicit reading/import/sync actions. Shared baseline143passes/current physical framework rebuilt. Final complete iPhone native result:146definitions (145passed/1failed),150parameterized cases (149passed/1failed),0skips. The only failure is repeated-tip StoreKit finishing. Recovery passes. Temporary SDK-only purchase→verified.finish reproduces the same unfinished transaction after finish returns; SDK/service cause and live-sandbox consequences remain unresolved. Production payment source/assertions restored exactly. Build and normal phone launch succeed. [Exact source/evidence/limits](iosApp/build/ios-next-20261112/README.md).

Read-only actual owner-server metadata verification now exists:682Books/7pages,110verified folders/111groups/1unresolved relationship,175credited Books/326person IDs,one successful detail/person-query sample. No real archive transfer/progress write. Separate10001-book native fixture validates full index/cache/search. Neither establishes physical offline/kill/storage/two-client acceptance. UI component renders cover320/768widths,light/dark/AMOLED/AX3; they are not physical iPad or spoken VoiceOver. Ten new es-MX entries machine-translated; fluent review remains open.

Fresh read-only ASC results supersede older missing-product/screenshot snapshots:all3expected consumables APPROVED,each review screenshot attached,each en-US only;USD1.99/4.99/9.99 andUSA/CAN/MEX/GBRavailability verified with complete responses. Published1.0isREADY_FOR_SALE/READY_FOR_DISTRIBUTION withVALID2026110902 andusesNonExemptEncryption=false. US/Mexico public lookup confirms1.0,10Novemberrelease,iOS16minimum,EN/ES. These are current published-build facts, not live sandbox/upgrade/legal clearance or an archive of the newer feature source. No metadata/account mutation.

The menu’s GitHub privacy link now serves9NovemberiOS wording describing SMB/Jellyfinimports/tips but explicitly excluding progress uploads. Candidate explicit sync/foreground indexing/creator caches require public-policy reconciliation before this source ships. [Local candidate privacy wording](PRIVACY.md) extended for creator caches; no hosted publication. Earlier5Novembermissing-tips snapshot is superseded, while candidate/public behavior mismatch remains. Current source encryption declaration remains unset; the prior published-build declaration is not automatically applied to this candidate.

Unclosed:SDK/live sandbox finishing,physical interaction/accessibility/iPad,fluent es-MX,controlled App Store upgrade,abrupt interruption/low-storage/live two-client CBZ/PDF,qualified licensing/export/privacy/candidate review. No release artifact/upload/submission/publication/real purchase/commit. Existing stable gates and historical evidence preserved.

## Android unlocked S25 recovery candidate — 12 November 2026 | development only

Bounded Samsung follow-up replaces earlier keyguard-only evidence for live Series grouping, automatic search paging, detail/Back, refresh, saved offline search and retained page3 reading. Physical failures corrected: cached library scope, real network Retry and artwork recovery. Final shell-504 shared/unit/build/lint gate passes:143 shared; each Android flavor171 total/170 passed/1 skipped, zero failures; fullDebug assembly and both debug lints pass. Final development APK7131ce31db773eecb85b01ebb616fe434416a7af8da382351c6eb68f028fee9d installed via shell-505 and installed checksum matches. Final physical cold-offline fallback/reconnect restores real cover art and preserves Series; downward peel advances the stack.

[Artifact-specific device/source record](docs/local/ANDROID_JF_DEVICE_QA_20261112.json), [engineering scope](docs/ANDROID_ENG_NOTES.md). Wi-Fi/data1/1 and stay-awake0 restored; no data clear/new archive download/intentional page advance/manual server sync. Existing reader resume may queue ordinary progress behavior. Earlier baseline/intermediate observations are explicitly attributed; no blanket exact-candidate matrix claim. Android creator, full accessibility/adaptive/account/interruption/storage/two-client and native parity acceptance remain open. All stable gates unchanged; no release/signing/upload/commit/publication.

## Android performance audit baseline — 12 November 2026 | development evidence only

Owner initiated the occasional audit during UI changes. Phase1 [report](docs/ANDROID_PERFORMANCE_AUDIT_20261112.html), [architecture](docs/ANDROID_PERFORMANCE_ARCHITECTURE_20261112.html), [UI handoff](docs/ANDROID_PERFORMANCE_UI_HANDOFF.md) and [local scoped evidence/manifest](docs/local/android-performance-20261112/README.md) completed. Production optimization/refactor and controlled After measurements remain unimplemented.

Measured existing fullDebug7131ce31…028fee9d on S25 Ultra:20s/6swipe All-grid625frames, gfxinfo52deadline misses(8.32%); Main doFrame p95/p99=5.43/9.70ms at8.33ms expected deadlines. FrameTimeline separately classifies24App Deadline Missed; metric definitions are not interchangeable. One process-cold/filesystem-warm first-frame388.07ms; not catalogue-ready/release startup. Debug/tracing overhead, one sample per journey, startup2systrace parse failures, no method-level terminal cause and no measured After benefits are explicit limitations.

Source priorities are shared scoped cover reuse, file-availability validation reuse and off-Main data projection. No automatic dependency/schema/UI migration. Browser/diagram artifact checks pass; application tests/build/install not rerun for documentation-only work. No app source/runtime settings, accounts, archives/progress, native iOS, signing/version/CI/store policy changed. Trace servers closed. Preserve all JF/release gates and source histories; this is not final-candidate performance or accessibility acceptance. No commit/upload/publication.

## Android performance fixes: verified development candidate — 12 November 2026

Implemented scoped byte-budgeted cover-memory/shared-flight reuse, shared IO file-availability validation with strict remote-copy Read checks, and cancellable Default-dispatched catalogue/local-list projection with memoized groups and ID-map backfill. Existing repository loader/flow APIs and sync/backup/account guards retained. Small prepared-group integration only in MainCatalogueGrid/LibraryScreen; no header/layout/schema/dependency/retention/sync-policy/shared/native iOS change.

Final shell-568 passed143shared tests; each Android flavor186total/185passed/1skipped/0failed; fullDebug assembly and both debug lints passed. Work-count fixtures verify one HTTP image request for8concurrent callers plus re-entry, reconnect invalidation, one archive validator call for8observers plus progress-only changes, forced Read validation,10k grouping/order/download identity and stale projection rejection. These are bounded fixtures, not measured device FPS/memory gains. [Exact source/artifact manifest](docs/local/android-performance-20261112/implementation/evidence.json), [implementation/limits](docs/local/android-performance-20261112/implementation/README.md).

Updated [interactive report/plan](docs/ANDROID_PERFORMANCE_AUDIT_20261112.html), [architecture](docs/ANDROID_PERFORMANCE_ARCHITECTURE_20261112.html), [UI handoff](docs/ANDROID_PERFORMANCE_UI_HANDOFF.md) and Android notes. Browser/offline-SVG checks shell-573 pass. Final development APK SHA256a9bdb0a551baa196b3b385e3823e7fec81e33958fb46c25c4f7246244b1a69a6 installed/checksum matched on S25 via shell-570; launch returned success but keyguard prevented live UI inspection. No data clear/settings/account/radio/archive/reader/deliberate progress action. Original audit timings are historical7131ce31 data; no controlled After/whole-feature/release/adaptive/accessibility acceptance inferred. No signing/version/CI/commit/upload/publication; all existing stable gates and owner-policy questions remain unchanged.

### Android cover pipeline follow-up — 12 November 2026

Implemented main-grid64px cover request buckets from parent constraints, bounded remote/retained thumbnail decoding, local-only system-trace cache/image diagnostics and IO cover authentication entry/return with existing identity/session guards. Cache budget and peel/reader/header behavior unchanged. Added a local non-debuggable R8 performance variant with the existing debug certificate and a variant-only profileable manifest; production release signing/version/publication behavior is unchanged.

Final shell-623 passed both Android unit tasks, fullDebug/fullPerformance assembly and both debug lints; Full190tests/0failures/1skip. Shared JVM task passed shell-609. New bitmap-bound/source-preservation/size-bucket checks and blocked-auth/UI-responsiveness test pass. The initial concurrency fixture's setup gate was corrected before the successful run.

Final fullPerformance APK SHA256faafa4a27bba87ed466bca9b3477a032d6c9cac6d679aa7f34d1268dd1849ffd installed/checksum verified on S25 R5CY93A1YRW. Compact covers, upward scrolling, downward peel1/3→2/3, Series members/Back selection preservation and All comic detail/Back pass. Final trace124cover auth slices/56decode slices off Main; no Main monitor slices matched the saved query. Gfxinfo20/351deadline misses,p95/p99=18/34ms in this sample; FrameTimeline20.08/24.16ms and remaining25.02ms measure/layout work prevent a blanket performance-acceptance claim. Same56requests/43evictions persist. [Exact protocol, code scope, SQL, manifests and mixed metrics](docs/local/android-performance-20261112/cover-followup/README.md).

No account/radio change, data clear, archive transfer, reader/deliberate progress action, new dependency, shared/native iOS change, commit, upload or publication. No release/JF/adaptive/accessibility gate or owner decision is closed by this work.


## Android isolated file-size device QA and optimized diagnostics — 2026-11-12 23:10 UTC

Development evidence only. Bryan cleared the idle device/build window. On codex/android-agent-bootstrap at base 21b3fac017885732c3f6b5dc6a7980790284ad66 plus the uncommitted file-size slice, fullDebug 86af3939401215116700cd93b66134c920ce07d930decafa33ebfe90b5c6a95c installed in place; same-source fullPerformance assembly passed and optimized APK 25f876fcfcf601b73cb05aadda099663d1570f917e8de71410f475507a777c3d is installed/checksum verified on S25 Ultra SM-S938U / Android17. Existing Debug certificate matched predecessor faafa4a2; no upload/Play signing or source integration. Package/version stay net.digitalbc.leafreader 0.3.7/30700.

Personally observed correct 160.0 MB for the retained 167775191-byte comic, pg3/300 retained, remote subtitle preservation, downward peel1/3→2/3 in place and members/Back retaining2/3 on both artifacts. Debug upward scroll and remote Details/Back passed. Cross-file rebinding/local-list/missing-denied-empty fixtures remain unavailable on this owner device. No full accessibility/adaptive, live-server sync or release acceptance is transferred from these checks.

Eight valid serial optimized traces characterize current All/Series scroll and warm Series entry. Series frame/RenderThread tails remain higher and repeated Series scroll still emits41 network events/42evictions per pass. Auth/decode stay off Main; no Main slice>20ms in these windows. Different workloads/live caches/JIT and point memory snapshots prevent a causal speedup or leak claim. Prior25.02ms layout evidence remains historical, not erased by non-reproduction. Invalid/failed attempts are preserved and excluded. [Exact protocol/results/artifacts/limits](docs/local/android-series-attribution-20261112/README.md), [device handoff](docs/local/android-filesize-qa-20261112/README.md), [Android notes](docs/ANDROID_ENG_NOTES.md). Existing143cached shared passes and191passed/1skip per Android flavor remain the automated record; only optimized assembly was newly run for profiling.

Device temporary traces were removed only after host-copy hash verification; all runners ended, final All/top with Downloaded off. No reader opening, archive transfer, deliberate progress/server write, data clear, account/radio/security change, new production change beyond file-size, commit, merge, push or release. No persistent checklist item, Q-019/Q-020 or broad JF/release gate is closed.


## Android approved Offline/Unread header implementation · 2026-11-13 UTC

Bryan assigned the next Android work and cleared the idle build/device window. Implemented All / Series / Lists / Offline tabs with an independent global Unread-only toggle, trailing removable chip and Menu grid/default-direction controls in the isolated `codex/android-agent-bootstrap` worktree at base `21b3fac017885732c3f6b5dc6a7980790284ad66`. The existing asynchronous file-size slice remains included. Offline consumes validated readable local copies, unknown remote progress remains unknown, and list/series identities, order and Continue scope are preserved. Local Edit sits above the local grid to keep the toolbar slot fixed. Large type uses the Leaf mark alone to preserve action space and awaits adaptive device review.

Final each-flavor unit results are 201 total / 200 passed / one expected SMB-live skip / zero failures or errors. Both debug lints pass with zero errors and 88 warnings each. FullDebug and same-source FullPerformance assemblies pass. Shared task earlier passed with 143 cached passes. The exact 17-file app manifest matches source after building. Final debug APK `6d61e983c834194f07525b371fceda0b2f8e4bc80a9504f8749d22a4f8fc69be` and optimized APK `ea8de8f02809cab46ddc9abca87666776a1a484b346226037dab57d824d70d64` were installed in place and checksum verified on S25 Ultra / Android 17. Existing Debug certificate, package and 0.3.7/30700 remain.

Compact physical checks pass for four tabs, persistent/clearable Unread, actual retained-copy metadata, search/member Back preservation and intentional downward peel. Testing found and fixed Menu/Back losing peeled-cover selection and tab return losing scrolled position. Final debug and optimized checks retain 2 / 3 across Menu/Back and exact first-cover bounds across scrolled Menu/Back and Series → Offline → Series. Context-tagged prepared rows avoid restoring a grid against stale rows, cached query pages survive re-entry, and cover selections retain scoped IDs. Intermediate failures and final evidence remain in the [consolidated QA record](docs/local/android-offline-unread-20261112/README.md) and [source/artifact manifest](docs/local/android-offline-unread-20261112/evidence.json).

Android slice is ready for exact-source review with bounded compact device evidence. Shared card remains In Progress. Await expanded/fold/large-type/TalkBack/keyboard/dark-mode/fluent es-MX, local-list/local-Series/failure fixtures, unreachable cold launch/reconnect, broader Jellyfin and final-candidate upgrade/privacy/licensing/release gates. Existing Series rendering/cache-churn findings remain open. This changed header requires a new frozen layout baseline before any future rendering experiment. Recommendation remains KEEP BUILDING. All release checklist history and owner questions remain preserved. Source is uncommitted and isolated.


## Android header source-review follow-up · 2026-11-13 UTC

Continuing Bryan’s assignment found two more Unread integration gaps. Saved collection traversal used the default 100-record cache query against online pages stored at limit 30 and missed entries cached under the UNREAD query. It now uses matching page size, prefers full saved membership, falls back to saved Unread membership when eligible, and stays within that query while paging. A targeted regression failed with expected first/second entries versus an empty result before the fix. It now covers both filtered-only and incomplete full caches, prevents pagination-scope mixing, and verifies local search/read/unknown handling and clearing Unread.

Local Series Continue/restart, totals and Add Comics membership now use the full scoped group even when Unread leaves zero displayed covers. The filtered grid shows the existing localized empty-result message. Physical all-started/all-completed local Series cases await an existing fixture.

Final each-flavor results are 202 total / 201 passed / one expected SMB-live skip / zero failures/errors. Both debug lints have zero errors/88 warnings, both assemblies and diff check pass. Source remains codex/android-agent-bootstrap at 21b3fac plus isolated uncommitted changes. FullDebug SHA-256 `f21ef3b561ec3cf086a6465fcaad1c8d81969e562fb9eac8e43eecef0fe30e1b`. Optimized SHA-256 `f58e73bad6b1ffccf1043014eb11282c47daa2bc2ce6d40202257bcb54b28ccf` installed/checksum verified, existing Debug certificate/0.3.7/30700.

Updated optimized S25 checks pass for peel/cover Menu return, exact scrolled Menu/tab return, all four tabs, Unread/chip, retained-copy metadata and search/member Back. [New source manifest, saved failure/pass reports and device assertions](docs/local/android-offline-unread-20261112/review-followup/README.md). Prior candidates retain their historical evidence. Exact-source integration, unavailable physical fixtures, adaptive/accessibility/localization and broader Jellyfin/upgrade/privacy/licensing/release gates remain open. KEEP BUILDING. The broader shared card stays In Progress and Series performance/cache churn remains unresolved.


## Android checklist readiness inspection · 2026-11-13 02:23 UTC

Local verified app commit`aac73d9c87c3a123f821b2fed9b26a3bb1c391ca`, documentation-reconciled baseline`3603b8969d9b288621026dc049a4e6edd7815b6d`. Initial unsigned Full/F-Droid release APK and Full AAB diagnostic build passed, all native LOAD alignments>=16KiB and no model. SYMBOL_TABLE configured, but pinned AAR binaries are already stripped and no symbol metadata is produced. Exact symbol coverage/build IDs retained. Subsequent identity/offline-license/IO-notice fixes pass203tests/one expected skip per flavor, both lints0errors/87warnings and debug/optimized assembly. Optimized0206bdd2 installed/checksum verified with bounded native About/Back evidence. Final release signing/Console matching, physical transfer/upgrade, privacy wording publication, licensing and broader device gates remain open. [Exact scoped evidence and remaining work](docs/local/android-eng-workthrough-20261113/README.md).

## Android isolated policy/retention/native checkpoint — 2026-11-13

Development source on reconciled3603b89 plus frozen [current input manifest/reports](docs/local/android-eng-workthrough-20261113/final-business/verification.json): shared144passed; each flavor229total/228passed/one expected SMB-live skip; both lints0errors/87warnings. Room8→9, optional completion backup, explicit conflict/defer and guarded confirmed sync, managed-copy removal/canonical redownload, local cover-byte cap and adaptive controls have hermetic and bounded minified synthetic native evidence. [Protocol/native limits](docs/local/android-eng-workthrough-20261113/progress-policy/README.md). Owner application remained prior0206bdd2 during this fixture phase; no Play-delivered upgrade or new shipped build is established.

Q-014 fresh production import/store/decoded grouping and normal metadata editing pass in the disposable package, with no existing user metadata rewrite. Unsupported rear-artwork experiment reverted after mixed repeated timings, preserving exact pixels/peel/Back; Series jank/churn remains open. [Targeted role/asset review proposal](docs/ANDROID_AGENT_GUIDANCE_RECONCILIATION.md) preserves root instructions/artwork; original rights/approval/export evidence still needs owner/design reconciliation. KEEP BUILDING. Existing signing/key-recovery/physical formats/failures/backup/transfer/two-client/accessibility/localization/privacy/licensing/Console/track/Play gates retain their full limits; no upload/publication or CI action.

## Android J-02 transport follow-through · 2026-11-13 development checkpoint

On isolated base6c368a8, added resolved-address enforcement for direct cleartext Jellyfin connections and per-hop transport selection while preserving response cleanup/cancellation and irreversible cross-origin credential stripping. Fifteen new hermetic transport regressions pass. Both flavor suites244total/243passed/one expected SMB-live skip, zero failures/errors; both lints0errors/87warnings and optimized assembly pass. Shared source unchanged from144JVM passes at6c. [Exact tested source/reports and precommit artifact](docs/local/android-eng-workthrough-20261113/transport/verification.json). The network-security XML correction is explanatory only; parsed effective XML matches baseline, and the final committed build must consume current bytes.

Native evidence for the exact committed artifact is recorded separately after installation in the live Android notes. These local tests do not establish successful external TLS, platform-wide LAN-only enforcement, native iOS parity, Play signing/delivery or broad release acceptance. Rear drawing-layer experiment reverted after18 controlled traces failed to show a clear RenderThread gain; rendering pressure/churn remains open. KEEP BUILDING; all existing source-integration, physical-device/failure/backup/transfer/two-client/accessibility/localization/privacy/licensing/signing/Console/track/tester gates retain their history and limits. No upload/publication or CI enablement.


## Android local verification and interrupted native trial · 2026-11-13

Current isolated branch has test-only commit3041af4 plus three uncommitted renderer/search/QA files on the reconciled21b3fac→3603b89→6c368a8→2db91af→6d37a677 history. Both flavor suites244total/243passed/one expected SMB-live skip, both lints0errors87warnings, optimized owner build1m40. Shared144JVM evidence is carried from unchanged6c sources. [Frozen205inputs, retained failures, exact APK/mapping and current limits](docs/local/android-eng-workthrough-20261113/halftone-search-guard/README.md). Atomic fake updates and explicit nongrouped Unread fixtures remove test scheduling dependencies without changing production grouping/progress or relaxing assertions/timeouts.

Development-signed optimizedAPK3821df96216401de45c7d9374ad8dc55783d1e9a58c7af848360ff2af8f127e7,0.3.7/30700, existing Debug certificate, non-debuggable/profileable/minified/resource-shrunk. Installed-r/pulled SHA verified. All8native bytes match the previously verified aligned baseline;16KiB zipalign passes, no model/runtime packaged. Launch guard observed Android Settings instead of owner foreground; final native testing stopped before capture. No launch/device/performance acceptance from this artifact. Rendering predecessors retain negative/mixed search findings; remote churn and broad device gates remain open.

Read-only GitHub recheck still reports main64c4764, newest listedv0.3.7 prerelease(Nov8), latest stable endpointv0.3.6(Nov5); Play delivery is unverified. Live main checkout remains21b3fac with unrelated dirty work preserved. KEEP BUILDING. Existing exact-source integration, signed/minified content/failure/backup/transfer/two-client/Fold/accessibility/localization/privacy publication/licensing/key-recovery/Console/track/tester/Play-delivered upgrade gates retain their full history and limits. No release signing, upload, publication, merge/push or CI enablement.


## Resumed Android acceptance: rejected trial, verified restoration · 2026-11-13

The renderer/search trial is rejected, not an accepted source change. Search latency outliers repeated17.66ms twice against current original controls10.05–11.61 despite lower static rendering cost. All31validated traces and qualifications remain [local](docs/local/android-eng-workthrough-20261113/halftone-search-guard/README.md). Three owned trial files restored; clean43f5ee5 app/runtime matches original2db91af, with later QA/test-only/docs history preserved. No experimental runtime code retained.

Fresh full unit tasks243passed/one expected SMB-live skip per flavor; both lints0errors87warnings; optimized build1m20. [205source/report/artifact verification](docs/local/android-eng-workthrough-20261113/halftone-search-guard/final-restored-verification.json). RebuiltAPK4899067f4b11ba7fb9246f20b3c1201aaf505402c341155171b2422f961b0833 exactly matches installed/pulled original0.3.7/30700 existing Debug certificate. Baseline8native bytes/alignment/no-model checks retain their verified scope. Restored native peel/Menu/membersBack and visible160MB/pg3of300 pass; QA/temp cleanup complete. Optional Play Protect APK-upload consent was declined; no build submission or protection-setting change by the agent.

KEEP BUILDING. Current source is a local review checkpoint, not Play-delivered upgrade or release acceptance. Shared144JVM result carried from unchanged6c; no new native iOS execution/parity claim. Existing source integration, real two-client/account/transfer/ENOSPC, physical Fold/accessibility/localization, signed/minified content/backup durability, privacy publication/licensing/asset rights/native symbols, key recovery/Console/track/tester gates preserve history and limits. No push/live merge/CI enablement/upload/publication.


## Android bitmap trial: original development runtime retained · 2026-11-13

The QA-only bitmap shader improved repeated component drawing, and actual Library scroll medians also improved (All FrameTimeline11.38→8.47ms, Series18.27→14.81; RenderThread7.38→4.87 and10.24→7.57). It did not satisfy the complete interaction gate: warm search10.70/17.76/8.62ms versus current original11.90/10.87/10.52. Preserve the positive scroll evidence and valid outlier; no causal/universal regression is inferred from the small chronological sample. All20 current traces thermal0/no reported loss/overlap; fresh restored and historical five-pose comparisons show only dot AA differences max4RGB. Native search/navigation and filmed intentional peel/Back pass. [Decision, exact inputs/artifacts, excluded error-banner control and full limits](docs/local/android-halftone-tile-library-20261113/README.md).

Three owned trial files restored after205hash checks; original4899067f installed/pulled verified,160MB/page3of300 retained, All/top/searchclosed and task-owned QA/temp cleanup complete. QA-only benchmark retained atd16e296; no production renderer optimization or resolved cache churn. Candidate243passes/one expected skip per flavor, lints0errors88warnings (one KTX style suggestion), optimizedbuild2m34; original production checks explicitly carried after exact restoration, shared144JVM carried unchanged. KEEP BUILDING; broader source/device/upgrade/privacy/licensing/release gates remain open.

The candidate used the existing Debug certificate, not release signing or Play-delivered upgrade evidence. Eight native library hashes are unchanged and16KiB ZIP alignment passes. No iOS/shared source change or new iOS native execution/parity claim. Existing licensing/provenance/privacy-publication/key-recovery/Console/track/tester gates retain all prior history and limits.

## Android readiness walkthrough — 2026-11-13

KEEP BUILDING. Runtime f75be00/local optimized Debug-key APK d24091f5 removes only the blank-search debounce. Both flavors243passes/one expected SMB-live skip and zero-error lints are carried; shared144 reports were verified with an up-to-date task. Matched candidate/original S25 controls and native Clear/Back/reversal/intentional-peel/position checks now pass their bounded assertions. Chronological frame p95s are mixed; exploratory layout-phase timing is not direct input-to-pixel latency, a universal speedup or broad performance acceptance. [Exact evidence](docs/local/android-cold-ui-attribution-20261113/README.md).

Corrected64-file integration proposal matches217current inputs;12build/config inputs omitted from the older205-path inventory are post-build reconciled, not retroactively frozen. Proposed tree0093191f99264ab0e8c281c6dd172e3bd0793ad0 is review preparation only; live dirty source and iOS/CI/store scope remain untouched. Combined source/destination selection and native integration acceptance remain open. [Proposal](docs/local/android-integration-proposal-20261113/README.md).

Offline d240inspection verifies eight packaged license assets/notices against source, eight native ELF16KiBalignment, compiled cloud exclusions/Android database-only transfer and full-flavor tips disabled. This is not final AAB/F-Droid inspection, actual transfer, complete source-offer/replacement/artwork provenance clearance or legal classification. Hosted policy predates current Jellyfin flows and still describes Ko-fi for the full flavor; unpublished wording and data-flow review are prepared. No policy or declaration was published. [Review](docs/local/android-artifact-preflight-20261113/REVIEW.md).

Refreshed authenticated Play Console shows Alpha0.3.7/30700 available to testers and internal0.3.6/30600 active. Complete uploaded-bundle history has those two codes. Production inactive; two opted-in testers versus12required for14continuousdays, Apply disabled. Individual drafts, Publishing overview and upload certificate remain unchecked, so this is not mutation-ready state or a next-version selection. No equivalence between Play30700 and local d240 is established. No invitations, uploads, review submissions or publication. [Read-only inventory](docs/local/android-artifact-preflight-20261113/CONSOLE_READONLY.md).

Bryan authorizes main-account King Spawn Vol.02 for live progress/read-state tests, and only S25 is available. The named retained copy downloads and displays page1of145; explicit sync returns without a visible error. Two-client/real write/interruption/upgrade acceptance is still open pending independent state/readback. Onboarding has a migration/success/cancel plan but no visual implementation because current Figma context is quota-blocked. All historical device/privacy/licensing/key-recovery/transfer/release gates remain open. [Remaining work](docs/ANDROID_REMAINING_WORK.md).


Console follow-through: Publishing overview is now inspected read-only. Managed publishing is off; one unsubmitted Alpha tester-feedback-channel change points to https://digitalbc.net/leaf/support/ (submit button observed disabled). No pending new build is established by that change. Native navigation then timed out; reconciliation found Chrome focus changed away from Console, so UI input stopped. Individual track drafts and public upload certificate remain unverified, no mutation-ready release state. Existing Console data/settings remain untouched. [Updated observation](docs/local/android-artifact-preflight-20261113/CONSOLE_READONLY.md).


## Authorized online Jellyfin checks and focus crash fix — 2026-11-13

Optimized local Debug-keyb5f81126 (0.3.7/30700) fixes the confirmed Library search-focus crash exposed by real main-account KingSpawnVol02 testing. Full217inputs frozen before build; Android243pass/one expected SMB-live skip per flavor, zero-error/87-warning lints; shared144 reports up-to-date, not newly rerun. Installed in place without reset/account/security/radio changes. Exact previous crash path now passes. [Full evidence](docs/local/android-real-sync-20261113/browser-resume/README.md).

Bounded live browser/S25 results: remote position/completion and lower-position reset, durable pending intent across crash/update, defer/relaunch/review, server and device choices, stale-choice rejection and independent browser readback pass. Visible page1/unplayed restored and cold-synced; named145page/367.5MB and original160MB/page3of300 copies retained. History/playcount/timestamps are not claimed restored. Cached remote captions can lag until metadata reload. This does not establish real network-loss/account-switch/storage interruption, whole JF-A/parity, other devices, final release signing/artifacts/privacy/licensing/transfer/upgrade/Play acceptance. KEEP BUILDING; no release checklist closure, upload, publication, live merge or CI enablement. Previous d240 performance evidence remains scoped to that older artifact.


## Local Android 0.3.8 source preparation — 2026-11-13

The owner assigned local candidate preparation. The reviewed 64-file projection is now integrated in isolated commit 3a90a1b. Selected source cb7c96e adds the reviewed release helper, a tested credential-source filter correction (31 offline helper tests), and corrected packaged notices. Application behavior remains at f3e5bb5; one notice asset changes. The immutable 216-input snapshot overrides the version to 0.3.8/30800 and excludes the QA-only Gradle init helper. Moving live, iOS, CI and store work are preserved.

No new APK or AAB has been built. The native guard observed an active iOS build and stopped before Gradle. Prior 2b59 development evidence does not close this candidate's gates. Fresh read-only Console checks cover uploaded codes 30600/30700, Alpha/Internal release histories and drafts, and unavailable production/open tracks. The upload certificate is recorded separately from Play app signing. Official bundletool was acquired and its published digest verified.

Local corresponding app source and pinned upstream 7-Zip native sources are prepared, not published. Packaged notices now correctly distinguish the historical model from the whole-page Android release. Source availability, replacement and artwork rights remain pending. The current hosted policy still omits Jellyfin flows and describes Ko-fi links disabled in the Play flavor. Replacement wording and a Console declaration review are prepared; nothing was published or submitted.

Q-026 (existing upload signing access) and Q-027 (artwork rights) are in live Android engineering notes at Bryan's request. Signing variables are absent, the relevant local filename search found only the debug key, and GitHub repository secret names are empty. No key was created, reset or extracted. Final signing, artifact, device, upgrade, backup, cancellation, transfer, accessibility, localization, privacy, licensing and Play gates remain open. **KEEP BUILDING**. [Preparation record](docs/local/android-release-prep-20261113/README.md). Historical checklists and results are preserved; no upload, live merge, CI change or release occurred.


## Android 0.3.8 unsigned rehearsal passed — 2026-11-13

Exact source cb7c96e, immutable216inputs unchanged, version0.3.8/30800: fresh shared144passed; Full and F-Droid each246passed/one expected opt-in SMB live skip; both debug assemblies; lints0errors/fatal and87warnings each. All tasks rerun without build-cache reuse. Full minified AAB/APK and unsigned F-Droid APK built; official bundletool validates the bundle and exact identity, both R8 mappings retained. [Artifact hashes and receipts](docs/local/android-release-prep-20261113/README.md).

Static checks pass: eight packaged license texts match source in each artifact, corrected notices, no model/runtime/comic assets or checked DEX inference descriptors. FullAAB/APK contain8native libraries, F-Droid4; all ELF LOAD alignments/congruence and bothAPK ZIP16KiB checks pass, and AAB config specifies PAGE_ALIGNMENT_16K. Native bytes match pinned prior binaries; dynamic symbols exist but full symbol tables/native debug-symbol metadata are absent. This is not complete native symbol coverage or runtime16KiB OS acceptance. Compiled backup/transfer rules and flavor tip flags match source, not actual OS transfer behavior.

Q-028 explicitly permits this functional build/tests alongside iOS. No device tests, performance measurements, signing credential access, installation or upload occurred. Q-026 is now correctly scoped to access to the established Keychain-backed leaf-upload identity, not an unknown signing method or proof a key is missing. Q-027 remains artwork provenance. A fresh signed attempt, signer match and final native/upgrade/backup/cancellation/transfer/privacy/source/rights/declaration/Play gates remain open. **KEEP BUILDING**; unsigned artifacts cannot pass Play handoff. Historical checklist passes are not transferred beyond the exact evidence above. Build/lock released, agent idle at this bounded milestone, no unattended worker or publication.


## Existing signing access verified; signed retry waiting — 2026-11-13

Q-026 secure access is verified with the existing Leaf application-support keystore and Leaf-specific Keychain item. Public certificate3856FF77...1DD94A matches the recorded Console upload identity, and a local nonapplication signature verifies; the access-check keystore hash remained unchanged. No credential value was printed/persisted/exported, and no key or access/security setting changed. This does not prove independent key recovery B-05.

The first fresh signed attempt entered Keychain approval during a transient native-process gap. iOS resumed while approval was pending; missing post-Keychain checking allowed unintended functional overlap. On Bryan's Q-029 answer to wait, stopped only the Android session and verified its exact-candidate Gradle daemon gone. Preserved that attempt as interrupted: shared144fresh tests completed, Android stage interrupted, no signed release acceptance. The successful unsigned rehearsal remains valid for its recorded scope. Corrected operational guard and three mocked regressions cover the race; a fresh identical216-input retry is prepared but not launched. Wait for coordinated iOS phase completion, not a process gap. No watcher/automatic retry or unattended worker remains. [Exact signing/correction record](docs/local/android-release-prep-20261113/signing/README.md). Final signed/device/upgrade/privacy/source/rights/Play gates remain open; KEEP BUILDING, no install/upload/release.


## Android 0.3.8 serial signed candidate verified — 2026-11-13

Sourcecb7c96e/216immutableinputs unchanged, version0.3.8/30800. Coordinated iOS FINISH and no-xcode checks before/after Keychain access preceded the serial retry. Execution23:39:05–23:46:10UTC/exit0, existing keystore unchanged, no credential values printed/persisted or key/security changes. Fresh shared144passed; Full/F-Droid each246passed/one expected opt-in SMB live skip; both debug builds and lints0errors/fatal87warnings. All tests rerun without build-cache reuse.

Signed FullAAB SHA25628887595e478d636b91f164cf87c239262d1930bd796ec3400cda3faf9a37818 and FullAPK9ee4d3405683e91b709c402af5fafc9829bfed6223d85817b5fff7cce283236e independently verify against existing uploadcertificate3856FF77F4AFADCE32E249068EA4A9DD7DF76708C0BDA7E20E9D3FC3761DD94A. F-Droid deliberatelyunsigned SHA2566ec190c08e8b8e6243b833d4de4440084d280063ae1e5bb45ffc60d9489e1a7c. Bundletool validates identity/nondebuggable; static licenses/notices/model exclusion/compiled policies/ELF+ZIP16KiB and AAB PAGE_ALIGNMENT_16K checks pass. Payloads equal unsigned rehearsal after excluding only signature metadata; mappings and F-Droid APK byte-identical. No full native debug symbols produced from stripped libraries; retained R8 mappings do not replace them. [Exact artifacts, signatures and receipts](docs/local/android-release-prep-20261113/README.md).

No device/performance tests, installation, live account writes, upload or publication. Prior unsigned and interrupted attempts preserved. Q-026 local access/signing resolved, independent key recovery B-05 unverified. Dedicated compatible signed/minified native acceptance, physical upgrade/backup/cancellation/transfer, privacy/source/declarations/artworkQ-027 and authorized Play-delivered gates remain open. KEEP BUILDING; signed artifact is ready for bounded compatible QA, not Play handoff or release. Current Console code/certificate/track must be rechecked before any upload. Locks released, agent finishing documentation then idle, no unattended worker; no source/live/iOS merge, push or CI/account settings change.


## Android signed candidate: bounded emulator acceptance — 2026-11-14

Exact sourcecb7c96e/0.3.8/30800 FullAPK9ee4d340 upgraded authentic signed0.3.7 d2d82f58 in-place on dedicated Pixel_10_Pro arm64 Android17/PAGE_SIZE16384. Native UI-generated fixture saved page5/12/custom list survived upgrade and cold restart. Native document export, picker/preview cancellation, KeepLocal7 and Overwrite5 durable restore pass. Generated7zip-via-CBR native SevenZip backend and two-page PDF both rendered/navigated; 7zip fixture does not establish RAR4/RAR5 coverage. Final cold restart retained all three task comics and list. All216frozen source hashes unchanged, no rebuild/runtime edit. [Exact observations, receipts and limits](docs/local/android-release-prep-20261113/device-qa/README.md).

Only bounded compatible signed/minified emulator gate is newly passed. Broader backup/preferences/remote credential and OS transfer, actual offline/sync/cancellation/account/storage interruption, physical/other-OS/vendor/form-factor and Play-delivered upgrade acceptance remain open. S25 untouched, no auth/security/network/signing changes, erase, upload or publication. Existing checklist passes/history retain their artifact limits. Owned emulator gracefully stopped and native slot released; iOS coordination notes updated. KEEP BUILDING; privacy/source/replacement/artworkQ027/declarations/key recovery and explicit distribution scope still required.


## Android remaining acceptance and About correction — 2026-11-14

The existing signed candidate cb7c96e / APK9ee4d340 now has additional native Android17/16KiB evidence: genuine stored RAR4/RAR5 two-page import/render/navigation, changed grid6→2 restore with cold export equality across five comics/list/otherpreferences, synthetic remote-reference backup roundtrip with strict portable field allowlists, malformed RAR/unsupported backup recovery and missing-comic safe skip. Final portable data was unchanged after the error checks. These are bounded emulator results; compressed/solid/encrypted/multipart RAR, physical/network/accessibility and OS transfer are not closed. The emulator was stopped without wipe/reset. [Results](docs/local/android-release-prep-20261113/remaining-acceptance/README.md).

Native About exposed an obsolete ML-panel claim. One string is corrected in isolated source6480b62, clean, using whole-page reading/current optional SMB+Jellyfin wording. A separate immutable216-input0.3.8/30800 attempt022254 is prepared; only MenuScreen.kt differs from cb7c96e. No old gate is transferred automatically. At02:31UTC, the foreground signed driver waits for the existing Leaf Keychain prompt before Gradle/log creation (Q-030); no corrected build or native About pass yet. Original signed artifacts remain the current verified candidate.

S25 remains unchanged at2b59. A separate compatible development-signed QA copy63b57769 has489payload entries byte-identical to canonical9ee4d340, but was not installed. Physical checks remain pending, and neither development nor upload signing proves a Play-delivered upgrade. Source/privacy/declaration/recovery review materials are concrete and local; artworkQ027, independent recoveryB05 and actual publication/owner declarations remain unresolved. Fresh read-only Console confirms two uploaded versions30700/30600 and matching public upload certificate; existing unsubmitted feedback-channel change untouched. KEEP BUILDING. No upload/publication, live/iOS merge, device/account/auth/security setting change or boardDone.


## Corrected signed candidate and bounded S25 acceptance — 2026-11-14

Q030 satisfied; corrected6480b62 signed0.3.8/30800 AAB0878eaa1/APK5d96f0a5 selected, all216inputs unchanged. Freshshared144/eachAndroid246pass+1expectedskip; zero-error lints,87warnings each. Canonical16KiBemulator About/storedRAR4+5/changed-grid restore pass. Separate payload-identical development-signed3a27da2b upgraded S25 in-place without erase; populated data/account preserved, generatedRAR5spread/coldresume/PDF and settings-onlyrestore pass. Final cleanup export exactly equals original2comics/0lists/7preferences excluding appversion/exporttimestamp. This does not establish Play-delivered upgrade or spoken accessibility. Bounded current candidate backup/device/native16KiB entries updated with evidence and limits; no old gates blindly transferred. [Current receipts and limitations](docs/local/android-release-prep-20261113/remaining-acceptance/ABOUT_QA_20261114.md).

KEEP BUILDING. Approved main-account fixture open atpage6 awaiting owner-controlled network disconnect for actualoffline/reconnect. Broader sync/cancellation/account/storage, accessibility/devicebreadth/OStransfer and privacy/source/artworkQ027/independentrecoveryB05/declarations/authorizeddistribution remain open. No upload/publication/push/merge/Done; historicalchecklist preserved. No running build/emulator/worker; release native slot while awaiting owner, recheck before next device step.


## Android acceptance evidence reconciliation — 2026-11-14

Resumed owner-assigned QA against isolated6480b62 and signed0.3.8/30800 FullAPK5d96f0a5; S25compatible3a27da2b unchanged. Read current notes/board; no competing xcodebuild at bounded device observation. Saved screen confirms approvedfixture page6/145. Coarse read-only connectivity shows Wi-Fi/mobiledata enabled and active defaultnetwork; no settings changed, no intentional page advancement/server writes. Owner disconnect prompt remains pending. Native lock released after observation; no build/emulator/unattended worker.

Independently rehashed all64 fresh test/lint reports. Both full and F-Droid AppDatabaseMigrationTest suites have11passes, zero failures/errors/skips. Reviewed independent populated schema4/pre-revision5 fixture SQL, normal Room migration/schema9 validation, disk reopen and comic positions/list/link/pending-write assertions. Recorded bounded upgrade_shipped_v4 and upgrade_pre_revision_v5 acceptance passes; these are automated fixture gates, not physical shipped DB or Play-delivered upgrade.

Reviewed exact current native symbol inventory: stripped upstream .symtab absent, .dynsym present; full symbol archive unavailable, exact R8 mappings retained. Qualified native_symbols_review passes under release-runbook allowance, preserving diagnostic limitations. No binary/source rebuild/symbol substitution. Corrected previous three evidence-backed acceptance statuses from passed to helper-required pass; evidence/limits retained. Six local gates now pass; offline/sync/privacy/notes and allpostPlay remain pending. Evidence: docs/local/android-release-prep-20261113/remaining-acceptance/about-migration-symbol-review.json.

KEEP BUILDING. Q027artwork/B05independent recovery and privacy/publicsource/declarations/notes, actual network/two-client/cancellation/account/storage, spoken accessibility/largefont/contrast, devicebreadth/OStransfer and eventual separatelyauthorized Play-delivered acceptance remain. No upload/publication/push/merge/Done. Engineer waiting for owner Offline response, no automatic worker/retry.


## Android S25 bounded offline/reconnect — 2026-11-14

Current6480b62/canonical5d96f0a5/S25payload-equivalent3a27da2b: verified actual network loss, approvedfixture6→7/coldrestartlibrary7/reopenedreader7 and unreachablebanner. Owner restorednetwork; freshcatalogue/targetedapprovedfixtureSync returns withoutvisibleerror/conflict, Continuepage7 retained. Roundedserver4% is not exact independentpage/ticks/queuedack proof; sync/cancellation/two-client gate stays pending. Sevenboundedlocal gates pass includingoffline; no broadclosure. Prior device6/server7 prompt left unresolved; no silentchoice/reset. Phone staysonlinepage7; no unrelatedcomic/account/settings changes.

[Exact observations and limitations](docs/local/android-release-prep-20261113/remaining-acceptance/S25_NETWORK_20261114.md). KEEP BUILDING; independentclient/livecases, accessibility/devicebreadth/OStransfer and privacy/rights/recovery/distribution gates remain. Nativephase FINISH/released, no worker/build/emulator/upload/publication/Done.


## Current source distribution packet / client acceptance — 2026-11-14

Current6480b62 built-signed source archive freshly matches all216frozeninputs; fiveartifacts/mappings and pinned875f38a upstreamsource rehashed. New unpublished DISTRIBUTION_REVIEW_6480.md uses exactcurrentarchive/artifact identity and preserves priorcb7 evidence. About-only delta leaves priorprivacyflow analysis as supporting evidence, not owner/legal/declaration approval. No source/publicprivacy/Console mutation; Q027rights/B05independentrecovery remain.

OwnerprovidedChrome approvedfixture session. Resumeonce returnedtodetail/noexactreader; afterreloadPlay/Markplayed/noResume. Potentialbrowserprogresschange requires freshLeafposition inspection/restoration before this test closes; noMarkplayed action, exactserverpage or two-clientpass. CurrentiOSlayoutbuild owns nativewindow; S25notaccessed. Sevenboundedlocalgates/KEEP BUILDING; broadergates/historypreserved. Evidence: docs/local/android-release-prep-20261113/remaining-acceptance/DISTRIBUTION_REVIEW_6480.md and browser-independent-6480-observation.json.


## Android import-session isolation finding — 2026-11-14

ANDROID-F01/P2 source-confirmed on exact6480b62: archive imports are not included in disconnect/session-job cancellation or session-generation publication checks; batch selections can be used with a newly loaded account/server. SharedViewModel persists across enabled Menu navigation. Runtime held-request reproduction/fix pending; existing progress-account tests do not cover imports. No observed real-account loss or leakage claimed. Sevenboundedlocalpasses remain, broad sync/cancellation/account/storage gate pending.

Fix original auth/URL/generation binding using existing cancellation/session machinery; test blocked archive disconnect/switch, two-item stale batch, and prepublication/relink rollback while preserving committed copies/history/list/outbox. [Exact review and acceptance](docs/local/android-session-review-20261114/README.md). Read-only review, no newbuild/test/source/device/server action. KEEP BUILDING; no broadDone or distribution. Prior browserprogress reconciliation still pending coordinatednativewindow, not waived.


## Android Series-first source prepared — 2026-11-14

Explicitly ownerassigned navigation change implemented in isolatedcodex/android-series-first-20261114, commite6de63e from6480b62. Production enumorderSeries/Lists/All/Offline and initialSeries; existingUnread/back/explicitnavigation/offlinefallback/peel retained. ExistingAllfixtures explicitlyselectAll, defaultSeriesprojection checked by updatedexistingtest. Diffcheckpasses, but no newcompile/unit/device pass: iOSnativephase active/lockheld/xcodebuild1, waitforcoordinatedrelease. [Exact patch and validation scope](docs/local/android-series-first-20261114/README.md). Current signed6480/S25unchanged; no gate transfer, live merge/install/upload/publication/Done. KEEP BUILDING.


## Android independent nondevice validation — 2026-11-14

Import-session repair9e85770 from6480b62 now reproduces3original failures and passes5focused checks, full250pass+1expectedSMBskip per flavor and zero-error lints/87warnings each. It scopes batches/actualIO cancellation/publication to the original session and protects committed retained copies. This fixes ANDROID-F01 only on that separate branch; current6480signed artifacts are unchanged.

Series-firste6de63e separately passes246+1expectedskip per flavor, zero-error lints/87warnings, and both debug APK builds. No install/device acceptance. Initial shared144 results were cached; a subsequent forced fresh run passed144, with all56trackedshared/build inputs equivalent across both branches. One execution, not two. Exact original/fixed/functional reports retained separately; historical checklist not rewritten.

Evidence: docs/local/android-import-session-20261114/README.md and docs/local/android-series-first-20261114/VALIDATION_20261114.md. Both branches remain independent; review/select exact combined source before a replacement signed candidate and fresh native acceptance. Current6480/S25 unchanged, broader live/account/storage/process-kill/accessibility/release gates remain open. KEEP BUILDING; nativewindow released, no worker/retry, merge/install/upload/publication/Done.
