# AGENTS.md — Leaf (`com.chakra.comicreader`)

Instructions and architectural context for AI coding agents working in this repository.

---

## 1. Project Overview

**Leaf** (forked from Chika / Chitra Katha) is an offline, panel-by-panel comic and manga reader for **Android** and **iOS**.
When a user opens a `.cbz` (ZIP) or `.cbr` (RAR/RAR5) archive, an on-device YOLO TFLite/LiteRT model (`manga_panel_detector_int8.tflite`) detects panel and speech-balloon bounding boxes. A shared Kotlin Multiplatform pipeline fills coverage gaps, orders panels in reading direction (LTR for Western comics, RTL for manga), merges tiny adjacent panels, divides oversized/spread panels along speech-balloon gaps, and frames each region with smooth camera transitions.

- **License:** Mozilla Public License 2.0 (`LICENSE`), with third-party notices in `THIRD_PARTY_NOTICES.md` (notably LGPL-2.1 for `7-Zip-JBinding-4Android` and Manga109-s dataset disclosure for the bundled model).
- **Privacy & Offline Guarantee:** The app has **no `INTERNET` permission**, no telemetry, no ads, and no network dependencies at runtime (`PRIVACY.md`). Never add network permissions or analytics SDKs.

---

## 2. Repository Structure & Modules

```text
.
├── shared/          # Kotlin Multiplatform (KMP) core — JVM 17 + iOS (LeafShared static framework)
├── app/             # Android application (Jetpack Compose, Material 3, Room, LiteRT, 7-Zip-JBinding)
├── iosApp/          # iOS application (SwiftUI, XcodeGen + CocoaPods + SwiftPM, LiteRT, libarchive)
├── training/        # Python/MPS scripts for training & exporting the YOLO TFLite panel detector
├── fdroid/          # F-Droid build recipe (com.chakra.comicreader.yml)
├── fastlane/        # Store listing metadata (fastlane/metadata/android/en-US/)
└── .github/         # CI, Android release, iOS unsigned IPA release, and iOS simulator screenshot workflows
```

### `:shared` (`shared/src/commonMain/kotlin/com/chakra/comicreader/`)
Pure Kotlin with **zero platform dependencies**. Everything that can be shared between Android and iOS lives here:
- **`detection/`**:
  - `Panel.kt` — Normalized `[0, 1]` bounding box (`Panel.FULL_PAGE = Panel(0f, 0f, 1f, 1f)`).
  - `YoloPanelDecoder.kt` — `Letterbox` calculation (`640×640`), raw/end-to-end YOLO tensor decoding (`class 0 = panel`, `class 1 = text/bubble`), IoU NMS + containment suppression, and letterbox un-projection. Always instantiate via `YoloPanelDecoder.default()` (or default constructor args on Kotlin) so thresholds never drift between platforms.
  - `PanelGapFiller.kt` — Samples a `64×64` coverage grid and recovers missed panels via maximal empty-rectangle search (ignoring thin gutters/margins).
  - `PanelOrdering.kt` — Recursive X-Y cut with straddle tolerance (`0.25f`) and row-band fallback (`0.12f`) supporting both LTR and RTL reading orders.
  - `PanelPlanner.kt` — Merges consecutive small adjacent panels and divides oversized/full-width/double-page spread panels using speech-balloon gaps. Uses `PanelPlanner.Config()` for LTR Western comics and `PanelPlanner.Config.MANGA` (less aggressive merging) for RTL manga.
  - `PanelReliability.kt` — Pairwise overlap ratio metric (`overlapScore`, `isReliable`) for diagnosing confused/borderless detections.
  - `PanelPipeline.kt` — **The single post-detection entry point** (`PanelPipeline.zoomRegions(...)`). Runs `PanelGapFiller → PanelOrdering → PanelPlanner`, falls back to splitting `Panel.FULL_PAGE` when `< 2` regions result, and pads each panel by `PANEL_MARGIN = 0.057f`.
