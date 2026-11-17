# Android first-launch onboarding

Refreshed 2026-11-15 against merged main `cbe3a7d2b9a34e9975d8910301b5f85de75da1f5` (0.3.8 / 30800). This is an implementation handoff, not completed product code or device acceptance. It supersedes the source-baseline and integration advice in the private November 13 plan; that plan and its failed design-access receipts remain historical evidence.

## Source and scope

Use a clean, isolated task branch from freshly reconciled main. The refresh checkout is `codex/android/library-onboarding-handoff`, based on the exact main above. Do not use the older `ee84175` search-experiment base or copy the moving dirty IDE checkout. The installed S25 development APK and existing local data stay untouched during planning.

The bounded feature is two first-launch cards: local Files import first, optional Jellyfin second. Preserve existing formats, multi-file import, SMB entry points after onboarding, authentication and local-HTTP consent, whole-page reading, manually retained copies, conflict choices, Library navigation and intentional Series peel. No telemetry, discovery, authentication or server requests merely from rendering either card. No new importer or authentication client.

## Approved design references

Design checkout: `leaf-reader-design@21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8`. The following reviewed files and exports match their committed blobs, despite unrelated dirty design files:

- [Onboarding behavior and copy](https://github.com/bclark/leaf-reader-design/blob/21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8/docs/ONBOARDING-HANDOFF.txt), blob `87096697fc9889a4fb6d4d9e80fffe87a7de7df1`.
- [Whole-app visual handoff, revision 4](https://github.com/bclark/leaf-reader-design/blob/21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8/docs/APP-PHONE-FLOW-2026-11-14.txt), blob `4c58d68f97c54015f6e26943814c450a7f6385c8`. Screens 1 and 2 govern appearance; onboarding behavior above governs completion and cancellation.
- [Local design fallback](https://github.com/bclark/leaf-reader-design/blob/21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8/docs/FIGMA-FALLBACK.txt), blob `fe2b5ff2dc572567d323dcb849387191a51f9e04`.
- [Phone welcome](https://github.com/bclark/leaf-reader-design/blob/21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8/assets/app-phone-flow/01-welcome.png) and [optional Jellyfin](https://github.com/bclark/leaf-reader-design/blob/21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8/assets/app-phone-flow/02-jellyfin-card.png). Matching exports are present under `light/`, `tablet-dark/`, `tablet-light/`, `portrait-dark/` and `portrait-light/` in that same folder. All twelve files were verified against Git; dark phone, light welcome, landscape Jellyfin and portrait welcome were visually inspected during refresh.
- [Original Figma page](https://www.figma.com/design/w3EYA6kPYW6g2BPErJ865X?node-id=16-19). A fresh context request returned no selected layer, then screenshot access hit the Starter quota. No live high-fidelity Figma context or screenshot was obtained. Session: `96f9cc1f-969d-4ace-881a-1ceb49f784c2`. Do not claim that the static exports are live Figma context or native captures.

The approved exports now resolve the old plan's lack of durable visual references. They show a forest header, existing Leaf mark, Anton headings, Archivo body/buttons, gold Files action, bordered secondary actions and themed sage/forest Jellyfin action. Tablet onboarding is a centred column up to 520 wide. Apply safe areas and scrolling/growing layouts for short windows and large text; do not copy absolute image coordinates or paint the export as a screen. Reuse existing `LeafWordmark`, `ic_leaf_logo`, fonts and semantic theme roles. Do not alter the global palette as part of onboarding. AMOLED and intermediate widths require native verification; no new approved drawing is inferred.

The Jellyfin graphic slot is explicitly a placeholder in the export. Ship the official Jellyfin artwork with its attribution/license, never the dashed placeholder or a recreation. Reuse an already verified official source/provenance packet if available; otherwise pin the upstream revision, checksum and CC BY-SA 4.0 notices before bundling. Leaf artwork distribution provenance remains the separately tracked rights gate; this handoff does not resolve it.

## Current integration findings

| Current source | Existing behavior | Required change |
| --- | --- | --- |
| `MainActivity.finishStartup` | Accesses `app.libraryRepository` to recover backup preferences before `AppNavHost` | Resolve first-launch eligibility before that first Room-backed access; preserve restore recovery and retry/close behavior |
| `ComicReaderApp` | Database, settings and repository are lazy singletons | Capture pre-start install evidence before constructing/migrating data that would make every fresh launch look existing |
| `AppSettings.loadPreferences` | Migrates nonempty `chika.settings` into empty `leaf.settings` | Preserve migration and prior markers; use bounded durable onboarding state and detect write failures |
| `AppNavHost` | Owns Library VM and catalogue VM before the Library route | Gate initial onboarding before normal Library/catalogue effects; retain the existing path after completion |
| `LibraryScreen` | Owns `OpenMultipleDocuments`; nonempty result calls `LibraryViewModel.importComics` | Share one stable native-picker/import path with the onboarding entry; retain all MIME types and multi-selection |
| `LibraryViewModel.importComics` | Counts confirmed `repository.importComic` successes but exposes final outcomes only as messages | Publish a structured, operation-scoped outcome; never parse messages or infer success from selection or `importing == false` |
| `AppNavHost.onConnectJellyfin` | Already opens `menu?section=jellyfin` | Reuse this direct setup route; the older plan's missing-route finding is superseded |
| `AppNavHost.onOpenJellyfin` | Selects All and requests catalogue retry | Do not confuse catalogue browsing with setup |
| `MenuScreen.connectToJellyfin` | Handles HTTP consent, submission, progress/error; observes repository Result | Add a narrowly scoped success callback to the same existing setup flow; preserve ordinary Settings behavior |
| `LibraryRepository.connectJellyfin` | Authenticates, stores auth/configuration, resets session guards and returns success | Complete onboarding only after this confirmed outcome and durable onboarding save; preserve existing session/outbox semantics |

Current code has no two-card first-launch implementation. The existing post-skip empty Library is a separate surface and must remain available.

## Lifecycle contract

Persist only the three destinations needed here: Welcome, Optional Jellyfin and Complete. Picker/setup presentation and an operation identity must survive the appropriate recreation boundary; avoid a generic workflow engine.

Before first Room access, retain any prior onboarding marker. If missing, use conservative evidence of an existing install: existing database/sidecars, existing/legacy preferences or configuration, and package install/update history. Empty library contents are not a fresh-install signal. Ambiguous package metadata must preserve existing reading access rather than forcing Welcome. Do not read or copy real user databases or credentials to test migration; use isolated fixtures.

For a newly eligible install, durably record Welcome before Room creation can obscure that decision. Preserve backup-preference recovery. A failed marker write must be observable and must not falsely report persistent completion; preserve normal access for an existing/ambiguous install. Test persistence failures explicitly.

| Starting state / action | Result |
| --- | --- |
| Welcome: Choose a file | Existing native multi-file picker; remain incomplete while pending |
| Picker cancel or no retained successful import | Return to Welcome with focus restored; preserve error/retry/skip |
| At least one confirmed retained import | Save Complete, then Library; partial import outcomes/errors remain visible |
| Welcome: I use Jellyfin | Save Optional Jellyfin; no discovery, authentication or progress write |
| Welcome: Maybe later | Save Complete, then Library |
| Optional: Back or system Back | Welcome |
| Optional: Connect Jellyfin | Existing setup/HTTP-consent path; remain incomplete while pending |
| Setup cancel, rejected sign-in, unreachable server or consent cancellation | Optional Jellyfin with retry/skip and focus restored |
| Confirmed connection success | Durably save Complete, then Library |
| Optional: Use without Jellyfin | Save Complete, then Library |
| Complete: relaunch or Back | Ordinary Library flow; completed onboarding does not reappear |

The visual handoff's shorthand “until any choice” does not mean that pressing Connect, entering Optional Jellyfin or selecting a file completes onboarding. The governing onboarding contract requires confirmed success or explicit skip.

Suppress duplicate presentations/imports from repeated taps. Preserve selections, settings input and correct return destinations across rotation. For process death after repository success but before completion save, reconcile confirmed retained output or matching stored successful connection through existing local APIs; a configured-URL boolean or toast is not success evidence. Do not introduce a network request to decide whether to display a card. Cancel after partial multi-file success must retain successful imports; record the completion/result policy explicitly in tests.

Completion belongs to this installation's lifecycle. Preserve the existing reading-data backup scope; do not silently add auth, hosts, paths or onboarding state to portable exports or reset onboarding during restore.

## Proposed implementation PRs

These are bounded engineering slices, not claimed implementations or automatic task dispatch. Recheck main and dependencies before each branch.

1. **Startup and persisted-state foundation.** Add the minimal migration-aware state/store before Room initialization; preserve legacy preferences, restore recovery and prior completion. No new visible screens in this slice. Verify fresh, existing-empty, existing-nonempty, prior marker, legacy/configured-source, package-info failure, repeated startup and failed durable write. The first-launch decision must not be altered merely by recomposition or the current library count.
2. **Confirmed outcome and presentation wiring.** Reuse the current picker/importer and setup route, expose scoped confirmed outcomes, preserve cancellation/HTTP consent/errors, and retire pending operations safely. Default ordinary Library/Settings behavior stays intact. Test partial success, stale outcomes, cancel/retry, repeated taps and process recreation; use synthetic archives and local mock servers, not the owner's account or library.
3. **Two-card UI and focused native acceptance.** Add the approved welcome/optional screens, resources and official-artwork notices, wire Back/focus/skip, and compare native results with approved phone/light/tablet references. Complete design-context access requirements before visual code under the Figma workflow; the context/quota limitation remains explicit. This refresh is not a visual-implementation permission exception.

Avoid landing a transient source that would mark genuinely new installs complete before UI wiring can use the persisted Welcome state. Any temporary gating must preserve stored intent and be removed deliberately when wiring lands. Do not change the existing S25 install merely to simulate a fresh user; use a dedicated test package/emulator and synthetic data. Native build and device checks remain coordinated.

## Acceptance matrix

All product acceptance remains open until exact source/artifact evidence exists.

- [ ] Fresh eligible install shows Welcome; existing empty/nonempty/legacy/configured installs and prior Complete preserve ordinary startup.
- [ ] Choose a file uses the existing native picker directly, all supported formats/multi-selection retained; cancel restores focus; invalid file fails visibly; retry can retain a valid synthetic archive and read it with no network.
- [ ] Optional Jellyfin is entered only from Welcome; system/visible Back return correctly; setup/HTTP-consent cancellation, failure, retry and confirmed success preserve the contract.
- [ ] Both explicit skips survive relaunch, and Add comics, SMB and source settings stay reachable afterward.
- [ ] Confirmed partial import, pending operation recreation, success-before-completion-write, stale callbacks, failed persistence and rapid repeated taps do not lose data or falsely complete.
- [ ] Showing either card performs no new network/discovery/authentication; credentials, server details and file paths remain outside portable backup.
- [ ] Official Jellyfin asset provenance and full license/attribution are bundled; no placeholder shipped; Leaf rights gate remains separately documented.
- [ ] English and es-MX strings, 48 dp targets, large text, short/narrow windows, light/dark/AMOLED, landscape and portrait tablet layouts are checked. Spoken TalkBack and physical wide-device acceptance are separate from semantic/synthetic-width tests.
- [ ] Exact current source, build inputs, artifact/certificate, OS/device, normal and minified scope and native screenshots/motion are recorded; no inference from historical or different-platform tests.

## Current refresh evidence and next task

Current base includes merged #32; normal S25 development build `599e9a6` and prior release-like `fead12b` evidence remain their own historical scopes. No app code, build, device or account was changed for this refresh. No user acceptance checkbox was closed. The prior source plan, current files and fifteen committed design reference files were inspected; the private receipt includes full hashes, dimensions and access failures.

The next implementation slice is the **startup/persisted-state foundation**, with existing-install protection as its primary acceptance criterion. Visual implementation still needs usable high-fidelity Figma context under the selected workflow; approved durable references are now catalogued rather than missing. Do not buy a plan, change account/security settings, or bypass limits to obtain it.

Track on the existing **ONB-ANDROID: local-first welcome and optional Jellyfin onboarding** card in [Leaf Delivery](https://github.com/users/bclark/projects/1). Planning Ready is not implementation In Progress or Done. Update the card and Android engineering notes at the next authorized implementation start. JF-A07's broader server/discovery/accessibility/device requirements and every release gate remain separate.
