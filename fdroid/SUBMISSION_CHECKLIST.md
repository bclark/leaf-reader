# F-Droid Submission Checklist for Leaf Reader

This checklist tracks verified requirements and pending actions for submitting **Leaf Reader** (`net.digitalbc.leaf`) to the official F-Droid repository (`fdroiddata`).

---

## Verified Submission Requirements

- [x] **Permanent Application ID:** Set `applicationId = "net.digitalbc.leaf"` in `app/build.gradle.kts`.
- [x] **Unsigned Release Build:** Removed debug signing fallback in `release` build type in `app/build.gradle.kts`. Running `./gradlew :app:assembleFdroidRelease` produces a clean unsigned APK (`app-fdroid-release-unsigned.apk`) for F-Droid server signing.
- [x] **Deterministic Versioning:** Set explicit, tracked version parameters: `versionName = "0.3.2"`, `versionCode = 30200` (`MAJOR * 10000 + MINOR * 100 + PATCH`).
- [x] **F-Droid Build Flavor (`fdroid`):** Configured Gradle product flavors (`full` and `fdroid`). `7-Zip-JBinding-4Android` (and non-free unRAR code) is completely removed from the `fdroid` dependency graph, compile classpath, and native `.so` files.
- [x] **CBR/RAR Graceful Fallback:** In the `fdroid` build, opening a `.cbr` file displays a clear in-app notice informing the user that CBR/RAR support is omitted in the F-Droid FLOSS build per F-Droid inclusion policies.
- [x] **Store & Fastlane Metadata:** Updated `fastlane/metadata/android/en-US/`:
  - `title.txt`: Leaf
  - `short_description.txt`: Read CBZ/CBR comics panel by panel, with on-device panel detection.
  - `full_description.txt`: Accurate Leaf feature description explaining that network permission is used exclusively for user-configured SMB local network shares.
  - Added changelog `fastlane/metadata/android/en-US/changelogs/30200.txt`.
- [x] **Bundled License Notices:** Bundled [`assets/THIRD_PARTY_NOTICES.md`](../app/src/main/assets/THIRD_PARTY_NOTICES.md) directly in the APK assets directory, ensuring full third-party notices and offline license accessibility.
- [x] **Recipe Template:** Created [`fdroid/net.digitalbc.leaf.yml`](net.digitalbc.leaf.yml) targeting `fdroidRelease`.

---

## Pending Action Items Before Opening F-Droid Merge Request

- [ ] **Drop Permissive Model Asset (Option A):** Place the trained Apache-2.0 panel detector model file into `app/src/main/assets/manga_panel_detector_int8.tflite` once training on public domain data completes.
- [ ] **Publish Release Tag:** Tag the release commit as `v0.3.2` on GitHub (`https://github.com/bclark/leaf-reader`) so the F-Droid build server can check out `commit: v0.3.2`.
- [ ] **Submit MR to `fdroiddata`:** Fork `https://gitlab.com/fdroid/fdroiddata`, copy `fdroid/net.digitalbc.leaf.yml` to `metadata/net.digitalbc.leaf.yml`, and open a Merge Request.