- **`ui/reader/CameraTransform.kt`**:
  - `computePageDraw(...)` — Computes `PageDraw(left, top, scaledWidth, scaledHeight)` for "contain" framing of a normalized `Panel` inside a container with `fill = 0.98f`.
  - `lerpPanel(...)` — Linear interpolation between two `Panel` rects.
- **`data/archive/`**:
  - `ComicArchive.kt` — Archive interface + `isImageEntry(name)` filter (`jpg`, `jpeg`, `png`, `webp`, `gif`, `bmp`, `avif`; excludes hidden/`__MACOSX` entries).
  - `ComicFormatDetector.kt` — Magic-byte format detection (`PK\x03\x04` for ZIP/CBZ, `Rar!\x1A\x07` for RAR4/RAR5 CBR) with file-extension fallback.
  - `NaturalOrder.kt` — `NaturalOrderComparator` so `page2.jpg` sorts before `page10.jpg`.
- **`data/page/PageConstants.kt`**:
  - Shared decoding and LRU cache budget constants (`maxPageDimension = 2560`, `cacheMemoryDivisor = 4`, `cacheMaxBytes = 512 MB`).

### `:app` (`app/src/main/java/com/chakra/comicreader/`)
Android app targeting `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`, `buildToolsVersion = "36.1.0"`, JVM target `17`.
- `data/archive/` — `ComicArchiveFactory`, `ZipComicArchive` (Apache Commons Compress), `RarComicArchive` (`7-Zip-JBinding-4Android` supporting RAR5).
- `data/page/PageLoader.kt` — Downsampled `BitmapFactory` decoding + `LruCache`.
- `data/db/` & `data/library/` — Room database (`ComicEntity`, `ComicDao`, `AppDatabase`) + `LibraryRepository` (copies imported archives into internal storage, extracts cover thumbnails, tracks `lastPage`, `lastSlot`, `rightToLeft`).
- `detection/MlPanelDetector.kt` — Memory-maps `app/src/main/assets/manga_panel_detector_int8.tflite` via LiteRT (`com.google.ai.edge.litert:litert`), runs inference under `synchronized(lock)`, and delegates decoding/planning to `YoloPanelDecoder` + `PanelPipeline`.
- `ui/reader/` — `ReaderViewModel` (page/slot state machine + next-page prefetch) and `ReaderScreen` (Compose canvas, `Animatable` camera tweening, tap zones, velocity-flick page turns, clamped pinch-zoom/pan, immersive system bars).
- `ui/library/`, `ui/menu/`, `ui/brand/`, `ui/theme/` — Leaf pulp-comic UI components and theme.

### `iosApp/` (`iosApp/Sources/`)
Native SwiftUI iOS app (`iOS 16.0+`, Swift `5.9`) consuming `:shared` (`LeafShared.framework`).
- **Project generation:** `iosApp/project.yml` is the source of truth for **XcodeGen**. `Leaf.xcodeproj`, `Leaf.xcworkspace`, and `Pods/` are generated and **must not be committed**.
- **Dependencies:**
  - **SwiftPM** (declared in `iosApp/project.yml`): `ZIPFoundation` (CBZ reading/writing) and `SwiftArchive` (`libarchive` C product for RAR/RAR5 reading).
  - **CocoaPods** (declared in `iosApp/Podfile` with vendored podspecs in `iosApp/podspecs/`): `TensorFlowLiteSwift` and `TensorFlowLiteC`.
- **Archive handling:** `LibraryStore.swift` imports files into `Documents/Comics/`. CBZ/ZIP files are copied directly; CBR/RAR files (detected via `ComicFormatDetector`) are converted once on import into `.cbz` via `CbrConverter.swift` + `CbrReader.swift` (`libarchive` C API) so the reader only needs random-access ZIP (`CbzArchive.swift`).
- **Detection & Reader:** `LiteRTPanelDetector.swift` runs `iosApp/Sources/manga_panel_detector_int8.tflite` and calls `YoloPanelDecoder.companion.default()` + `PanelPipeline.shared.zoomRegions(...)`. `ReaderView.swift` animates panel transitions using `CameraFraming` (`GeometryEffect` wrapping `CameraTransformKt.computePageDraw`) and `ReaderGestures.swift` (UIKit gesture recognizers).

