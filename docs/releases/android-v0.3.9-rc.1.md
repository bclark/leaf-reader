# Leaf 0.3.9 — RC1

A fresh start for your next issue.

- Start with a local file or choose Jellyfin from the new welcome screens. Existing installations keep ordinary Library access.
- Search across your library and manage offline copies with clearer actions and removal confirmation.
- Open SMB imports in optimized builds, and cancel ongoing transfers without the previous long cleanup wait.

## Downloads and signing

- **Full APK:** `leaf-0.3.9-rc.1-full.apk`, signed with Leaf's existing upload key.
- **Full AAB:** `leaf-0.3.9-rc.1.aab`, signed; intended for a separately authorized Play upload, not direct installation.
- **F-Droid APK:** `leaf-0.3.9-rc.1-fdroid-unsigned.apk`, deliberately unsigned for downstream signing; it cannot be installed as supplied.
- Matching R8 maps, checksums, corresponding Android source and the pinned 7-Zip source archive are attached. See `SOURCE_AND_SIGNING.md`.

The Full APK can update a compatible GitHub install signed with the same key. Google Play and developer installs may use different certificates; update Play installs through Play and preserve their data rather than uninstalling to bypass a signature mismatch.

## Testing and limits

Fresh shared tests: 144 passed. Each Android flavor: 319 passed and one expected opt-in live-SMB skip. Both debug builds and lints passed with zero errors and 90 existing warnings per flavor. The signed Full APK passed four runtime tests on an isolated 16 KB emulator, including SMB reflection and native ZIP/RAR extraction. A local in-place upgrade retained the synthetic reading position and exported settings.

This is a **GitHub prerelease**, not Play availability or complete release acceptance. The exact-candidate synthetic restore check remains pending after a UI-capture failure and native-slot coordination wait. Final store declarations, release-note locales and live-server evidence reconciliation remain separate. TalkBack and second-account switching are deferred. Play-delivered upgrade acceptance, tester feedback and production eligibility remain open. Upstream native libraries are stripped; no complete native-symbol archive is available.
