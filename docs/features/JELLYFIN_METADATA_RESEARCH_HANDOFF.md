# Handoff: source comic metadata for Series and details

Date: 2026-11-11
Project: Leaf
Task type: investigation and evidence report; no product implementation or server edits.

## Objective

Find where Leaf can reliably obtain the metadata needed to organize Jellyfin comics into series and make the comic details page useful. Identify whether information is absent from the source, omitted from a Jellyfin response, lost in Leaf's mapping, or already parsed but not displayed.

Return an implementation-ready field mapping and a short recommended next step. Do not assume that every comic needs to be downloaded or manually retagged.

## Read first

- [JF-01 product contract](JELLYFIN_FIRST_LIBRARY.md), especially Approved Series and Lists behavior.
- [Android engineering notes](../ANDROID_ENG_NOTES.md); preserve historical evidence and checklist states.
- [Existing bounded QA](../local/ANDROID_JF_SERIES_LISTS_QA_20261111.json).
- Root AGENTS.md and applicable instructions.

The goal is Jellyfin comics in the existing Leaf library UI. Keep native Series stacks/peel, details, lists and offline access. Ambiguous series belong in Unsorted. This research does not settle progress conflicts, retention/eviction or release acceptance.

## What is known, and what is not

The prior Android phone session indexed 724 catalogue records, all classified as Unsorted by the current mapper/grouping rules. That is evidence about the data Leaf currently consumes, not proof that Jellyfin's full item records, sidecars or embedded archive metadata lack useful information. Recheck the current server/version and representative raw responses before diagnosing the cause.

Current shared grouping requires a nonblank series name and either SeriesId or explicit volume metadata. Scope includes server/account/library. Audit this rule as well as the source data: a missing TV-style SeriesId need not imply a comic has no identifiable series. Matching names alone must not silently merge distinct runs.

The details page currently supports title, series, issue number, publication year, server reading state, synopsis, creators and genres when supplied. Its sparse appearance may reflect missing data, request fields, parsing or display omissions. Establish which.

## Representative sample

Use a small sample, ideally 4–6 items:

1. King Spawn - Vol. 01, the owner's previously designated title.
2. Another King Spawn volume, if available, to establish series membership and collected-volume order.
3. Two single issues from one series.
4. Two runs/editions sharing a series name, if available.
5. An item whose Jellyfin web details look richer than Leaf's.
6. A genuinely sparse item as a control.

One item can satisfy several cases. Record unavailable sample cases instead of inventing them. Previously observed titles include Dead Eyes - Vol. 01 and Rat Race; these are sample candidates, not verified metadata matches.

## Investigation sequence

1. Trace the current catalogue query, full-detail request, response parser, shared models and UI. Record the exact request fields and where each useful field survives or disappears.
2. Using the owner's existing authorized Jellyfin connection where available, compare catalogue and full-item metadata for the sample. Check parent/ancestor records only where needed to explain series identity. Record the server version and relevant metadata provider/plugin versions.
3. Inspect embedded ComicInfo.xml or existing sidecars for already-local sample archives through the established archive APIs. Reading this metadata must not require decoding all page images. If the agent instead has authorized read access on the server host, inspect only the selected files/metadata there. Do not bulk-download the library. If archive metadata cannot be accessed without a new transfer, report that limitation.
4. Verify actual Books/comic behavior against documentation or source for the installed Jellyfin version. A field in a generic SDK/TV DTO is not proof that Books populate it.
5. Only where existing sources are insufficient, research public publisher/catalogue/metadata-provider sources. Use minimal public title/edition searches. Identify provider IDs, edition matching, available fields, access requirements and whether a proposed source allows the intended reuse. Research is not authorization to add a cloud dependency or automatic third-party enrichment to Leaf.
6. Explain where enrichment would belong: existing Jellyfin provider/configuration, a Leaf mapping fix, local archive metadata, or a separately proposed owner-confirmed override. Keep proposed changes separate from verified findings.

## Series information to establish

| Needed fact | Questions to answer |
|---|---|
| Series display name | Is it the actual comic series, a publisher/showcase label, or a folder label? |
| Run/edition identity | Is there a provider series ID, explicit run/volume, or independently supported run-start year? |
| Comic type | Is this a single issue, collected volume/trade, omnibus, annual, special or manga volume? |
| Order | Which field represents issue number versus collected-volume number? Are fractional/alphanumeric numbers preserved? |
| Membership | Which stable item IDs belong to each run, within the selected account/library? |
| Refresh behavior | What happens when metadata improves or the display name changes? Can identity stay stable? |