### `training/`
Local Apple Silicon (`mps`) training pipeline (`ultralytics` YOLO) to train and export replacements for `manga_panel_detector_int8.tflite` on combined Western (`COMICS`) + manga (`Manga109-s`) datasets. See `training/README.md`.

---

## 3. Build, Test & Verification Commands

### Prerequisites
- **JDK 17** (or JDK 21 locally; CI uses Temurin JDK 17 to match `jvmTarget = 17` without network toolchain downloads).
- **Android SDK** with `platforms;android-36` and `build-tools;36.1.0`.
- **macOS / iOS builds:** Xcode, `xcodegen` (`brew install xcodegen`), and CocoaPods (`pod`).

### Shared KMP Unit Tests (Run First!)
```bash
# Fast JVM unit tests for detection, ordering, planning, gap-filling, camera math, and archive utils
./gradlew :shared:jvmTest --stacktrace

# Run the same commonTest suite on the iOS simulator (macOS only)
./gradlew :shared:iosSimulatorArm64Test --stacktrace
```

### Android Build & Lint
```bash
# Build debug APK
./gradlew :app:assembleDebug --stacktrace

# Run Android Lint
./gradlew :app:lintDebug --stacktrace

# Install debug APK on connected device/emulator
./gradlew :app:installDebug
```

### iOS Build Chain (Order Matters)
Because `Leaf.xcodeproj` is generated by XcodeGen and patched by CocoaPods, always run steps in this order when setting up or changing project files:
```bash
# 1. Build the Kotlin Multiplatform static framework
# For iOS Simulator (Apple Silicon):
./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64
# Or for physical iOS device:
./gradlew :shared:linkReleaseFrameworkIosArm64

# 2. Generate Leaf.xcodeproj from iosApp/project.yml
xcodegen generate --spec iosApp/project.yml --project iosApp

# 3. Install CocoaPods (wires TensorFlowLite into the generated project & creates Leaf.xcworkspace)
(cd iosApp && pod install)

# 4. Build via Leaf.xcworkspace (NEVER build Leaf.xcodeproj directly when pods are used)
xcodebuild -workspace iosApp/Leaf.xcworkspace -scheme Leaf -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build ARCHS=arm64 \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" build
```

### iOS Simulator QA Hooks (`LeafDebug.swift`)
In `DEBUG` builds (or builds with `LEAF_SIM_CHECK`), `iosApp` honors environment variables prefixed with `SIMCTL_CHILD_` when launched via `xcrun simctl launch`:
- `LEAF_DEBUG_OPEN=<substring>` — Auto-open the first comic in `Documents/Comics` matching `<substring>`.
- `LEAF_DEBUG_PAGE=<int>` — Start on 0-based page index.
- `LEAF_DEBUG_STEP=<int>` — Start on slot (`-1` = intro, `0..n-1` = panel, `n` = outro).
- `LEAF_DEBUG_RTL=0|1` — Force LTR (`0`) or RTL (`1`).
- `LEAF_DEBUG_RAW=1` — Bypass `PanelPlanner` and inspect raw ordered model detections.
- `LEAF_DEBUG_MODEL=<name>` — Load an alternate `.tflite` resource from the bundle.

See `.github/workflows/sim-check.yml` for how CI boots simulators, seeds `iosApp/ci/SyntheticManga.cbz`, and captures screenshots.

---

## 4. Architectural Invariants & Guardrails

1. **Keep `:shared` Pure and Authoritative**
   - Never import Android, UIKit, or platform-specific APIs in `shared/src/commonMain`.
   - Never duplicate post-detection logic (`PanelGapFiller`, `PanelOrdering`, `PanelPlanner`), YOLO decoding (`YoloPanelDecoder`), camera framing (`computePageDraw`), or format detection (`ComicFormatDetector`) in `:app` or `iosApp`. Both platforms must call `PanelPipeline.zoomRegions(...)`.
   - Remember that Kotlin default parameter values are not exported to Swift/Obj-C headers; expose explicit helpers or companion factory methods (like `YoloPanelDecoder.default()`) when Swift callers need default configurations.

