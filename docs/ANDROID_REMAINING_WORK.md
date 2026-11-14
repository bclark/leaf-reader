# Android remaining work — 2026-11-13

Recommendation: **KEEP BUILDING**. This is a development readiness record, not release approval. Preserve the historical checklist and artifact-specific limits in the live Android engineering notes.

Prior S25 development candidate was `2b59a88824335fb40132a15c2f92edace350ea806f4466f78908687988184ff4`, runtime source `f3e5bb5cd62aa49593888577887124fa068620db`, with mounted-search autofocus and observed Jellyfin caption freshness. All217 inputs frozen before the build; [caption and outage evidence](local/android-sync-captions-20261113/IMPLEMENTATION.md). Existing Debug certificate, package net.digitalbc.leafreader,0.3.7/30700; no equivalence to Play Alpha established. Each flavor: 246 passes/one expected SMB-live skip, lints zero errors/87 warnings. Shared144 reports verified with up-to-date task. Earlier artifacts retain their own evidence limits.

Latest local release build is now **signed0.3.8/30800**, sourcecb7c96e, immutable216inputs unchanged: FullAAB28887595... and FullAPK9ee4d340... both verify against existing uploadcertificate3856FF77...1DD94A. Fresh shared144passed; each flavor246passed/one expectedskip; lints0errors/fatal87warnings. Debug/minified artifacts and static packaging checks pass. Exact signed emulator acceptance now passes bounded retained-data upgrade, reading/native rendering, export/restore/cancellation and cold restart on Android17/16KiB. No S25 mutation or upload; broader native/distribution gates remain. [Signed artifacts and receipts](local/android-release-prep-20261113/README.md).

## Assigned work and acceptance

| Work | Current evidence and next gate |
|---|---|
| Search clear and Series performance | One condition removes the coded 300 ms blank-query debounce, keeping nonblank typing debounce. Three candidate and three original fresh-process controls completed; native X/Back/reversal/peel and cover-position preservation checks pass their bounded assertions. Direct input-to-presentation linkage is absent. Item layout cost, memory/cache demand and broad scrolling acceptance remain open. [Evidence](local/android-cold-ui-attribution-20261113/README.md). |
| Real Jellyfin sync | Bryan authorizes the main account and **King Spawn Vol. 02** for progress/read-state writes. Record initial state, exercise both conflict choices, other-client reset/completion, relaunch/retry and retained-copy behavior, and reconcile final state. Named main-account volume now passes bounded online browser→device progress/completion/reset, durable pending recovery, defer/relaunch/review, both choices, stale-choice rejection and independent browser readback. The exposed search-focus crash is fixed and its exact native path passes. Earlier candidate restored page1/unplayed. Latest fresh native sync and independent browser Resume established page6/unplayed; old open Reader display1 was stale evidence, cause unproven. Loaded-caption fix passes scoped native checks. Actual owner-disconnected S25 retained page7 across cold restart while desktop server remained6. Actual reconnection and explicit queued retry independently verified server7/unplayed; restored fresh baseline6/unplayed in both clients and after cold restart/explicit sync, original copy unchanged. account switch/storage interruption and broad parity remain open. Retained145pages/367.5MB. [Exact live results](local/android-real-sync-20261113/browser-resume/README.md). |
| Physical acceptance | Only S25 is available. Native accessibility, real IME composition, large text/themes, remaining network scenarios, storage interruption and final-artifact reading checks remain scoped separately. S25 cannot establish minimum-OS, Fold/tablet or another vendor's behavior. Do not silently waive those gates. |
| Integration | Isolated sourcecb7c96e now has a fully built/signed/static-verified0.3.8/30800 candidate with216frozen inputs unchanged. AAB/APK signatures match the existing uploadcertificate; payloads match the unsigned rehearsal excluding signature metadata, both mappings/F-DroidAPK byte-identical. No movinglive/iOS/CI/store merge. Signed device/upgrade/backup/cancellation/privacy/source/rights/Play gates remain open. [Exact results](local/android-release-prep-20261113/README.md). |
| Privacy and licensing | Offline inspection verifies eight packaged license texts/notices, eight native libraries' 16 KiB ELF alignment, compiled backup/network rules and full-flavor tips disabled. Hosted Android wording is older than current Jellyfin flows and still describes Ko-fi for the full flavor. An unpublished replacement draft and data-flow inventory are prepared. Source offer/replacement rights, artwork provenance, actual transfer, final signed AAB/F-Droid artifacts and approved Console declarations remain gates. [Review](local/android-artifact-preflight-20261113/REVIEW.md). |
| Play readiness | Refreshed Console shows Alpha 0.3.7/30700 available to testers, internal 0.3.6/30600 active, and two uploaded bundles total. Production inactive; only **2 opted-in testers**, with **12 for 14 continuous days** required and Apply disabled. Highest observed consumed code is 30700; individual drafts, Publishing overview and upload certificate are not yet fully inspected. No mutation-ready state, next code, invitation, upload or submission is authorized. [Inventory](local/android-artifact-preflight-20261113/CONSOLE_READONLY.md). |
| Onboarding | Migration/success/cancel/process-death plan prepared against pinned handoff96975b3, preserving Files and optional Jellyfin. Figma context failed its Starter MCP quota; current visual frames were unavailable. Ready/planning only, no visual implementation guessed. Requires current design context, official asset licensing and exact native acceptance. [Plan](local/android-onboarding-plan-20261113/IMPLEMENTATION_PLAN.md). |

