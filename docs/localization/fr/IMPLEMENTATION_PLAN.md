# French implementation and acceptance plan

This is a proposed continuation of the prepared packet. It does not declare French implemented or authorize a release. Preserve Leaf’s current hierarchy, typography, whole-page reader, source privacy and intentional Series peel gesture.

## 1. Resolve and review the copy

Use `resource-review.csv` for the 175 current resource entries. Review `hardcoded-review.csv` by production call path before extraction. Unused legacy screens, logging strings and protocol/storage identifiers are not automatic translation work. Keep reviewer decisions and explicitly reconcile source changes.

Prioritize currently reachable onboarding, Library/search/Continue, reader controls and errors, Jellyfin setup/details/conflicts, SMB browsing/import/cancellation, list/series actions, settings and backup/restore. Both distribution flavors need review; optional tipping stays governed by the existing flavor capability.

The initial audit found these concrete areas:

| Source | Verified issue to address |
| --- | --- |
| `ui/menu/MenuScreen.kt` | Literal restore actions, project links, dynamic preview/status strings and exception-message fallbacks. |
| `ui/reader/ReaderScreen.kt` | Literal loading/retry/back labels and accessibility descriptions. |
| `ui/library/SeriesDetailScreen.kt` | Literal selection/removal controls and interpolated confirmation copy. |
| `ui/library/ReadingListDetailScreen.kt` | Literal ordering/list actions, missing-member guidance and count sentences. |
| `ui/library/RemoteListActions.kt` | Literal list-creation and membership/download explanations. |
| `ui/library/LibraryViewModel.kt` | User-facing error fallbacks and fixed-US transfer/size formatting. |
| `ui/library/LibraryScreen.kt`, `JellyfinBrowserScreen.kt` | Fixed-US size display; preserve byte values and behavior. |
| `ui/menu/MenuScreen.kt` | Locale-dependent date presentation mixed with an intentionally stable backup filename date. |

The complete source/line inventory is in the CSV files. These findings are source evidence, not new device observations.

## 2. Extract and wire without changing behavior

- Reuse existing resource IDs or native system actions when they have the same meaning. Extract remaining English UI copy to default resources, then add reviewed French values.
- Use `stringResource` in composables and `Context.getString` at Android presentation boundaries. Do not call composable APIs in callbacks or introduce Android resources into shared Kotlin.
- Use positional arguments and plural resources for count-dependent sentences. Keep source/server values as arguments; do not assemble translated sentences from English fragments.
- Map user-facing error categories at the presentation boundary. Raw server/exception text may remain source text; do not rewrite protocol matching, exception types or storage contracts to translate an error.
- Keep all existing Spanish resources and reviewed choices. Current English/Spanish key parity does not prove full Spanish UI coverage. Newly extracted keys need an explicit Spanish fallback/review decision; do not label new Spanish text reviewed automatically.
- Preserve original capitalization/typography intent and supported-format claims. Fix stale English copy separately and carry agreed meaning across translations.

Acceptance: default and French keys/quantities/placeholders match; protected names/storage fields are unchanged; no new network/telemetry/dependency or reading-state behavior; tests for affected formatter/error/resource boundaries, not a duplicate test for every label.

## 3. Native language selection

Once copy coverage and review are complete, declare a curated supported-language list (`en`, `es-MX`, `fr`) for Android’s system per-app language settings on Android 13+. Avoid advertising dependency-provided languages accidentally. Older Android versions can continue following the system locale; a new in-app language picker is not required for this first implementation.

There is currently no `localeConfig`, generated locale configuration or custom language picker in the inspected app. Decide whether to use an explicit curated configuration or carefully restricted AGP generation during wiring. Do not add a custom locale persistence/fallback framework.

References:
- [Android localization](https://developer.android.com/guide/topics/resources/localization)
- [Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages)
- [Pseudolocale testing](https://developer.android.com/guide/topics/resources/pseudolocales)

## 4. Formatting and UI acceptance

Review all 19 formatting candidates. Localize display dates, decimals, percentages, speeds and sizes with appropriate platform/locale APIs. Preserve stable backup filename timestamps, JSON values, identifiers, byte counts and connection details. Some candidates are storage contracts rather than display bugs.

Under coordinated native slots, verify both flavors and the following:

- English regression and French locale/resource fallback, including French-France and French-Canada device locale matching for the general `fr` resources.
- Android 12 behavior and Android 13+ per-app language changes; preserve the user’s existing language/device settings, using owned QA environments or owner-local handling.
- Compact screens, larger text, accents in Anton/Archivo, wrapping/truncation, dialogs and icon labels. Use pseudolocales to catch remaining hardcoded copy and expansion issues; pseudolocale success is not fluent-language approval.
- Zero/one/two/many counts, long titles/creator names, `%` and nested messages, read/unread and conflict-choice states, deletion/retention guarantees, authentication/network errors, import cancellation and backup/restore outcomes.
- Local reading without a configured server, cold-start retention and the Series peel gesture.
- French accessibility semantics and spoken output when the owner schedules it. Existing TalkBack deferral stays deferred; do not change accessibility settings or count semantics checks as spoken acceptance.

Record exact source/artifact/locale/device and evidence. Fresh compile/lint/unit checks and relevant minified runtime checks follow the actual code changes. Do not reuse this preparation packet’s four Python tests as Android acceptance.

## Completion boundary

Preparation is complete when the draft packet is reproducible and its coverage/limitations are explicit. French app support requires reviewed copy, UI wiring, compiled resources and native acceptance. Store/support/privacy translations and publication are separate tasks; existing release gates and owner deferrals remain in force.
