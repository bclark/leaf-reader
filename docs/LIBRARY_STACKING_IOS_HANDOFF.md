# Library Stacking & Series Experience: iOS Parity & Verification Handoff

## 1. Overview
The **Library Stacking & Series Experience** enables automated and manual comic grouping into series decks, smart next-issue resume tracking, dedicated series detail views, reading lists, and batch reading operations.

This document details the complete feature specification verified on Android, the test scenarios to validate on device, and the exact requirements for iOS parity.

---

## 2. Feature Specification (Verified on Android)

### A. Automatic & Manual Grouping
- **Auto-Grouping Criterion**:
  - Two or more comics sharing the same `series` and `volume` automatically collapse into a `LibraryItem.SeriesStack` in the `ALL` library tab.
  - Standalone comics (1 issue or no series metadata) remain as `LibraryItem.SingleComic`.
  - In the `SERIES` tab, **all** series (even single-issue series) appear as series stacks, sorted alphabetically by series name.
  - In the `READING_LISTS` tab, curated collections appear as `LibraryItem.ReadingListItem`.
  - In the `UNREAD` tab, only comics with `lastPage == 0` are displayed.
- **Archive Metadata Backfill**:
  - `LibraryRepository.backfillMetadataIfNeeded()`:
    1. If a comic in the database is missing series metadata, it runs `ComicMetadataParser.parseFilename(title)`.
    2. If filename heuristics don't extract `series` and `issueNumber`, it opens the archive (`ComicArchiveFactory.open(archiveFile)`) and reads embedded metadata from `ComicInfo.xml`.
    3. Merges parsed metadata and updates the database record.

### B. Visual Decked `ComicStackCard` & Interactive Peel
- **Layered Visual**:
  - Decks of 2 or 3+ cards with staggered top translation (`-4.dp`, `-8.dp`), subtle scaling (`0.96x`, `0.93x`), and halftone borders.
- **Smart Initial Cover**:
  - `topIndex` is initialized to the index of `topCoverComic` (the first uncompleted issue: `lastPage < pageCount - 1`, falling back to the first issue if all are completed).
  - Opening the library immediately presents the issue the user is currently reading or needs to read next.
- **Dynamic Issue Badges**:
  - Front and deck layers show exact issue numbering if available in metadata (e.g., `ISSUE #4 OF 12`) or sequence position (`ISSUE 4 OF 12`).
  - Single issue or volume cards show `[SERIES] · [VOLUME]` or `1 ISSUE`.
- **Composite Progress**:
  - A bottom progress bar indicates the overall series completion fraction (`completedIssues / totalIssues`).
- **One-Pointer Cover Peel**:
  - Vertical swipe peels the top card down with spring physics, cycling through issues in the stack. Tapping opens the `SeriesDetailScreen`.

### C. Dedicated `SeriesDetailScreen`
- **Compact Hero Status Bar (Single-Row Card)**:
  - **Left Column (Progress Summary)**:
    - **Unified Progress Badge**: Single bordered pill showing `"X OF Y READ"` (or `"0 ISSUES"` when empty) in Anton font (`12.sp`, letterSpacing `0.5.sp`). Colored `Ochre` while unread/in-progress, switching to `LeafAccent` green (`#4ADE80`) when all issues are completed.
    - **Progress Bar**: Slim `3.dp` height `LinearProgressIndicator` (`completedCount / totalCount`) directly below the badge when `totalCount > 0`.
  - **Right Row (Quick Actions)**:
    - **Mark All Read / Unread Icon Button (`36.dp` × `36.dp` rounded square, `LeafSurface` background, `InkSoft` border)**:
      - **Mark All as Read** (when not all completed): Open book icon (`MenuBook` / SF Symbol `book.pages` or `book.fill`) with a `13.dp` circular `LeafAccent` (`#4ADE80`) checkmark badge (`✓`) in the bottom-right corner.
      - **Mark All as Unread** (when all completed): Closed book icon (`Book` / SF Symbol `book.closed.fill`) with a `13.dp` circular `ComicRose` (`#A15555`) minus badge (`−`) in the bottom-right corner.
    - **Primary Action Pill (`36.dp` height, `Ochre` background, `1.5.dp` `Ink` border, `8.dp` corner radius)**:
      - Play icon (`16.dp`) + Anton label (`13.sp`, `Ink`):
        - `▶ START` when starting fresh from an unread issue.
        - `▶ CONTINUE` when an issue is partially read (`lastPage > 0`) or some issues are completed.
        - `▶ RESTART` when all issues in the series are completed.