## Baseline and release limits

Latest live read: main21b3fac with37tracked changes, preserved; it is not the isolated Android candidate. The original optimized development control4899067f and candidate d24091f5 have separate frozen artifacts. Earlier control source switches were restored at their historical checkpoints; the current isolated release source is recorded above.

The earlier 205-input records remain valid for their recorded scope: 204 app/shared source paths and one QA helper. The corrected inventory adds 12 tracked build/config inputs; `app/build.gradle.kts` contains the native-symbol configuration missing from the first proposal. No new source build or full pre-build freeze is implied by this reconciliation.

Current design reference is whole-page reading, forest/paper/gold, Anton/Archivo, full rear artwork and intentional downward Series peel. The obsolete panel/model architecture is not a restoration target. Preserve device/SMB originals, auth boundaries, privacy and manually retained Read/Continue copies.

No local fixtures or unit suites close Play-delivered upgrade acceptance, actual tester feedback, privacy publication or production eligibility. App modification is not permission to change CI, merge the moving live checkout, publish a policy, upload, submit for review or release.


Console follow-through: Publishing overview checked read-only, managed publishing off, one unsubmitted Alpha tester-feedback-channel change to https://digitalbc.net/leaf/support/. No new-release submission inferred. Native navigation timed out and reconciliation found Chrome focus away from Console; UI input stopped. Individual drafts/upload certificate remain open; no release state changed. [Updated inventory](local/android-artifact-preflight-20261113/CONSOLE_READONLY.md).


## Local 0.3.8 rehearsal checkpoint

The unsigned rehearsal is built and statically verified. Q-028 explicitly authorized functional build/tests alongside iOS; no device tests or performance measurements occurred. Fresh shared144 and each Android flavor246passes/one expected SMB live skip; zero lint errors/fatal and87warnings per flavor. Both debug APKs, minified Full AAB/APK and F-Droid APK built, both R8 mappings retained. Bundletool validation, exact identity, eight license texts, model/runtime exclusion, ELF/ZIP16KiB alignment and AAB PAGE_ALIGNMENT_16K configuration pass. No full native debug-symbol metadata was produced from stripped prebuilt libraries.

Q-026 secure access is verified: existing Leaf keystore and Keychain item opened, public certificate matched, and a local signature verified without key/security changes. The first signed build was interrupted because iOS resumed during Keychain approval; no signed release acceptance. Q-029 directs waiting for coordinated iOS phase completion; a guarded fresh retry is prepared. Q-027 artwork provenance blocks distribution evidence, not this completed rehearsal. Next is a fresh signed attempt and exact signed/device/upgrade/privacy/source/declaration acceptance. KEEP BUILDING; no upload/publication. [Results and receipts](local/android-release-prep-20261113/README.md).


Signed retry coordination: the local driver now checks native occupancy before and immediately after Keychain access; three mocked regressions pass, including the reproduced race. The first attempt and logs are preserved as interrupted; no Android worker or automatic retry remains. Resume only after the active iOS phase is confirmed finished. [Signing status](local/android-release-prep-20261113/signing/README.md).


## Serial signed retry completed

The owner said continue after the iOS phase explicitly finished. Pre/post-Keychain native checks and shared/candidate locks passed; fresh signed run completed with existing keystore unchanged. Prior interrupted attempt and overlap qualification remain preserved. No automatic worker, device tests, performance capture, install or upload. Q-026 access/signing is verified; Q-027 and final acceptance remain. Next: dedicated compatible native QA, preserving the S25 library and existing signatures.


## Signed emulator QA completed — 2026-11-14

