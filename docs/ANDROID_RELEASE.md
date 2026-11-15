# Leaf Android releases

## The easy path

Ask Codex in this repository: **“/deploy-play prepare 0.3.8”** or **“Cut the next Leaf Android release for closed testing.”** It handles source review, the build, useful notes with a little personality, and the authorized Play steps. Say whether the destination is a local candidate, a Play draft, closed testing, or production. You get one release preview before the next action that needs your approval.

This extends the existing `deploy-play` skill, already linked from Bryan's personal skills. GitHub tags, F-Droid publishing and Google Play are distinct destinations. The old GitHub workflow is retained as an explicitly selected mode. No workflow/CI settings were changed.

## Local helper

Python 3.11+, the repository's Java/Android toolchain (SDK/build-tools 36.1.0), and Google's official [bundletool JAR](https://github.com/google/bundletool/releases) are required. The helper uses only Python's standard library. The Mac has the Android SDK; bundletool and a Play upload CLI were **not found during setup**. No software or credentials were installed. The skill uses Play Console in the authorized browser for remote steps.

```sh
# First record a real, fresh Console read as described below.
python3 scripts/release_android.py prepare \
  --version 0.3.8 --version-code 30800 --since v0.3.7 \
  --play-state /PRIVATE/LOCAL/play-state.json
# These numbers are examples, not reserved or live-verified choices.
# Review Android/shared tracked edits before adding --allow-dirty.

# Use the exact candidate path printed by prepare. No upload.
python3 scripts/release_android.py build CANDIDATE \
  --android-sdk "$HOME/Library/Android/sdk" \
  --bundletool /PATH/TO/bundletool-all-VERSION.jar --signed
# Omit --signed for an unsigned rehearsal; it cannot pass Play handoff.
# Add --offline only when all Gradle dependencies/toolchains are cached.

# After local candidate acceptance, notes review and another fresh Console check:
python3 scripts/release_android.py handoff CANDIDATE \
  --play-state /PRIVATE/LOCAL/play-state.json
python3 scripts/release_android.py status CANDIDATE
```

`handoff` writes an **internal/closed-testing upload** preview locally. Initial production/open-track uploads are blocked; promote the same tested artifact later through the release skill. **Neither it nor any other helper command uploads, submits, starts a rollout or reads live Play state.** The release skill performs authorized Console work. `status` is local state only.

Candidates live under ignored `app/build/play-releases/`, in distinct timestamped attempt directories. The development checkout's version defaults, F-Droid metadata and tags do not change. Only the candidate snapshot's versionName/versionCode are overridden. Failed and unsigned attempts are preserved; prepare a new attempt using the same code only if it is still unused in Play. Local preparation is not a remote reservation. Coordinate concurrent operators and recheck immediately before upload.

Snapshots include tracked Android/shared/Gradle build inputs, not unrelated iOS edits or private `.env`/SDK/signing files. Unknown untracked inputs block preparation. The manifest records source hashes, base/head, the exact version override, baseline patch, logs, test XML and artifact checksums. Do not share whole candidate directories: they can contain private unpublished source or logs. Only explicitly selected approved release artifacts go to a store.

## Fresh Console evidence

The release skill reads the actual Console and writes this local JSON. If access is unavailable, stop selection/upload and report that limit; do not fill the record from old notes. This is an auditable observation, not an authenticated API attestation or approval. Place it under an ignored local build folder, not in Git.

```json
{
  "package": "net.digitalbc.leafreader",
  "checked_at": "ACTUAL_UTC_TIMESTAMP_WITH_TIME_ZONE",
  "all_tracks_and_drafts_checked": true,
  "max_version_code": 0,
  "track": "EXACT_VERIFIED_TRACK_IDENTIFIER_OR_NAME",
  "track_kind": "closed",
  "upload_certificate_sha256": "ACTUAL_PUBLIC_UPLOAD_CERTIFICATE_SHA256",
  "evidence": "Actual Console pages/screenshots and what was checked"
}
```

Replace every placeholder from live evidence. `max_version_code` is the highest **consumed** code across all tracks, drafts and uploaded bundle history, including unused/inactive bundles, not just the production release. The example zero is not a real observation. Valid track kinds are internal, closed, open and production. Records older than 24 hours or future-dated fail validation; the operator must still recheck immediately before mutation. The helper requires a code higher than both source and the observed maximum. Historical 0.3.7 used **30700**, not 307; AGENTS.md's older formula is not authoritative.

## Signing and artifact checks

Use the existing upload key and securely injected `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` environment. KEYSTORE_FILE must be an absolute path. Do not paste credentials into chat or commands, print the environment, copy a keystore into the snapshot, or create a replacement key. The helper never looks up, stores or changes credentials. An authorized existing Keychain arrangement may supply them to the build process without displaying values.

The signed full AAB is validated with bundletool, its actual manifest/package/version checked, and its JAR signature and public certificate matched against the observed Play **upload** certificate. The full APK's signature and identity are checked independently. Both R8 mappings are retained. The F-Droid APK is deliberately built without signing variables and remains unsigned for its own distribution process.

Upload signing and Play app signing are different. Do not uninstall an existing Play app to bypass a signature mismatch. Before upload, use populated predecessor database fixtures to verify the normal Room upgrade paths and a dedicated test install for signed/minified behavior. After the authorized test upload becomes available, verify a compatible Play-delivered in-place upgrade before promotion. This post-Play gate is intentionally not a prerequisite for the first test upload.

The helper inventories embedded native symbols; it does not manufacture missing data from stripped third-party libraries. Native symbols are not R8 mappings. Review 16 KB ELF alignment/packaging and actual device behavior and record coverage. The known 0.3.7 native-symbol warning is diagnostic quality, not proof of failed submission. New symbol configuration or upstream symbol downloads require a separate reviewed source change; never swap immutable historical artifacts just to clear a warning.

## Release notes and acceptance

`notes/en-US.txt` starts as a blocked draft. Write a short personable opener plus practical benefits, at most 500 characters per locale. Example tone, **not publishable claims for an unreviewed candidate**: “A little polish for your next chapter.” Explain visible changes and where to use them; skip commit jargon and vague promises. Cover every active Play release-note locale, with review for translations. The helper catches empty/overlong/placeholder text; the skill verifies factual accuracy and locale coverage.

Full shared and both Android unit suites run with `--rerun-tasks --no-build-cache`, followed by debug builds/lint and minified release builds. XML evidence must exist, with no failed tests. Migration, backup and Jellyfin repository suites must be present. Only the explicitly opt-in `SmbLiveTest` may skip, and its skip is reported; no live server is contacted by the standard build. Cached dependencies are fine, cached test outcomes are not. Full test results are retained in the isolated attempt, so a later focused developer run cannot overwrite the release evidence.

Fill the pre-upload `gates` section in `acceptance.json` only after real local checks, binding it to the exact AAB checksum. Every pre-upload gate requires `status: "pass"` and a concrete evidence reference:

- Populated database-fixture upgrades from shipped schema 4 and the earlier pre-revision schema 5, including pending read/progress intent. Test Room's normal opening/migration path, not a direct manual call to a migration. These local fixtures do not establish Play-delivered upgrade acceptance.
- Export/restore of reading progress, lists and preferences, plus omitted credentials/hosts/paths. This is the reading-data backup feature, separate from encryption/export law.
- Signed/minified device imports, reading and restart/offline behavior on a dedicated compatible test installation. No debug-only substitution or uninstall of existing user data.
- Jellyfin reconnect, cancellation/account switch and real progress effects where explicitly authorized. Do not invent live-server success from mock tests.
- Privacy/data-flow/declaration review, 16 KB checks, actual native-symbol coverage review, and notes review.

A `pass` for symbol review can acknowledge genuinely unavailable upstream symbols and the resulting nonblocking warning. It must not claim complete symbols. Legal classification and account declarations need correct facts and authorization; a checklist does not confer either. Android has no Apple's `ITSAppUsesNonExemptEncryption` switch to copy.

The separate `post_play` section starts pending and must remain honest. Once the exact uploaded bundle is available, record a Play-delivered in-place upgrade with preserved data, real testing feedback and current production eligibility. The helper does not validate those Console/device outcomes or authorize promotion. The release skill checks them before expansion to production.

## Play upload, review and finalization

After testing handoff succeeds, `/deploy-play` uses the authorized Console session to upload the **exact full AAB**, enter reviewed release notes, inspect warnings and handle only the requested next stage. No API credentials are needed for that browser path. If the browser/session is unavailable, report the exact blocker rather than installing/configuring a service account or promising an upload.

Saving a draft, sending changes for review, starting a closed test and publishing production are distinct. Some Console buttons combine actions; inspect their actual consequences and Publishing overview before proceeding. Preserve managed publishing and existing countries/tester settings unless authorized to change them. For production, require post-Play delivery/upgrade acceptance and testing feedback, promote the exact already tested versionCode without rebuilding/reuploading, verify account access and approval, then get concrete rollout scope (percentage, countries and timing). Never promote an Alpha build automatically.

Record the candidate identity, upload attempt time, exact track, checksum and final Console state. After a timeout, inspect bundle/release history before retrying; a failed UI response is not proof the upload failed. Verify processing, review and actual availability separately. A submitted release is not necessarily live.

For applicable personal accounts, check the current closed-test eligibility requirement (previously 12 testers opted in continuously for 14 days). An Alpha draft, APK download, invitation or internal test does not establish the clock. Verify the closed opt-in URL and actual participation. Production-access applications need truthful engagement/feedback evidence and explicit scope. Do not send tester invitations without authorized recipients.

## Setup verification and current limits

Setup passed **30 Android offline helper tests** and **27 existing iOS regression tests** (57 total), with mocked Gradle/signing/Play fixtures, not a real release. YAML/frontmatter metadata and whitespace checks passed. The standard skill validator could not run because available Python interpreters lack PyYAML; no dependency was installed. No new real candidate, version bump, build, signing, credential change, upload, submission, rollout, commit, tag, push or Play setting change occurred.

At setup, source was 0.3.7/30700 and the schema-5 revision-column upgrade defect blocked helper builds. **November 10 follow-through:** current working-tree source now uses Room schema 6 with a preserving migration for both v5 layouts. Populated v4 and both v5 upgrade paths pass normal Room opening and database-reopen checks, including pending progress/read/unread intent. Fresh shared and both Android debug unit suites passed. See [Android engineering notes](ANDROID_ENG_NOTES.md) and [compliance evidence](../RELEASE_COMPLIANCE.md). These local regression results do not establish signed/minified candidate or Play-delivered upgrade acceptance. App version remains 0.3.7/30700; historical Alpha status is still owner-reported.

The next real run also needs live Play code/certificate/track reads, official bundletool, existing signing access and full candidate/device evidence. Browser upload/finalization and the installed Android build toolchain have not been exercised by this setup. See `ANDROID_ENG_NOTES.md` and `RELEASE_COMPLIANCE.md` for historical evidence and open release gates.

**November 11 backup follow-through:** source now uses Room schema 7 with a preserving 6→7 migration and a pending-preference recovery journal. Populated v4/v5/v6 upgrade paths pass; a development-signed minified QA APK was installed and tested for native restore, absent-fixture skip, local re-import and cold-start progress. This supersedes schema 6 as the current source schema only; all historical setup/evidence remains scoped to its recorded artifact. See [Android engineering notes](ANDROID_ENG_NOTES.md) and [compliance evidence](../RELEASE_COMPLIANCE.md) for exact hashes, automatic-recovery test boundaries and open C-04/final/Play gates. App version remains 0.3.7/30700; no store release occurred.


## Android development checkpoint · 2026-11-13 07:41 UTC

Current isolated Android development source has Room9 and owner-approved ask-choice/manual-retention behavior, with exact development evidence in [engineering notes](ANDROID_ENG_NOTES.md) and isolated [compliance checkpoint](../../leaf-worktrees/android-agent-bootstrap/RELEASE_COMPLIANCE.md). Both current flavor suites243passes/one expected SMB-live skip and lints0errors87warnings; shared144JVM result is carried from unchanged sources. Experimental optimized3821df96 is development-key signed; installed/pulledhash verified but native launch/search/scroll acceptance is pending after the S25 window was interrupted by Settings foreground. Three runtime/QA files remain uncommitted, test-only corrections3041af4. KEEP BUILDING. Existing helper/release/signing/Play-track/upgrade/publication authorization and all gate limits/history remain unchanged; no release or upload performed.


## Resumed S25 development acceptance completed · 2026-11-13 13:01 UTC

The renderer trial was rejected after repeat search-latency outliers and all three trial source files restored. Clean43f5ee5 freshly passes243tests/one expected SMB-live skip per flavor and both lints0errors87warnings; optimized build1m20 produces exact original4899067f, matching the installed/pulled/native-checked APK. Restored peel/Back and160MB/pg3of300 pass, All/top/searchclosed, owned QA/temp cleanup complete. Optional Play Protect APK submission declined; no protection-setting change or build upload by agent. KEEP BUILDING. Current signing/Play-delivered upgrade/privacy/licensing/Console/track/physical gates remain separately open; no release stage authorized or performed. See [engineering notes](ANDROID_ENG_NOTES.md) and isolated [compliance](../../leaf-worktrees/android-agent-bootstrap/RELEASE_COMPLIANCE.md).
