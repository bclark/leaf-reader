# Library Organization & Stacking: Progress, Plan & Handoff

## 1. Context & User Requirements
User feedback from friend testing Leaf:
> *"Library needs some organization features like stacking and reading lists. But runs really smooth for reading... I like to stack by series or volume. Like all Deadpool books in a stack, or stacks of Punisher Vol 2, Vol 3, etc."*

### Architectural Decisions Approved by User:
1. **Auto-Grouping + Manual Override (Option C):**
   - Automatically detect series/volume/issue metadata from internal `ComicInfo.xml` (CBZ) or via filename heuristic regex.
   - If 2+ comics share a series/volume, they automatically collapse into a **Series Stack** in the `ALL` library view, and all series appear under `SERIES`.
   - Users can manually create/edit stacks and move issues into/out of stacks.
2. **Dedicated Series Screen:**
   - Tapping a stack opens a dedicated Series view with series title, issue list/grid sorted naturally (`#1`, `#2`, `#3`...), stats (`X issues · Y unread`), and a primary **"READ NEXT: ISSUE #X"** button.
3. **Adaptive Filter Placement:**
   - **Expanded screens (Tablets, Foldables unfolded, Landscape >= 600dp):** Horizontal filter chips directly below header: `[ ALL ]  [ SERIES ]  [ READING LISTS ]  [ UNREAD ]`.
   - **Compact screens (Folded phone portrait < 600dp):** Filter placed in top-left Menu drawer with an active status chip next to search.

---

## 2. Current State & Completed Work

**Branch:** `feat/library-organization-stacking` in `/Users/bryanclark/Repos/chika`

### Phase 1: Metadata Engine & Database (Completed & Verified)
1. **Pure Kotlin Multiplatform Metadata Parser (`:shared`):**
   - File: `shared/src/commonMain/kotlin/com/chakra/comicreader/data/archive/ComicMetadataParser.kt`
   - Handles `ComicInfo.xml` tag extraction and filename regex heuristics (`Series (Year) #Issue`, `Volume X`, etc.).
   - Unit tests: `shared/src/commonTest/kotlin/com/chakra/comicreader/data/archive/ComicMetadataParserTest.kt` (verified with `:shared:jvmTest`, 75 tests passing).
2. **Archive XML Hook:**
   - `ComicArchive.kt`: `readComicInfoXml(): String?` interface hook.
   - `ZipComicArchive.kt`: Implemented entry lookup for `ComicInfo.xml` with UTF-8 decoding.
3. **Database Entities, DAO & Migration (`:app`):**
   - `ComicEntity.kt`: Added `series: String? = null`, `volume: String? = null`, `issueNumber: Float? = null`.
   - `ReadingListEntity.kt`: `ReadingListEntity` & `ReadingListItemEntity` (junction table with sortOrder).
   - `ReadingListDao.kt`: Full CRUD operations for lists, items, and ordering.
   - `ComicDao.kt`: Added `observeBySeries`, `getComicsWithoutSeries`, and `updateMetadata`.
   - `AppDatabase.kt`: Bumped to version 3 with non-destructive `MIGRATION_2_3`.
4. **Repository Implementation:**
   - `LibraryRepository.kt`: Wired metadata extraction in `indexCopiedFile`, added `backfillMetadataIfNeeded()` for existing library items, and added reading list CRUD methods.
   - `ComicReaderApp.kt`: Passed `database.readingListDao()` to `LibraryRepository`.

### Phase 2: Stacking Engine & ViewModel (Completed)
- `LibraryViewModel.kt`:
  - Added `LibraryFilter` enum (`ALL`, `SERIES`, `READING_LISTS`, `UNREAD`).
  - Added `LibraryItem` sealed interface:
    - `LibraryItem.SingleComic(val comic: ComicEntity)`
    - `LibraryItem.SeriesStack(val seriesName: String, val volume: String?, val issues: List<ComicEntity>, val topCoverComic: ComicEntity, val totalIssues: Int, val unreadIssues: Int, val progressFraction: Float)`
    - `LibraryItem.ReadingListItem(val readingList: ReadingListEntity, val previewComics: List<ComicEntity>, val itemCount: Int)`
  - Added reactive `libraryItems: StateFlow<List<LibraryItem>>` combining `comics`, `readingLists`, `searchQuery`, and `libraryFilter`.
  - Added series helper methods: `getSeriesIssues(seriesName, volume)`, `markSeriesRead(...)`, reading list methods.

