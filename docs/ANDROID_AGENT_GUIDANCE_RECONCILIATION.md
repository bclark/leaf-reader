# Android guidance and asset reconciliation — review proposal

Prepared for DS04/DS09 on 13 November 2026 against isolated `3603b89` plus the Android candidate changes. This is a targeted factual replacement proposal for the outdated Android portions of root `AGENTS.md`. The root instructions and existing artwork were not rewritten. Bryan’s current whole-page/privacy/peel instructions remain authoritative. Owner review of the proposed role wording and undocumented artwork approvals remains open.

## Proposed Android role wording

Leaf’s current Android package is `net.digitalbc.leafreader`. It reads whole comic pages with page navigation, pinch zoom/pan and optional spreads. `ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING` is true; the active application uses `NoopPanelSource`, and release packaging excludes the bundled model/runtime. Historical panel detection classes are retained source, not a request to restore guided reading. Preserve whole-page behavior and the intentional downward Series cover peel, its selected cover, navigation return and scroll restoration.

Use current source and the latest live `ANDROID_ENG_NOTES.md`, `LEAF_PRODUCT_FLOWS_HANDOFF.md`, `ANDROID_PERFORMANCE_UI_HANDOFF.md`, `ANDROID_RELEASE.md` and `ANDROID_BEST_PRACTICES_AUDIT.md`. Historical checklist passes and engineer recommendations do not independently authorize release or new product behavior. Reconcile an exact baseline before implementation; do not copy a moving dirty checkout or interrupt another native build/device session.

The library supports device imports, explicit SMB imports and optional user-configured Jellyfin catalogue/details/copies/progress. Whole-page reading of a retained valid file remains available without Jellyfin. Read/Continue retrieves a complete archive when needed; call this download/cache-on-read, not streaming. Preserve scoped server/user/item identity, unknown remote state, portable backup compatibility, durable pending progress and explicit conflict choices. Read/Continue copies remain until manual removal. Removing a download is separate from deleting library history or a server file.

Keep common Kotlin code platform-independent and reuse existing shared archive, navigation, catalogue identity and progress-policy contracts. Keep archive/page decoding and credential/cover work off Main. Do not revive removed behavior from the historical architecture narrative. Native builds/device tests are coordinated and serialized. Full and F-Droid differ in supported archive/tip behavior; verify both actual variants rather than using the old unflavoured debug examples as release evidence.

The current Android palette is forest green `#1C3A27`, deep forest `#122519`, sage `#4A7C59`, gold `#D4AF37`, deep ink `#14191C`/surface `#1E2529` and paper white `#F9F9FB`. `Crimson`, `Maroon`, `Cream` and `Ochre` are compatibility aliases in `Color.kt`; their names do not authorize reverting to the old crimson palette. Anton remains the display face and Archivo the UI/body face. Use semantic light/dark colors, native controls and accessible touch targets. Preserve approved light/dark launcher exports and the canonical leaf/page mark. A redesigned mark or typography direction needs Bryan’s decision.

Keep existing privacy boundaries: no telemetry, analytics, ads or unrelated network services. Network requests belong only to the configured optional source/user action or approved existing platform feature. Credential files stay out of portable exports and backup/transfer; metadata and unencrypted exports need their actual documented disclosures. App changes, local builds or board status never authorize store upload/publication, account/signing changes or enabling CI.

## Existing asset evidence

| Asset | Purpose and verified source history | Current SHA-256 |
|---|---|---|
| `docs/leaf-icon.png` | Canonical 1024×1024 leaf/page mark identified by the Android audit. Added under `ec7e37c`; transparent update `36f6568`. | `0f771cd5767e850a8b7ab22d30e168c7203bfdfe2fcf838ced6cd4f29d01bb7d` |
| `app/src/main/res/drawable/ic_leaf_logo.png` | Current in-app mark, byte-identical to the canonical file. Latest source `36f6568`. | `0f771cd5767e850a8b7ab22d30e168c7203bfdfe2fcf838ced6cd4f29d01bb7d` |
| `docs/logo-lockup.png` | Visually inspected: CHIKA / CHITRA KATHA upstream lockup, not Leaf branding. History includes upstream design commit `e73ec3a`; latest path history `1e8b84c`. Preserve upstream attribution. | `95561f749bcbf0892c519cbcc703feb98ebe9c5519c43a86bf2c5ffefc631fb3` |
| `fastlane/metadata/android/en-US/images/icon.png` | Existing 512×512 listing export, latest `378ab95`. Its presence is not proof of current Play listing acceptance. | `37e27cb6115f2b0e0540b8a4a92e53ac064053eb8f0898eae66ece81b26794a5` |
| Android launcher foreground/monochrome/legacy/light/dark files | Existing density-specific exports and adaptive XML, preserved unchanged. History includes transparent update `36f6568`, adaptive configuration `0277692`, appearance controls `378ab95`. | Per-file inventory below. |
| Anton / Archivo | Existing font files, originally included in upstream design history. Full OFL notices retained in `THIRD_PARTY_LICENSES/OFL-1.1-Anton.txt` and `OFL-1.1-Archivo.txt` and Android About assets. | Per-file inventory below. |

[Complete local inventory](local/android-eng-workthrough-20261113/asset-provenance.json) records 41 exact paths, dimensions where PNG, byte sizes, SHA-256 and latest path commit. Inventory SHA-256: `0c1d0bfe4b2ed2d6fef4d269b7e0a34eea1e5d4ed43cc2f6cd18ff3622c90e1e`. No image/font file was generated or modified for this proposal.

Repository history proves inclusion and byte identity. It does not establish the original artwork creator, rights grant, owner approval date or reproducible export recipe. Those records were not found in the examined source evidence. Bryan/design owner must reconcile them before DS04/DS09 is closed. Do not invent provenance or replace existing approved assets to bypass that gate.
