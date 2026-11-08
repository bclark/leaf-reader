# Localization QA evidence

Latest candidate verification: [7 November authoritative readiness record](../../RELEASE_COMPLIANCE.md#6-ios-local-candidate--7-november-2026). The earlier results below remain historical; current artifacts, fresh test counts and external gates are maintained there.

Status: development checks complete; fluent review and the remaining release matrix are pending. These development checks do not authorize advertising Spanish support.

## Native runtime checks — 6 November 2026

Leaf scheme, Bryan’s iPhone, version 1.0 build 2026110601, source based on 0f03e38 with uncommitted StoreKit/localization changes. Two checks passed: standard bundle language matching, and accented/emoji title/series/list content through backup serialization and parsing with unchanged schema identifiers.

| Preferred language, then English | Actual Foundation bundle match |
| --- | --- |
| en | en |
| es-MX | es-MX |
| es | es-MX |
| es-ES | es-MX |
| fr | en |

This records `Bundle.preferredLocalizations(from:forPreferences:)` against the built app’s available localizations. It is an observation on this runtime, not a custom fallback implementation or a claim that Spain Spanish has been reviewed. No separate generic Spanish or Spain catalog exists.

Evidence: Xcode ActionArtifacts `757E1016-63C5-4277-9BDD-05B0A5D3F459/RunSomeTests/1B02D0BA-70C6-4AAA-A9C0-3CA65780571D.txt` and `test-console-log-2026-11-06T08-06-06-05-00.txt`.

Builds after string extraction/source refactoring passed. Catalog, UI, accessibility, and archive evidence is recorded below; unmet release checks remain explicit. No real-money purchase, publication, or App Store Connect change has been performed.

## Catalog and native-suite verification

All nonempty source strings are translated: 300 app entries and 5 InfoPlist entries. Final extraction also records one empty-string key for the SMB field whose visible label is supplied separately; it has no copy to translate and is excluded from the review count. All 305 are machine-translated drafts, with zero human-approved entries. The CSV review sheet covers every nonempty catalog key; no reviewer is assigned. A flat-placeholder audit found no mismatches in the completed plain-text translation pairs; varied counts are compiled by Xcode and exercised by native tests.

All 85 native tests passed across the full and focused runs. The initial full run passed 83/85, including all five localization checks (compiled resources/permission text, count forms 0/1/2/11/1000, font glyph coverage, language matching, and Unicode backup round-trip). An older hero-title test was updated to expect preserved user casing. The StoreKit integration harness now drives the same direct purchase path as the picker instead of assuming that an externally injected transaction racing quiet startup recovery always emits a thank-you. It checks finished transactions, repeated tips, pending approval, refunds, and the three Spanish fixture product names. No app transaction behavior was changed for this test adjustment.

Final focused results: reading-list regression passed in `RunSomeTests/9736CCC7-E436-474C-B804-C618331180AB.txt`; both local StoreKit integration checks passed in `RunSomeTests/6F14769E-A051-4629-B5A7-115435EE52ED.txt`. Initial full-suite evidence: `RunAllTests/C8829E26-E1CE-4089-AEDF-DAF47143F977.txt`. All paths are within the ActionArtifacts directory identified above.

The local StoreKit fixture includes draft es_MX product names/descriptions. Its default remains en_US/USA; the integration check explicitly selects es_MX/MEX. Fixture prices and this check do not validate live Mexico pricing or App Store Connect metadata.

## Simulator UI and accessibility checks

Eleven screenshots and their [manifest](screenshots/manifest.json) record the final source on iPhone Duo, iOS 27.1, build 1.0 (2026110601), based on 0f03e38 plus uncommitted changes. Spanish library, series, appearance, backup/about, support, SMB, and tips surfaces were inspected. Generic `es` and Spain `es-ES` launch preferences both rendered the Spanish library; the region remained `es_MX`, so this is not the separate Spain-region matrix cell. Language preferences were supplied at launch; the Settings app’s per-app language control was not exercised.

The SMB field now has a persistent translated label that fits at normal size and wraps at the largest accessibility text size. Tip text wraps and scrolls without overlap. Accessibility hierarchy rechecks confirmed MENÚ, one mark-all label, and the specific remove-from-series label. These hierarchy checks are not a full spoken VoiceOver audit.

The UI run used local StoreKit configuration `en_US` / USA independently of Spanish app language and Mexican region, so English product names and USD were expected. The native integration test separately verified Spanish fixture names with `es_MX` / MEX; neither validates live Mexican prices.

Still untested: iPad, successful landscape layout, the full large-text and VoiceOver matrix, Settings language switching, fresh Local Network permission/denial and controlled live SMB failures, real storefronts, and the complete reader/list/import/restore UI matrix. The simulator rotation attempt did not establish landscape coverage. Synthetic fixtures were removed and the original simulator appearance/text settings and Xcode scheme/destination restored. No fluent language review has been performed.

## Final release archive inspection

After regenerating from `project.yml` and installing pods, Release archive creation succeeded at `/private/tmp/Leaf-es-MX-2026110601.xcarchive`. The app signature passed `codesign --verify --deep --strict`. Inspection of the archived app itself confirmed bundle ID `net.digitalbc.leafreader`, version 1.0, build 2026110601, minimum iOS 16.0, and English development language.

The archive contains English plural resources and Mexican Spanish strings, plural resources, and InfoPlist strings, including the Spanish Local Network explanation. No `.tflite` or `.storekit` files occur in the app bundle. `ITSAppUsesNonExemptEncryption` remains absent pending the current SMB export-compliance review. Build log: `/private/tmp/leaf-es-MX-archive.log`; inspection result: `/private/tmp/leaf-es-MX-archive-verification.json`.

This is a local signed archive, not an upload or TestFlight/storefront validation. Fluent review, metadata approval, publication of reviewed Spanish destinations, the remaining device/accessibility matrix, and live sandbox/storefront verification remain release gates. All changes remain uncommitted.