---

## 3. Remaining Implementation Roadmap

### Phase 3: Visual ComicStackCard & Adaptive Filter UI (`LibraryScreen.kt` & `MenuScreen.kt`)
1. **`ComicStackCard` Composable:**
   - Decked cards visual: offset and rotated back card (`rotationZ = 2.5f`, `offset = 4.dp`, halftone border) underneath the main cover.
   - Top badge: `SERIES · X ISSUES` (or volume badge e.g. `VOL. 2`).
   - Composite progress bar at bottom showing read fraction across all issues in the stack.
   - Click opens `onOpenSeries(seriesName, volume)`.
2. **Adaptive Filter Bar:**
   - Check `LocalConfiguration.current.screenWidthDp >= 600`:
     - If `>= 600.dp`, display horizontal Leaf chip row (`ALL`, `SERIES`, `READING LISTS`, `UNREAD`) below header.
     - If `< 600.dp`, render in `MenuScreen.kt` under a "LIBRARY FILTER" section, and show an active filter chip in `LibraryScreen.kt` when not `ALL` (e.g. `[ SERIES ✕ ]` to quickly clear).
3. **Connect Grid to `viewModel.libraryItems`:**
   - Render `items(libraryItems)` dispatching to `ComicCard` for `SingleComic`, `ComicStackCard` for `SeriesStack`, or `ReadingListCard` for `ReadingListItem`.

### Phase 4: Dedicated Series View Screen & Routes
1. **`SeriesDetailScreen.kt`:**
   - Hero header: Series title, volume, cover art, total pages/issues, unread count.
   - Primary action button: `"CONTINUE: ISSUE #X"` (opens the next unread issue in reader) or `"READ FROM START (#1)"`.
   - Grid of issues in natural issue number order (`#1`, `#2`...).
   - Long-click / menu actions: Mark all as read/unread.
2. **Navigation Wiring (`AppNavHost.kt`):**
   - Add route `series/{seriesName}?volume={volume}`.
   - Connect `onOpenSeries` from `LibraryScreen` to `navController.navigate(Routes.series(seriesName, volume))`.

---

## 4. Copy-Paste Handoff Prompt

When resuming work, copy-paste this prompt:

```text
We are continuing the Comic Stacking and Reading Lists implementation for Leaf (Android comic reader).
Refer to LIBRARY_ORGANIZATION_PLAN_AND_HANDOFF.md for complete architecture and progress details.

Branch: feat/library-organization-stacking in /Users/bryanclark/Repos/chika

Current Status:
- Phase 1 (Metadata extraction, Room database v3 MIGRATION_2_3, ComicEntity, ReadingListDao, LibraryRepository) is complete and tested.
- Phase 2 (LibraryFilter, LibraryItem models, and libraryItems flow grouping series stacks in LibraryViewModel) is complete.

Next Steps to execute:
Proceed with Phase 3 & 4:
1. In LibraryScreen.kt:
   - Build ComicStackCard (decked multi-card visual with rotation offset, series badge, composite progress).
   - Render adaptive filter chips (ALL, SERIES, LISTS, UNREAD) when screenWidthDp >= 600dp, or in MenuScreen.kt when compact.
   - Update grid to consume viewModel.libraryItems instead of raw comics list.
2. In SeriesDetailScreen.kt:
   - Create dedicated screen with hero header, "READ NEXT: ISSUE #X" button, and issue grid.
3. In AppNavHost.kt:
   - Add Routes.SERIES and connect navigation from LibraryScreen to SeriesDetailScreen.
4. Verify with build and assembleDebug.
```
