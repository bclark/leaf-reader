# F-Droid Submission Checklist for Leaf Reader

This checklist tracks verified requirements and pending actions for submitting **Leaf Reader** (`net.digitalbc.leafreader`) to the official F-Droid repository (`fdroiddata`).

---

## Verified Submission Requirements

- [x] **Permanent Application ID:** Set `applicationId = "net.digitalbc.leafreader"` in `app/build.gradle.kts`.
- [x] **Unsigned Release Build:** Removed debug signing fallback in `release` build type in `app/build.gradle.kts`. Running `./gradlew :app:assembleFdroidRelease` produces a clean unsigned APK (`app-fdroid-release-unsigned.apk`) for F-Droid server signing.
- [x] **Deterministic Versioning:** Set explicit, tracked version parameters: `versionName = "0.3.3"`, `versionCode = 30300` (`MAJOR * 10000 + MINOR * 100 + PATCH`).
- [x] **F-Droid Build Flavor (`fdroid`):** Configured Gradle product flavors (`full` and `fdroid`). `7-Zip-JBinding-4Android` (and non-free unRAR code) is completely removed from the `fdroid` dependency graph, compile classpath, and native `.so` files.
- [x] **CBR/RAR Graceful Fallback:** In the `fdroid` build, opening a `.cbr` file displays a clear in-app notice informing the user that CBR/RAR support is omitted in the F-Droid FLOSS build per F-Droid inclusion policies.
- [x] **Store & Fastlane Metadata:** Updated `fastlane/metadata/android/en-US/`:
  - `title.txt`: Leaf
  - `short_description.txt`: Read CBZ/CBR comics panel by panel, with on-device panel detection.
  - `full_description.txt`: Accurate Leaf feature description.
  - Added changelog `fastlane/metadata/android/en-US/changelogs/30300.txt`.
- [x] **Bundled License Notices:** Bundled [`assets/THIRD_PARTY_NOTICES.md`](../app/src/main/assets/THIRD_PARTY_NOTICES.md) directly in the APK assets directory, ensuring full third-party notices, AGPL-3.0 text, and offline license accessibility in the app UI.
- [x] **Recipe Template:** Updated [`fdroid/net.digitalbc.leafreader.yml`](net.digitalbc.leafreader.yml) targeting `gradle: - fdroid` and `subdir: app`. Archived obsolete `com.chakra.comicreader.yml`.

---

## Action Items For F-Droid Submission

- [x] **Publish Release Tag:** Tagged and pushed `v0.3.3` on GitHub (`https://github.com/bclark/leaf-reader`).
- [ ] **Submit MR to `fdroiddata`:** Fork `https://gitlab.com/fdroid/fdroiddata`, copy `fdroid/net.digitalbc.leafreader.yml` to `metadata/net.digitalbc.leafreader.yml`, and open a Merge Request.