Exact signed0.3.8 APK9ee4d340/sourcecb7c96e upgraded authentic same-key0.3.7 in-place on existing Pixel_10_Pro Android17/16KiB emulator. Generated comic page5/12 and one-member custom list retained. Native export, picker/preview cancellation, KeepLocal7 and Overwrite5 with cold restart, generated7zip-via-CBR native backend and PDF pixel rendering/navigation pass. All216frozen inputs unchanged; no rebuild or runtime edit. [Bounded native evidence](local/android-release-prep-20261113/device-qa/README.md).

Next: varied-preference/remote-credential backup coverage, true RAR4/RAR5 files, actual offline/network/cancellation/transfer and physical final-artifact acceptance; resolve public privacy/corresponding-source/replacement/artworkQ027/declarations/key-recovery evidence before any Play handoff. Preserve S25: differing development certificate cannot be bypassed with uninstall. Emulator coverage does not waive minimum-OS/form-factor/vendor or Play-delivered gates. KEEP BUILDING. Native slot released, emulator stopped with task data retained; board broad statuses remain honest, noDone/upload/publication.


## Remaining acceptance and About correction — 2026-11-14

The existing signed candidate cb7c96e / APK9ee4d340 now has additional native Android17/16KiB evidence: genuine stored RAR4/RAR5 two-page import/render/navigation, changed grid6→2 restore with cold export equality across five comics/list/otherpreferences, synthetic remote-reference backup roundtrip with strict portable field allowlists, malformed RAR/unsupported backup recovery and missing-comic safe skip. Final portable data was unchanged after the error checks. These are bounded emulator results; compressed/solid/encrypted/multipart RAR, physical/network/accessibility and OS transfer are not closed. The emulator was stopped without wipe/reset. [Results](local/android-release-prep-20261113/remaining-acceptance/README.md).

Native About exposed an obsolete ML-panel claim. One string is corrected in isolated source6480b62, clean, using whole-page reading/current optional SMB+Jellyfin wording. A separate immutable216-input0.3.8/30800 attempt022254 is prepared; only MenuScreen.kt differs from cb7c96e. No old gate is transferred automatically. At02:31UTC, the foreground signed driver waits for the existing Leaf Keychain prompt before Gradle/log creation (Q-030); no corrected build or native About pass yet. Original signed artifacts remain the current verified candidate.

S25 remains unchanged at2b59. A separate compatible development-signed QA copy63b57769 has489payload entries byte-identical to canonical9ee4d340, but was not installed. Physical checks remain pending, and neither development nor upload signing proves a Play-delivered upgrade. Source/privacy/declaration/recovery review materials are concrete and local; artworkQ027, independent recoveryB05 and actual publication/owner declarations remain unresolved. Fresh read-only Console confirms two uploaded versions30700/30600 and matching public upload certificate; existing unsubmitted feedback-channel change untouched. KEEP BUILDING. No upload/publication, live/iOS merge, device/account/auth/security setting change or boardDone.


## Corrected signed candidate and bounded S25 acceptance — 2026-11-14

Q030 satisfied; corrected6480b62 signed0.3.8/30800 AAB0878eaa1/APK5d96f0a5 selected, all216inputs unchanged. Freshshared144/eachAndroid246pass+1expectedskip; zero-error lints,87warnings each. Canonical16KiBemulator About/storedRAR4+5/changed-grid restore pass. Separate payload-identical development-signed3a27da2b upgraded S25 in-place without erase; populated data/account preserved, generatedRAR5spread/coldresume/PDF and settings-onlyrestore pass. Final cleanup export exactly equals original2comics/0lists/7preferences excluding appversion/exporttimestamp. This does not establish Play-delivered upgrade or spoken accessibility. Bounded current candidate backup/device/native16KiB entries updated with evidence and limits; no old gates blindly transferred. [Current receipts and limitations](local/android-release-prep-20261113/remaining-acceptance/ABOUT_QA_20261114.md).

KEEP BUILDING. Approved main-account fixture open atpage6 awaiting owner-controlled network disconnect for actualoffline/reconnect. Broader sync/cancellation/account/storage, accessibility/devicebreadth/OStransfer and privacy/source/artworkQ027/independentrecoveryB05/declarations/authorizeddistribution remain open. No upload/publication/push/merge/Done; historicalchecklist preserved. No running build/emulator/worker; release native slot while awaiting owner, recheck before next device step.


## Android acceptance evidence reconciliation — 2026-11-14

Resumed owner-assigned QA against isolated6480b62 and signed0.3.8/30800 FullAPK5d96f0a5; S25compatible3a27da2b unchanged. Read current notes/board; no competing xcodebuild at bounded device observation. Saved screen confirms approvedfixture page6/145. Coarse read-only connectivity shows Wi-Fi/mobiledata enabled and active defaultnetwork; no settings changed, no intentional page advancement/server writes. Owner disconnect prompt remains pending. Native lock released after observation; no build/emulator/unattended worker.

