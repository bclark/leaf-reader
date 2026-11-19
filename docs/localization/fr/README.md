# French localization preparation

**Draft: French is not enabled in the app.** No fluent review, Android resource compilation or French device acceptance is claimed.

This packet implements the first preparation step approved by Bryan: inventory the current text and prepare a translation/review sheet before UI wiring. It uses general French (`fr`), with a consistent formal “vous” voice. Regional variants such as `fr-CA` can be considered after a reviewer identifies a concrete need.

The source baseline is `9ff11f5cba8d589e823ab80e8816898a85e6dc33`. Its application code matches the published Android 0.3.9 RC1 outside the release version metadata. The released artifacts are unchanged by this work.

## What is ready

- Draft French translations for all **175 current default resource entries**, represented by **179 review rows** because four resources have separate plural variants.
- Six draft XML files in [draft-values-fr](draft-values-fr), kept outside `app/src` so they cannot ship accidentally.
- A [resource review sheet](resource-review.csv) with English, proposed French, quantity, placeholders, source references and reviewer fields.
- A [hardcoded-copy inventory](hardcoded-review.csv) covering 1,697 string literals across 100 production Android/shared Kotlin files. The scan includes Android main, Full and F-Droid source sets; it excludes tests, private QA overlays and local configuration.
- A [formatting review sheet](formatting-review.csv) with 19 date, number, size or enum-display sites.
- [Machine-readable counts and source hashes](inventory.json), a reproducible generator and four focused offline tooling tests.

The lexical inventory identifies **221 direct UI-copy candidates** and **338 additional UI-copy candidates**. These are not a count of distinct missing translations or proof that every site is reachable. Other rows include protocol keys, paths, formats, logging/error strings and usages that need manual classification. Conditional text inside a string template remains visible in its parent row; the scanner does not type-check Kotlin or establish a production call graph.

The older line-based scan found 149 direct `Text`/`contentDescription` sites. The lexical scan includes multiline and named-argument cases, so its larger count supersedes that preliminary lower bound.

## Review process

1. Read the [glossary](GLOSSARY.md), then review `resource-review.csv` in a spreadsheet or text editor.
2. Put a proposed correction in `reviewed_french`. Leave `french_draft` and English/source identity columns intact. Empty `reviewed_french` means the original draft remains the proposal.
3. Set `decision` to `approved`, `revise`, `question` or `unreviewed`. An approval needs the fluent reviewer’s name and review date. All actual rows currently remain unreviewed.
4. Check placeholders, plural meaning, narrow no-break spaces, accents and the context of actions versus states. Security, deletion, retention and conflict-choice messages deserve explicit review.
5. Review the candidate/source sheets separately. Assign real resource keys only after checking whether a literal is user-visible, reachable and covered by an existing resource. Preserve technical and user/server-owned data.
6. Implement approved text through Android resources, then review it on real screens. A row approval alone does not prove fit, accessibility or correct UI wiring.

The inventory uses spreadsheet-safe leading apostrophes for source cells beginning with formula operators. Hardcoded-source excerpts show physical line breaks as `\n`/`\r` to keep spreadsheet rows readable; the original literal remains in the pinned source. Reviewer spreadsheets and annotated sheets should remain private if reviewers add personal information; only the unreviewed, empty-reviewer template is proposed for the repository.

## Reproduce and validate

From the repository root:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 docs/localization/fr/prepare.py --check
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s docs/localization/fr -p test_prepare.py
```

`prepare.py` regenerates inventories and draft XML when run without `--check`. It **never overwrites existing review CSV files**. The check command allows reviewer annotations and validates their identities/source columns; it reports source changes for explicit reconciliation rather than dropping reviews. It also checks edited French placeholders and requires a reviewer/date for approval. Updating `translations.json` does not automatically approve or apply a reviewer’s correction; reconcile the draft/source columns deliberately while retaining review history.

No Gradle, emulator, physical-device, network translation service or signing operation is needed for these packet checks. They verify structural consistency, not fluent French or Android rendering.

## Remaining implementation

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the extraction/wiring and QA workstreams. They include conditional copy, accessibility, errors, formatters, native language declaration and both Android flavors. Do not enable or advertise French merely by copying these six files: substantial UI copy still lives outside resource XML.

Some existing English resources mention only CBZ/CBR, despite PDF support elsewhere. Their French drafts stay faithful to the current source. Review the English source copy before approving a correction across languages; do not silently change supported-format claims in translation.

Store listings, hosted support/privacy pages, credentials, account/server choices, backups, comic metadata, app releases and iOS localization are outside this preparation packet.