Do not treat an individual issue's publication year as the series launch year. Do not assume ComicInfo Volume, a title containing "Vol.", and Jellyfin ParentIndexNumber all mean the same thing. Preserve raw values before proposing normalization. File/title/folder patterns can be evidence for investigation, but inferred matches must be labelled and cannot silently replace the approved Unsorted behavior.

Recommend a source precedence and fallback rule, with concrete examples of both a safe grouping and a case that must remain Unsorted. Include same-name runs, collected volumes versus issues, missing numbering and conflicting sources.

## Details-page information to source

Prioritize:

- Identity: clean title, series/run, issue or collected-volume designation, edition.
- Description: synopsis/summary.
- Credits: writer, penciller/artist, inker, colorist and publisher/imprint, preserving supplied roles.
- Publication: date/year, language and genres where useful and supported.
- Reading context: other available comics in the same verified series, preceding/next item in reliable order.
- Availability: retained copy versus remote-only; format, size and page count when known.

Reading progress, download availability and Leaf list membership come from existing app/server state rather than public comic databases. Identify how the page should combine these states without presenting a remote-only comic as offline-readable. Distinguish an actual page count from an estimate or one unavailable until download.

Omit unsupported fields in the proposed UI. Do not fill unknown values with guesses or infer series completeness from the number of files in this library.

## Relevant code

Paths are relative to the repository root:

- shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinCatalogue.kt
- shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinSeries.kt
- shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinModels.kt
- shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinItemDetails.kt
- shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/archive/ComicMetadataParser.kt
- app/src/main/java/net/digitalbc/leafreader/data/jellyfin/JellyfinClient.kt
- app/src/main/java/net/digitalbc/leafreader/data/jellyfin/JellyfinSnapshots.kt
- app/src/main/java/net/digitalbc/leafreader/data/library/LibraryRepository.kt
- app/src/main/java/net/digitalbc/leafreader/ui/library/JellyfinDetailScreen.kt
- app/src/main/java/net/digitalbc/leafreader/ui/library/JellyfinCatalogueViewModel.kt
- app/src/main/java/net/digitalbc/leafreader/ui/library/MainCatalogueGrid.kt

Starting references; verify against the installed version:

- [Jellyfin BaseItemDto reference](https://typescript-sdk.jellyfin.org/interfaces/generated-client.BaseItemDto.html)
- [Jellyfin 10.10.7 DTO source](https://github.com/jellyfin/jellyfin/blob/v10.10.7/MediaBrowser.Model/Dto/BaseItemDto.cs)
- [ComicInfo documentation](https://anansi-project.github.io/docs/comicinfo/documentation)

## Deliverables

Write a sibling report named JELLYFIN_METADATA_RESEARCH_REPORT.md containing:

1. A short diagnosis, separating verified facts, hypotheses and inaccessible sources.
2. A sample comparison table: selected comic, expected series/run/type/order, raw catalogue values, full-detail values, archive/sidecar values, and evidence reference.
3. A field mapping table: desired Leaf field, source and exact field/path, observed example, availability, normalization, confidence and fallback.
4. A provenance-backed proposed record for King Spawn - Vol. 01. Leave missing values explicitly unknown; distinguish issue range contained in a collected volume from its volume number.
5. Proposed series identity/order rules with same-name-run and ambiguous examples.
6. A prioritized details-page recommendation: information available now versus requiring mapper changes or source enrichment.
7. The smallest implementation sequence and sanitized fixture cases for an implementation agent. State whether each step is a Leaf fix, server-side preparation or an owner decision.
8. Any missing access or owner input, asked once and specifically. Do not request credentials already available through an authorized connection.

Provide links/version references and concise sanitized JSON/XML excerpts where useful. Use aliases for private server/account/item IDs in shareable evidence; preserve equality consistently. Do not include tokens, passwords, private server URLs or filesystem paths.

## Boundaries and completion

Use read-only source/server investigation. Do not edit comic metadata, trigger a library-wide metadata refresh, install server plugins, change server configuration, write progress, clear app data, download new archives, or modify application code under this handoff. Propose such steps in the report when necessary. Existing downloaded comics and user lists must remain intact.

Research is complete when the report establishes a defensible source/mapping for the required fields, or pinpoints why a specific field is unavailable and what would obtain it. A useful result may be "fix Leaf's mapping"; do not default to requesting a server upgrade or retagging the whole collection. If live access is unavailable, deliver the code/documentation findings and clearly mark the sample verification pending.

Record the report link and bounded result in Android engineering notes. If shared product behavior or iOS-facing documentation changes, follow the repository's iOS coordination recording instructions. This research closes no JF or release acceptance gate by itself.
