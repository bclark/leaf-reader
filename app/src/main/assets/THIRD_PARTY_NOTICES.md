# Third-Party Notices

Leaf (the application source) is a fork of **Chika** (by Chakra / batunii) and is licensed under the **Mozilla Public License 2.0 (MPL-2.0)** — see [`LICENSE`](https://github.com/bclark/leaf-reader/blob/main/LICENSE).

This document details all third-party software libraries, native binaries, fonts, machine learning models, and datasets used by or bundled with Leaf across Android and iOS platforms. Full license texts are provided in [`THIRD_PARTY_LICENSES/`](https://github.com/bclark/leaf-reader/tree/main/THIRD_PARTY_LICENSES).

---

## Summary of Direct Dependencies & Assets

| Component / Dependency | License | Platforms | Bundled? | Key Obligations & Source |
|---|---|---|---|---|
| **AndroidX / Jetpack Compose / Room / Core / Activity / Lifecycle / Navigation / DocumentFile** | Apache-2.0 | Android | Yes (bytecode) | Retain license & NOTICE attribution. © The Android Open Source Project |
| **Kotlin Standard Library & Coroutines** | Apache-2.0 | Android / iOS / Shared | Yes | Retain license notice. © JetBrains / Kotlin Foundation |
| **Apache Commons Compress** (`org.apache.commons:commons-compress:1.27.1`) | Apache-2.0 | Android | Yes (bytecode) | Retain Apache 2.0 license & NOTICE. © The Apache Software Foundation |
| **SMBJ** (`com.hierynomus:smbj:0.13.0`) | Apache-2.0 | Android | Yes (bytecode) | Retain Apache 2.0 license. © Jeroen van Erp (Hierynomus) |
| **OkHttp** (`com.squareup.okhttp3:okhttp:4.12.0`) | Apache-2.0 | Android | Yes (bytecode) | Retain Apache 2.0 license & NOTICE. © Square, Inc. |
| **LiteRT / TensorFlow Lite** (`litert:1.4.2` / `TensorFlowLiteSwift:2.17.0`) | Apache-2.0 | Android / iOS (optional) | Excluded in whole-page release builds | Retain Apache 2.0 license when bundled. © The TensorFlow / LiteRT Authors |
| **7-Zip-JBinding-4Android** (`Release-16.02-2.03`) | **GNU LGPL-2.1** (+ unRAR, BSD) | Android ONLY | Yes (native `.so`) | Retain LGPL-2.1 notice & unRAR restriction; allow library replacement (§6). See LGPL section below. |
| **ZIPFoundation** (`0.9.19`) | MIT | iOS ONLY | Yes (compiled) | Retain MIT copyright notice. © Thomas Weichselbaumer |
| **libarchive** (`libarchive-for-swift:1.0.0`) | BSD 2-Clause / 3-Clause / Public Domain | iOS ONLY | Yes (compiled) | Retain BSD license notice. © Tim Kientzle et al. |
| **Anton Font** (`Anton-Regular.ttf`) | OFL-1.1 | Android / iOS | Yes (asset) | Retain SIL OFL text; do not sell font standalone. © Vernon Adams |
| **Archivo Font** (`Archivo-Variable.ttf`) | OFL-1.1 | Android / iOS | Yes (asset) | Retain SIL OFL text; do not sell font standalone. © Omnibus-Type |
| **Panel Detection Model** (`manga_panel_detector_int8.tflite`) | **AGPL-3.0** (Derived from Ultralytics YOLO) | Repository / training only | No (excluded from Android & iOS release builds) | Trained on Manga109-s + COMICS. Excluded from packaged app binaries. See Model section below. |

MPL-2.0 (Leaf) is compatible with combining larger works governed by the above licenses (MPL-2.0 §3.3).

---

## Detailed License Notices

### Apache License 2.0
Full text: [`THIRD_PARTY_LICENSES/Apache-2.0.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/Apache-2.0.txt)

- **Kotlin Standard Library & KotlinX Coroutines** (`1.9.0` / `2.2.10`) — © JetBrains s.r.o. and Kotlin Foundation contributors.
- **AndroidX & Jetpack Libraries**: `core-ktx:1.15.0`, `lifecycle-*:2.8.7`, `activity-compose:1.9.3`, `compose-bom:2024.12.01`, `navigation-compose:2.8.5`, `documentfile:1.0.1`, `room-*:2.7.0-alpha13` — © The Android Open Source Project.
- **Apache Commons Compress** (`1.27.1`) — © The Apache Software Foundation. Used for CBZ/ZIP archive reading on Android.
- **SMBJ** (`0.13.0`) — © Jeroen van Erp (Hierynomus). Used for SMB network drive connectivity on Android.
- **OkHttp** (`4.12.0`) — © Square, Inc. Used for Jellyfin client network connectivity and authenticated downloads on Android.
- **Google LiteRT / TensorFlow Lite**:
  - Android: `com.google.ai.edge.litert:litert:1.4.2` — © Google LLC / The TensorFlow Authors.
  - iOS: `TensorFlowLiteSwift` / `TensorFlowLiteC` (`2.17.0`) via CocoaPods — © Google LLC / The TensorFlow Authors.

### MIT License
Full text: [`THIRD_PARTY_LICENSES/MIT-ZIPFoundation.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/MIT-ZIPFoundation.txt)

- **ZIPFoundation** (`0.9.19`) — © 2017-2024 Thomas Weichselbaumer. Used for CBZ/ZIP archive handling on iOS.

### BSD Licenses
Full text: [`THIRD_PARTY_LICENSES/BSD-libarchive.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/BSD-libarchive.txt)

- **libarchive** via `libarchive-for-swift` (`1.0.0`) — © 2003-2024 Tim Kientzle et al. Used for CBR/RAR archive decompression on iOS.

### SIL Open Font License 1.1 (OFL-1.1)
Full texts: [`THIRD_PARTY_LICENSES/OFL-1.1-Anton.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/OFL-1.1-Anton.txt), [`THIRD_PARTY_LICENSES/OFL-1.1-Archivo.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/OFL-1.1-Archivo.txt)

- **Anton** (Display Font) — © 2011 Vernon Adams (`vernon@sansseries.fr`).
- **Archivo** (UI Font) — © 2019 Omnibus-Type (`omnibus-type@omnibus-type.com`).
- *Obligation:* Fonts are bundled as application assets. Copyright and OFL license texts are retained. Fonts are not sold on a standalone basis.

---

## GNU LGPL 2.1 — 7-Zip-JBinding-4Android (Android Only, full flavor)

Full text: [`THIRD_PARTY_LICENSES/LGPL-2.1.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/LGPL-2.1.txt)  
Upstream Source: https://github.com/omicronapps/7-Zip-JBinding-4Android (wraps 7-Zip JBinding / 7-Zip, https://7-zip.org)

`com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03` is bundled as a dynamically loaded native library (`lib7-Zip-JBinding.so`) on Android to decode **CBR (RAR/RAR5)** archives in the `full` flavor. It is completely excluded from the F-Droid FLOSS build (`fdroid` flavor).

- **unRAR License Restriction:** Portions derived from unRAR carry the restriction that unRAR source code may not be used to re-create the RAR compression algorithm. Leaf uses the library strictly for decompression/reading, which is permitted.
- **LGPL §6 Relinking / Replacement Compliance:**
  1. **Source Code Availability:** Leaf is an open-source project. Complete source code and build scripts are publicly accessible at https://github.com/bclark/leaf-reader. Any user may modify or update the 7-Zip-JBinding dependency or build scripts and recompile the application.
  2. **APK Binary Replacement:** Users receiving prebuilt APK binaries may replace `lib7-Zip-JBinding.so` directly:
     - Unzip the APK archive (`unzip Leaf.apk -d /tmp/apk`).
     - Replace `lib/<abi>/lib7-Zip-JBinding.so` with a custom-compiled version of the library.
     - Re-align and re-sign the APK using `zipalign` and `apksigner`.
- **Platform Scope:** iOS does **not** bundle 7-Zip-JBinding or any LGPL code; iOS uses `libarchive` (BSD-licensed) for RAR decompression.

---

## Bundled Panel-Detection Model & Training Data

Full text: [`THIRD_PARTY_LICENSES/AGPL-3.0.txt`](https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_LICENSES/AGPL-3.0.txt)

- **Model Asset:** `manga_panel_detector_int8.tflite` (bundled in `app/src/main/assets/` on Android and `iosApp/Sources/` on iOS).
- **Provenance & Model License (AGPL-3.0):**
  - Originates from [`leoxs22/manga-panel-detector-yolo26n`](https://huggingface.co/leoxs22/manga-panel-detector-yolo26n).
  - The model was trained and exported using **Ultralytics YOLO** (`yolov8n` / `yolo11n`) software and pretrained weights.
  - While originally labelled Apache-2.0 on HuggingFace, the upstream author explicitly corrected the license label to **GNU AGPL-3.0** because it derives from Ultralytics AGPL-3.0 pretrained weights.
  - Full Python training, dataset conversion, and export scripts are located in `training/` within this repository (`train.py`, `export_tflite.py`, `manga109_to_yolo.py`, `comics_to_yolo.py`).
  - *Evaluation Note:* Retaining an AGPL-3.0 model asset in an open-source App Store or Google Play release is under review. An alternative model trained from a permissively licensed base (e.g., RT-DETR or YOLOX) on public domain datasets is recommended for future releases.

- **Training Data Disclosures (Manga109-s & COMICS):**
  - **Manga109-s Dataset:** The model was trained in part on annotations from the **Manga109-s dataset** (developed by the University of Tokyo / Manga109 Consortium).
  - *Clarification on Manga109-s Availability:* Manga109-s is a research dataset available to researchers upon approved application directly from the provider at http://www.manga109.org/en/download_s.html. **Manga109-s is NOT a freely redistributable dataset**; dataset images and annotations are not included in this repository.
  - *Disclosure Requirement:* Per Manga109 terms, use of Manga109-s in model training requires explicit citation/disclosure — **this notice constitutes that disclosure**. Dataset usage permissions do not override underlying model software/weights licenses (such as AGPL-3.0).
  - **COMICS / UMIACS Dataset:** Additional training data includes public domain panel annotations from the COMICS dataset.

---

## Brand Assets & Upstream Attribution

- Leaf is a fork of **Chika** (created by Chakra / Chalchitra Krida). Upstream source is maintained at https://github.com/batunii/chika.
- The **Leaf** name, logo, and wordmarks are brand assets of this project. Trademark and brand rights for Chika / Chitra Katha remain reserved by their respective owners and are not governed by the code license.
