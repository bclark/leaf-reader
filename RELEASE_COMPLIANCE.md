# Release Compliance Checklist

This document tracks verified legal/compliance requirements and open blockers for open-source releases of **Leaf**.

---

## Verified & Completed Compliance Items

- [x] **Primary Source Code License:** The project code is licensed under the **Mozilla Public License 2.0 (MPL-2.0)**, preserving inherited MPL-2.0 code from upstream Chika and modifications (`LICENSE`).
- [x] **Upstream Attribution:** Explicit attribution to upstream Chika (by Chakra / batunii) is retained across `README.md`, `THIRD_PARTY_NOTICES.md`, `PRIVACY.md`, `MenuScreen.kt` (Android), and `MenuView.swift` (iOS).
- [x] **In-App Legal Links:** All in-app links for Privacy Policy, Open-Source Notices, and Source Code in `MenuScreen.kt` and `MenuView.swift` point to `https://github.com/bclark/leaf-reader` while maintaining upstream links to `https://github.com/batunii/chika`.
- [x] **Third-Party Dependency Inventory:** Full audit and documentation of direct dependencies across Android & iOS in `THIRD_PARTY_NOTICES.md`:
  - AndroidX / Compose / Kotlin / Coroutines / Room (Apache-2.0)
  - Apache Commons Compress 1.27.1 (Apache-2.0)
  - SMBJ 0.13.0 (Apache-2.0)
  - Google LiteRT / TensorFlow Lite 1.4.2 (Android) & 2.17.0 (iOS) (Apache-2.0)
  - ZIPFoundation 0.9.19 (iOS, MIT) — license text in `THIRD_PARTY_LICENSES/MIT-ZIPFoundation.txt`
  - libarchive / SwiftArchive 1.0.0 (iOS, BSD) — license text in `THIRD_PARTY_LICENSES/BSD-libarchive.txt`
  - Anton & Archivo Fonts (OFL-1.1)
- [x] **Font License Compliance:** Full SIL Open Font License 1.1 texts retained in `THIRD_PARTY_LICENSES/OFL-1.1-*.txt`. Fonts are bundled inside application assets and not sold standalone.
- [x] **Manga109-s Documentation Correction:** Removed inaccurate claims describing Manga109-s as a "redistributable" dataset. Updated `THIRD_PARTY_NOTICES.md` and `training/README.md` to clarify that Manga109-s is a research dataset requiring direct application from the University of Tokyo / Manga109 Consortium, while preserving required attribution/training disclosures.
- [x] **LGPL §6 Relinking Mechanism (Android 7-Zip-JBinding):**
  - Documented that Leaf's open-source repository on GitHub provides full source and Gradle scripts for users to rebuild with modified libraries.
  - Documented the APK binary replacement procedure (`unzip`, replace `lib7-Zip-JBinding.so`, `zipalign`, `apksigner`) in `THIRD_PARTY_NOTICES.md`.
  - Retained required unRAR restriction notice (decompression permitted; compression creation prohibited).
  - Clarified that iOS uses `libarchive` (BSD-licensed) and contains no LGPL / 7-Zip code.
- [x] **Application ID (`net.digitalbc.leaf`):** Assigned permanent application ID under `digitalbc.net` in `app/build.gradle.kts`.
- [x] **F-Droid Build Flavors:** Implemented `full` and `fdroid` product flavors. `7-Zip-JBinding-4Android` is completely excluded from the `fdroid` dependency graph, compile classpath, and native binaries.
- [x] **Deterministic Versioning & Unsigned Release Build:** `versionName = "0.3.2"`, `versionCode = 30200`. Removed debug signing fallback in `release` build type so `./gradlew :app:assembleFdroidRelease` produces a clean unsigned APK (`app-fdroid-release-unsigned.apk`) for F-Droid server signing.
- [x] **Store Metadata & Notices:** Updated Fastlane metadata and changelog `30200.txt`. Bundled full third-party notices directly in `app/src/main/assets/THIRD_PARTY_NOTICES.md`.

---

## Unresolved Blockers & Decisions Needed

### 1. Bundled Model Asset License (`manga_panel_detector_int8.tflite`)
- **Status:** **Action / Decision Needed**
- **Issue:** Upstream HuggingFace repository (`leoxs22/manga-panel-detector-yolo26n`) corrected its license to **AGPL-3.0** because it derives from Ultralytics YOLO (`yolov8n` / `yolo11n`) pretrained weights.
- **Impact:** Bundling an AGPL-3.0 model asset in an open-source App Store or Google Play release creates copyleft obligations and potential App Store terms compatibility questions.
- **Recommended Action:** Replace the bundled model in a future release with a custom model trained from scratch or using a permissively licensed base architecture (e.g., RT-DETR or YOLOX) on public domain datasets (COMICS).

---

## Verification & Build Checks Run

- [x] **Gradle JVM Unit Tests:** `./gradlew :shared:jvmTest` (Passed)
- [x] **Android Build & Assembly:** `./gradlew :app:assembleDebug` (Passed)
- [x] **Android Static Analysis / Lint:** Confirmed build files and code compile cleanly without compliance/build errors.
- [x] **iOS Build Status:** Built via XcodeGen / Kotlin framework. (iOS build verified via Swift syntax check; macOS Xcode toolchain required for full iOS simulator run).