- **Pinned Bottom-Right FAB (`+ ADD COMICS`)**:
  - Pinned to `Alignment.BottomEnd` (`padding(20.dp)`), matching the main Library screen's `+ ADD` FAB (`Ochre` background, `Ink` text, `+ ADD COMICS` label).
  - Grid bottom content padding set to `88.dp` so the FAB does not overlap cards.
- **Natural Order Grid**:
  - Issues sorted by `issueNumber ?: Float.MAX_VALUE` then `title`.
  - Each item displays individual progress bar, quick-remove `(X)` icon, and cover thumbnail.
- **Issue Actions Modal**:
  - Long-pressing any issue opens an action sheet with:
    1. `CONTINUE READING` / `READ COMIC`
    2. `MARK AS READ` / `MARK AS UNREAD`
    3. `REMOVE FROM SERIES` (detaches issue back to standalone)
- **Add Comics Flow**:
  - `+ ADD COMICS` bottom-right FAB opens a multi-select dialog showing downloaded library comics not yet in the series.

---

## 3. What to Test on Android (Manual Verification Checklist)

When running the `:app` build on your Android device, execute the following test matrix:

### Test Case 1: Automatic Stacking in `ALL` Filter
1. Ensure your library has at least two issues of the same series (e.g. `Comic Title 01.cbz` and `Comic Title 02.cbz`).
2. Open the app on the `ALL` tab.
3. **Verify**: The issues are collapsed into a single `ComicStackCard` with a layered card deck visual behind it.
4. **Verify**: Standalone single comics remain as single items.

### Test Case 2: Resume Indicator & Issue Badging on Stack Card
1. In a multi-issue stack, mark Issue #1 as completed and leave Issue #2 unread.
2. Return to the Library screen.
3. **Verify**: The stack card's top cover is Issue #2 (the next unread issue), **not** Issue #1.
4. **Verify**: The badge reads `ISSUE #2 OF X` (or shows the issue number).
5. **Verify**: The aggregate progress bar at the bottom is partially filled (e.g. 50% for 1 of 2 completed).

### Test Case 3: Interactive Cover Peel
1. On a `ComicStackCard`, drag downward on the cover.
2. **Verify**: The top cover peels downward revealing the card underneath with its badge and cover.
3. Release past the threshold.
4. **Verify**: The stack cycles to the next cover.
5. Tap directly on the card without dragging.
6. **Verify**: It navigates cleanly to the `SeriesDetailScreen`.

### Test Case 4: `SeriesDetailScreen` Compact Hero Status Bar & CTA
1. Open a series with multiple issues.
2. If none have been started, verify the hero bar shows `0 OF X READ`, the open-book-with-checkmark icon (`BookStatusBadgeIcon`), and the `▶ START` pill.
3. Open an issue and read a few pages, then back out.
4. Verify the hero pill updates to `▶ CONTINUE`.
5. Tap `▶ CONTINUE`; verify it opens directly to your saved page.
6. Return and tap the book-with-checkmark button in the hero bar; verify all issues fill their progress bars, the badge turns `LeafAccent` green (`X OF X READ`), the book icon switches to the closed-book-with-minus badge, and the action pill changes to `▶ RESTART`.
7. Tap the closed-book-with-minus button; verify all progress bars reset to empty (`0 OF X READ`).

### Test Case 5: Issue Long-Press Menu & Direct Removal
1. In `SeriesDetailScreen`, long-press on an issue card.
2. **Verify**: An action sheet opens with "Continue Reading/Read Comic", "Mark as Read/Unread", and "Remove from Series".
3. Tap "Mark as Read"; verify that single issue updates its progress bar to full.
4. Tap the `(X)` close icon on the top-right of an issue's card (or choose "Remove from Series" in the long-press menu).
5. Confirm removal.
6. **Verify**: The comic is removed from the series. Return to the main library: verify that comic is now listed as a standalone single comic.