Independently rehashed all64 fresh test/lint reports. Both full and F-Droid AppDatabaseMigrationTest suites have11passes, zero failures/errors/skips. Reviewed independent populated schema4/pre-revision5 fixture SQL, normal Room migration/schema9 validation, disk reopen and comic positions/list/link/pending-write assertions. Recorded bounded upgrade_shipped_v4 and upgrade_pre_revision_v5 acceptance passes; these are automated fixture gates, not physical shipped DB or Play-delivered upgrade.

Reviewed exact current native symbol inventory: stripped upstream .symtab absent, .dynsym present; full symbol archive unavailable, exact R8 mappings retained. Qualified native_symbols_review passes under release-runbook allowance, preserving diagnostic limitations. No binary/source rebuild/symbol substitution. Corrected previous three evidence-backed acceptance statuses from passed to helper-required pass; evidence/limits retained. Six local gates now pass; offline/sync/privacy/notes and allpostPlay remain pending. Evidence: docs/local/android-release-prep-20261113/remaining-acceptance/about-migration-symbol-review.json.

KEEP BUILDING. Q027artwork/B05independent recovery and privacy/publicsource/declarations/notes, actual network/two-client/cancellation/account/storage, spoken accessibility/largefont/contrast, devicebreadth/OStransfer and eventual separatelyauthorized Play-delivered acceptance remain. No upload/publication/push/merge/Done. Engineer waiting for owner Offline response, no automatic worker/retry.


## Android S25 bounded offline/reconnect — 2026-11-14

Current6480b62/canonical5d96f0a5/S25payload-equivalent3a27da2b: verified actual network loss, approvedfixture6→7/coldrestartlibrary7/reopenedreader7 and unreachablebanner. Owner restorednetwork; freshcatalogue/targetedapprovedfixtureSync returns withoutvisibleerror/conflict, Continuepage7 retained. Roundedserver4% is not exact independentpage/ticks/queuedack proof; sync/cancellation/two-client gate stays pending. Sevenboundedlocal gates pass includingoffline; no broadclosure. Prior device6/server7 prompt left unresolved; no silentchoice/reset. Phone staysonlinepage7; no unrelatedcomic/account/settings changes.

[Exact observations and limitations](local/android-release-prep-20261113/remaining-acceptance/S25_NETWORK_20261114.md). KEEP BUILDING; independentclient/livecases, accessibility/devicebreadth/OStransfer and privacy/rights/recovery/distribution gates remain. Nativephase FINISH/released, no worker/build/emulator/upload/publication/Done.


## Current source distribution packet / client acceptance — 2026-11-14

Current6480b62 built-signed source archive freshly matches all216frozeninputs; fiveartifacts/mappings and pinned875f38a upstreamsource rehashed. New unpublished DISTRIBUTION_REVIEW_6480.md uses exactcurrentarchive/artifact identity and preserves priorcb7 evidence. About-only delta leaves priorprivacyflow analysis as supporting evidence, not owner/legal/declaration approval. No source/publicprivacy/Console mutation; Q027rights/B05independentrecovery remain.

OwnerprovidedChrome approvedfixture session. Resumeonce returnedtodetail/noexactreader; afterreloadPlay/Markplayed/noResume. Potentialbrowserprogresschange requires freshLeafposition inspection/restoration before this test closes; noMarkplayed action, exactserverpage or two-clientpass. CurrentiOSlayoutbuild owns nativewindow; S25notaccessed. Sevenboundedlocalgates/KEEP BUILDING; broadergates/historypreserved. Evidence: docs/local/android-release-prep-20261113/remaining-acceptance/DISTRIBUTION_REVIEW_6480.md and browser-independent-6480-observation.json.


## Android import-session isolation finding — 2026-11-14

ANDROID-F01/P2 source-confirmed on exact6480b62: archive imports are not included in disconnect/session-job cancellation or session-generation publication checks; batch selections can be used with a newly loaded account/server. SharedViewModel persists across enabled Menu navigation. Runtime held-request reproduction/fix pending; existing progress-account tests do not cover imports. No observed real-account loss or leakage claimed. Sevenboundedlocalpasses remain, broad sync/cancellation/account/storage gate pending.

Fix original auth/URL/generation binding using existing cancellation/session machinery; test blocked archive disconnect/switch, two-item stale batch, and prepublication/relink rollback while preserving committed copies/history/list/outbox. [Exact review and acceptance](local/android-session-review-20261114/README.md). Read-only review, no newbuild/test/source/device/server action. KEEP BUILDING; no broadDone or distribution. Prior browserprogress reconciliation still pending coordinatednativewindow, not waived.