2. **Maintain Strict Android ↔ iOS Detection & Framing Parity**
   - Model input preprocessing is the only platform-specific step in panel detection and must remain pixel-equivalent across platforms:
     - Letterbox via `Letterbox.fit(pageW, pageH, 640)`.
     - Bilinear scaling (`Bitmap.createScaledBitmap(..., filter = true)` on Android; `CGContext.interpolationQuality = .medium` on iOS — do **not** change iOS to `.high`/bicubic as it perturbs borderline detections).
     - Padding filled with RGB `(114, 114, 114)` normalized to `[0, 1]` float32.
     - On iOS (`LiteRTPanelDetector.swift`), **do not add a vertical flip** to the `CGContext` when drawing the `CGImage`; `CGBitmapContext` row 0 is already the top scanline when drawn at `y = size - padY - newH`.
   - Whenever touching detection, ordering, planning, or camera math, run `./gradlew :shared:jvmTest` and verify `DevicePageDiagnosticTest` passes.

3. **Reader Slot State Machine Convention**
   - Every page steps through: **Whole-Page Intro → Panel 1 → … → Panel N → Whole-Page Outro**, so page transitions always start and end zoomed out.
   - Note the index offset between platforms when reading/modifying UI state:
     - **Android (`ReaderViewModel.kt`)**: `slot = 0` is intro, `1..panels.size` are 1-based panels (`panels[slot - 1]`), `panels.size + 1` is outro.
     - **iOS (`ReaderView.swift`)**: `step = -1` is intro, `0..<regions.count` are 0-based panels (`regions[step]`), `regions.count` is outro.

4. **Thread Safety & Concurrency**
   - **TFLite/LiteRT `Interpreter` is NOT thread-safe.** Always guard inference with `synchronized(lock)` on Android and `NSLock` + serial `detectQueue` on iOS.
   - **`ZIPFoundation` (`Archive`) on iOS is NOT thread-safe.** All page reads on `CbzArchive` must be synchronized (`NSLock` in `CbzArchive` / serial `ioQueue`).
   - Do not decode multi-MB comic pages on the main/UI thread.

5. **Android Packaging, 16 KB Alignment & R8 Rules**
   - `app/build.gradle.kts` enables R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) for release builds. Any new JNI/reflection dependencies must have keep rules added to `app/proguard-rules.pro`.
   - Play Store `targetSdk 36` requires **16 KB ELF page alignment** (`0x4000`) for all native `.so` libraries (`7-Zip-JBinding-4Android >= Release-16.02-2.03` and `litert >= 1.4.2`). Verify after any native dependency bump with:
     `unzip -p app.apk 'lib/arm64-v8a/*.so' | readelf -lW - | grep LOAD`
   - Keep `androidResources { noCompress += "tflite" }` so `MlPanelDetector` can `mmap` the model asset via `openFd`.
   - `versionCode` in `app/build.gradle.kts` is deterministically computed from `versionName` (`MAJOR*10000 + MINOR*100 + PATCH`) for F-Droid reproducible builds.

6. **Brand & Design System**
   - Follow the established Leaf pulp-comic aesthetic defined in `Color.kt` / `LeafTheme.swift` and `LeafBrand.kt` / `LeafBrand.swift`:
     - **Palette:** `Ink` (`#17100E`), `InkSoft` (`#2A201C`), `Crimson` (`#D11F2D`), `CrimsonBright` (`#E62534`), `Maroon` (`#7C1620`), `Cream` (`#F3E9D6`), `Ochre` (`#E0A22B`).
     - **Typography:** `Anton` (display/headers, uppercase with wide tracking) and `Archivo` (UI/body).