### Test Case 6: Manual Series Addition (Bottom-Right FAB)
1. Inside `SeriesDetailScreen`, tap the pinned bottom-right `+ ADD COMICS` FAB.
2. Select one or more standalone comics from the dialog and tap `ADD`.
3. **Verify**: The comics are immediately added to the series and sorted into their natural issue positions.

### Test Case 7: Tabs & Search Filtering
1. Switch to `SERIES`: verify only series stacks appear, ordered alphabetically.
2. Switch to `UNREAD`: verify only comics with zero reading progress appear.
3. Switch to `LISTS`: verify reading lists appear.
4. Type in the search bar: verify searching filters both by comic title and series name.

---

## 4. iOS Parity Requirements & Technical Guidance

### Architecture Mapping
| Android (`:app`) | iOS (`iosApp`) | Status / Action Needed |
| :--- | :--- | :--- |
| `LibraryRepository.backfillMetadataIfNeeded()` | `ComicMetadataStore.backfillMetadataIfNeeded()` | Check that archive `readComicInfoXml()` is invoked if filename parsing yields no series. |
| `LibraryViewModel.libraryItems` | `LibraryStore.libraryItems` | Implemented; ensure `topCoverComic` defaults to first uncompleted issue (`lastPage < pageCount - 1`). |
| `ComicStackCard` (Jetpack Compose) | `ComicStackCard` (SwiftUI) | Verify `topIndex` starts at the `topCoverComic` index, and issue badges show `#num` if available. |
| `SeriesDetailScreen.kt` | `SeriesDetailView.swift` | Replace multi-row hero card with compact single-row status bar (`X OF Y READ` + `BookStatusBadgeIcon` + `▶ START/CONTINUE/RESTART` pill) and move `+ ADD COMICS` to bottom-right FAB. |
| `LibraryViewModelTest.kt` | `LibraryStoreTests.swift` | Add XCTest cases mirroring the 10 Android Robolectric unit tests. |

### iOS Refinements to Match Android Experience

1. **Top Cover Initialization (`LibraryView.swift`)**:
   Ensure `ComicStackCard` initializes its active index to match `topCoverComic`:
   ```swift
   let initialIndex = stack.issues.firstIndex(of: stack.topCoverComic) ?? 0
   @State private var topIndex: Int = initialIndex
   ```

2. **Issue Number Badge Formatting (`SeriesIssueCard` & `ComicStackCard`)**:
   Format `metadata?.issueNumber` cleanly:
   ```swift
   var issueBadgeText: String {
       if let num = metadata?.issueNumber {
           let formatted = (num.truncatingRemainder(dividingBy: 1) == 0) ? "\(Int(num))" : "\(num)"
           return "ISSUE #\(formatted) OF \(totalIssues)"
       }
       return "ISSUE \(index + 1) OF \(totalIssues)"
   }
   ```

3. **Compact Hero Action Pill Label in `SeriesDetailView.swift`**:
   ```swift
   var actionLabel: String {
       if allCompleted {
           return "RESTART"
       } else if let next = nextUnread, (ReadingProgress.get(next)?.page ?? 0) > 0 || completedCount > 0 {
           return "CONTINUE"
       } else {
           return "START"
       }
   }
   ```

4. **Issue Quick Remove Button & Context Menu**:
   In `SeriesDetailView`, provide both the top-right overlay `(xmark)` button for fast removal and the `.contextMenu` containing:
   - Read / Continue
   - Toggle Read / Unread
   - Remove from Series

---

## 5. Architectural Boundary & Shared Engine Requests

All work outlined in this document is strictly confined to the iOS application target (`iosApp/`). 

If iOS implementation requires additional functionality, shared models, or parsing algorithms from the **Shared KMP Engine** (`shared/`) or modifications to **Physical Storage** (archive decoders, database migrations, backup JSON/ZIP structures, file layout):
1. **Do not modify shared or storage code from the iOS workflow.**
2. Author an **Engine/Storage Handoff Document** in `docs/` detailing the needed API signature, data structure, or behavior.
3. The Android & Shared KMP maintainer will implement, verify with tests, and expose it via the `LeafShared` framework for iOS consumption.

