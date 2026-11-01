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
| **LiteRT / TensorFlow Lite** (`litert:1.4.2` / `TensorFlowLiteSwift:2.17.0`) | Apache-2.0 | Android / iOS | Yes (native `.so` / `.framework`) | Retain Apache 2.0 license. © The TensorFlow / LiteRT Authors |
| **7-Zip-JBinding-4Android** (`Release-16.02-2.03`) | **GNU LGPL-2.1** (+ unRAR, BSD) | Android ONLY | Yes (native `.so`) | Retain LGPL-2.1 notice & unRAR restriction; allow library replacement (§6). See LGPL section below. |
| **ZIPFoundation** (`0.9.19`) | MIT | iOS ONLY | Yes (compiled) | Retain MIT copyright notice. © Thomas Weichselbaumer |
| **libarchive** (`libarchive-for-swift:1.0.0`) | BSD 2-Clause / 3-Clause / Public Domain | iOS ONLY | Yes (compiled) | Retain BSD license notice. © Tim Kientzle et al. |
| **Anton Font** (`Anton-Regular.ttf`) | OFL-1.1 | Android / iOS | Yes (asset) | Retain SIL OFL text; do not sell font standalone. © Vernon Adams |
| **Archivo Font** (`Archivo-Variable.ttf`) | OFL-1.1 | Android / iOS | Yes (asset) | Retain SIL OFL text; do not sell font standalone. © Omnibus-Type |
| **Panel Detection Model** (`manga_panel_detector_int8.tflite`) | **AGPL-3.0** (Derived from Ultralytics YOLO) | Android / iOS | Yes (asset) | Trained on Manga109-s + COMICS. Subject to AGPL-3.0 copyleft & Manga109 disclosure. |

MPL-2.0 (Leaf) is compatible with combining larger works governed by the above licenses (MPL-2.0 §3.3).
