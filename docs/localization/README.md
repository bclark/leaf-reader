# iOS Mexican Spanish localization

Status: **DRAFT — awaiting fluent Mexican Spanish review. No reviewer has been identified.** Machine-translated catalog entries and review-sheet rows are not release approval. English and Spanish App Store metadata also require owner approval. No publishing or App Store Connect changes are included.

## Scope and decisions

- English is the development language. `es-MX` is the only added localization.
- Use iOS’s per-app language setting; no in-app selector, persisted language preference, bundle override, or custom fallback.
- Starting glossary: cómic, lista de lectura, respaldo, propina. Leaf, product identifiers, filenames, metadata supplied by readers, credentials, URLs, backup schema keys, enum raw values, and third-party notices are not translated.
- Tips support development, unlock nothing, and remain optional. Product display names and prices come from Apple. App language, device region, and purchase storefront are separate settings.
- Privacy, support, and license links currently lead to English pages and are labeled accordingly. Spanish drafts must be reviewed and published before replacing destinations.
- Proposed iOS English listing is the source for Spanish metadata. Android metadata is not reused. Spanish support must not be advertised until the localization has been tested and reviewed.

## Files and review workflow

[ES_MX_REVIEW.csv](ES_MX_REVIEW.csv) contains 305 catalog review rows, covering every nonempty catalog entry. The extracted empty SMB field placeholder has no copy to review. [ES_MX_PROSE_REVIEW.json](ES_MX_PROSE_REVIEW.json) pairs the English and Spanish prose drafts.

`iosApp/Sources/Localizable.xcstrings` contains app strings; `InfoPlist.xcstrings` contains system-facing text, including the Local Network permission explanation. `project.yml` preserves English development language, es-MX region, and Swift string extraction through regeneration.

Review each row in the review sheet against its source screen, placeholders, and plural cases. Record reviewer name, revised copy, decision, and review date. Check Anton/Archivo accents and Mexican usage on screen, particularly compact buttons and multiline error messages. The catalog’s machine-translated state deliberately distinguishes drafts from approved human translations. No fluent review has happened yet.

The [English listing](IOS_LISTING_EN_DRAFT.md), [Spanish listing](IOS_LISTING_ES_MX_DRAFT.md), [Spanish privacy](IOS_PRIVACY_ES_MX_DRAFT.md), and [Spanish support](IOS_SUPPORT_ES_MX_DRAFT.md) documents are drafts only. See [QA_RESULTS.md](QA_RESULTS.md) for checks completed and remaining gates. The live English privacy policy remains `PRIVACY.md` at the repository root.

## Required QA matrix

Record the exact build, source commit plus dirty state, iOS version, device, app language, region, storefront, and test environment for each run. Never infer a purchase storefront from the UI language or device region.

| App language preference | Device region | Storefront | Purpose |
| --- | --- | --- | --- |
| en | US | US | English regression |
| es-MX | MX | MX | Primary Mexican Spanish flow |
| es-MX | US | US | Spanish UI with US currency |
| en | MX | MX | English UI with Mexican currency |
| es | MX | Independent | Observe Apple’s generic Spanish matching |
| es-ES | ES | Independent | Observe Apple’s Spain matching |
| Unsupported language, then en | Any | Independent | Observe English fallback |

Use actual Apple language matching; record observations rather than promises for generic Spanish or Spain. Repeat critical UI checks on compact iPhone and iPad, portrait and landscape, large Dynamic Type and VoiceOver. Check counts 0, 1, 2, 11, and large values. Import a synthetic comic whose filename contains accents and emoji; preserve its title, series, and reading-list text through backup and restore. Inspect reader status, series/list navigation, import errors, backup conflicts, and all tip states.

SMB: test fresh Local Network permission, denial, the Open App Settings path, invalid credentials, unavailable server, cancellation, and partial batch results. Requires a controlled server; do not assume a generic transport error proves permission handling.

StoreKit: local fixture tests are simulated and do not prove live MXN amounts or localized App Store product metadata. Signed sandbox/TestFlight tests with confirmed products and account agreements remain release gates. Do not make real-money purchases for QA.

Screenshots should use `<screen>_<locale>_<device>_<state>.png`, accompanied by a manifest containing the run settings above. Screenshots without verified language/region/storefront context are not release evidence.

## Release gates

Fluent Mexican Spanish review; owner approval of both listings; publication of reviewed Spanish web destinations; exact existing product IDs and Apple-localized product copy; live sandbox/TestFlight storefront checks; full device/accessibility matrix; current SMB export-compliance review; final signed archive inspection. Keep the model and StoreKit fixtures excluded from the app bundle. Do not copy the old encryption declaration.
