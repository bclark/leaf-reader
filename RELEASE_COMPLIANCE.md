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
