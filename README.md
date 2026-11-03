<p align="center">
  <img src="docs/leaf-icon.png" width="160" alt="Leaf Logo">
</p>

<p align="center">
  <strong>Leaf</strong> is an immersive, offline comic and manga reader for Android and iOS built with Kotlin Multiplatform and Jetpack Compose.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg" alt="License: MPL 2.0"></a>
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84.svg" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/platform-iOS%2017.0%2B-000000.svg" alt="iOS 17.0+">
  <img src="https://img.shields.io/badge/architecture-Kotlin%20Multiplatform-7F52FF.svg" alt="Kotlin Multiplatform">
</p>

<p align="center">
  <img src="docs/screens/spread.png" width="850" alt="Two-Page Spread Reading Mode">
</p>

<p align="center">
  <img src="docs/screens/library.png" width="22%" alt="Comic Library with Series Stacks">
  &nbsp;
  <img src="docs/screens/series.png" width="22%" alt="Series Detail View">
  &nbsp;
  <img src="docs/screens/lists.png" width="22%" alt="Reading Lists Stacks">
  &nbsp;
  <img src="docs/screens/reader.png" width="22%" alt="In-Reader Navigation & Controls">
</p>

---

## Origin & Upstream Attribution

> [!NOTE]
> **Leaf** is a friendly fork of [**Chika**](https://github.com/batunii/chika) by [Chakra](https://github.com/batunii).
> 
> The core on-device ML panel-detection engine, Kotlin Multiplatform architecture (`:shared`), and distinctive pulp-comic design system (Anton & Archivo typography with halftone ink, crimson, and cream styling) were created by Chakra for Chika. All credit for that foundational work belongs to the upstream project.
> 
> * Upstream Repository: [github.com/batunii/chika](https://github.com/batunii/chika)
> * Original Author: [Chakra (batunii)](https://github.com/batunii)

---

## What makes Leaf special

Leaf lets you read comics your way: choose between **guided panel-by-panel reading** powered by on-device ML, or traditional **full-page flip transitions**.

### Key Features

- **Dual Reading Modes (Quick Settings Switch):**
  - **Panel by Panel:** Steps smoothly from panel to panel in reading order with camera zoom transitions — making reading comfortable on phones without tedious manual pinch-zooming.
  - **Page Flip:** Smooth horizontal full-page slide transition for traditional graphic novel reading and wide splash pages.
- **On-Device ML Panel Detection:** Powered by an on-device YOLO TFLite model trained on Manga109-s; discovers panels and speech bubbles completely offline with zero latency or cloud dependencies.
- **Manga (RTL) & Western (LTR) Direction:** First-class support for Right-to-Left Japanese manga and Left-to-Right Western comics, with automatic detection, per-book memory, and specialized manga planning algorithms.
- **Smart Merge & Split Planner:** Merges tiny adjacent panels for readability and splits wide multi-character spreads with speech-bubble-aware cuts.
- **Interactive 3D Page Curl:** Realistic gesture-driven page curl transition with authentic cylinder deformation, specular highlights, back-page rendering, and shadow depth.
- **Comprehensive Format Support:**
  - **CBZ** (ZIP archives)
  - **CBR** (RAR4 and RAR5 via 7-Zip-JBinding)
  - **PDF** (Native vector and raster page rendering)
- **SMB Network Drive Streaming:** Connect to local Samba / SMB network shares over your home Wi-Fi, browse remote directories, and stream or download comics directly.
- **Multi-Select & Batch Import:** Import single comics, multi-select specific titles, or "Select All" to bulk-import whole folders from device storage or remote SMB shares.
- **Adaptive Grid Density:** Seamless column toggling (2, 3, 4, or 6 columns) tailor-made for foldable devices (like Samsung Galaxy Z Fold) and Android tablets.
- **Instant Library Search:** Real-time title search and filtering across your entire comic library.
- **Reading Progress Sync:** Automatically saves and resumes your exact page **and** panel position per comic.
- **AMOLED True-Black Theme:** High-contrast true-black mode for OLED screens and battery savings.
- **Privacy & 100% Offline Core:** Declares no external cloud dependencies, zero analytics, zero telemetry, and zero ads. The `INTERNET` permission is used strictly for connecting to your local LAN Samba (SMB) shares.

---

## Architecture

Platform-independent logic is organized in `:shared`, a Kotlin Multiplatform module (JVM & iOS targets) consumed by the `:app` Android frontend and the `iosApp` SwiftUI frontend:

```
:shared (commonMain — pure Kotlin, unit-tested)
  data/archive   ComicArchive interface · ZipComicArchive · natural page ordering
  detection      Panel model · PanelOrdering (reading order) · PanelPlanner (merge/divide)
  ui/reader      Camera math · panel containment framing · camera lerp transitions

:app (Android Jetpack Compose)
  data/archive   ZipComicArchive (CBZ), RarComicArchive (CBR/RAR5), PdfComicArchive (PDF)
  data/drive     SmbDriveClient · SMB stream provider & background downloader
  data/library   LibraryRepository · multi-select import · Room database persistence
  data/page      PageLoader · downsampled bitmap decoding · LRU memory cache
  detection      MlPanelDetector · TFLite inference · whole-page fallback
  ui/brand       Component kit (wordmark, halftone patterns, gold leaf emblem, dog-eared bookmark badges)
  ui/library     LibraryScreen & LibraryViewModel (grid density, real-time search, batch import)
  ui/reader      ReaderScreen & ReaderViewModel (3D page curl, panel camera, gestures, page slide)
  ui/theme       Leaf brand palette (Forest Green, Sage, Gold Accent, Deep Ink, Paper White) + Anton/Archivo type

iosApp (Native iOS SwiftUI)
  Sources/       SwiftUI views (ReaderView, LibraryView, MenuView) backed by LeafShared framework
```

**Technology Stack:**
- **Language:** Kotlin 2.0+ (Kotlin Multiplatform) & Swift
- **UI:** Jetpack Compose with Material 3 (Android) & SwiftUI (iOS)
- **Local Database:** AndroidX Room
- **Machine Learning:** TensorFlow Lite / LiteRT (YOLO26n Manga109-s model)
- **Archive Decoding:** Apache Commons Compress, 7-Zip-JBinding-4Android, Android PdfRenderer
- **Network File Sharing:** SMBJ for Samba / SMB 2 & 3 protocol support

---

## Building and Running

Requires **Android Studio** (Koala or newer) with JDK 21 and Android **API 36** (`minSdk 26`, Android 8.0+).

```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Build and install to a connected device or running emulator
./gradlew :app:installDebug
```

On Windows without `java` on `PATH`, point Gradle at Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:installDebug
```

---

## Support Leaf

Leaf is maintained by [Bryan Clark](https://github.com/bclark) (forked with appreciation from Chika by Chakra). All features remain 100% free and open source. If you enjoy using Leaf, you can leave an optional tip to support ongoing development:

* **Ko-fi:** [ko-fi.com/bryanclark](https://ko-fi.com/bryanclark)

---

## License & Third-Party Notices

Leaf is licensed under the **Mozilla Public License 2.0** — see [`LICENSE`](LICENSE).

Third-party libraries, bundled models, and fonts retain their respective licenses:
- **Chika upstream base:** [batunii/chika](https://github.com/batunii/chika) (MPL 2.0)
- **Panel Detection Model:** [`leoxs22/manga-panel-detector-yolo26n`](https://huggingface.co/leoxs22/manga-panel-detector-yolo26n) (GNU AGPL-3.0, derived from Ultralytics YOLO; trained on Manga109-s + COMICS)
- **Fonts:** [Anton](https://fonts.google.com/specimen/Anton) and [Archivo](https://fonts.google.com/specimen/Archivo) (SIL Open Font License 1.1)
- **Libraries:** TensorFlow Lite, Apache Commons Compress, 7-Zip-JBinding (LGPL-2.1, full flavor), SMBJ

Detailed license audits and source disclosures are documented in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
