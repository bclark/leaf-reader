# Reading Lists & Storyline Arcs: iOS Parity & Verification Handoff

## 1. Executive Summary
The **Reading Lists & Storyline Arcs** feature allows users to curate custom reading orders and multi-series crossover events (e.g. *Civil War*, *Infinity Gauntlet*, character-specific arcs) that transcend publisher series structures.

This feature promotes Reading Lists from basic alert popups to a first-class, dedicated full-screen experience matching the visual fidelity and UX of `SeriesDetailScreen`.

---

## 2. Feature Specification (Verified on Android)

### A. Dedicated Full-Screen Reading List Experience (`ReadingListDetailScreen`)
* **Route**: `reading_list/{listId}` (accessible via `Routes.readingList(listId)`).
* **App Bar**:
  * Back button (`chevron.left` / `ArrowBack`).
  * List title in brand Anton font (`18.sp`, uppercase).
  * Subtitle: `CUSTOM READING LIST` (`11.sp`, `InkSoft.copy(alpha = 0.7f)`).
  * Quick action icons in toolbar:
    * **Edit / Rename**: Opens dialog to update the reading list name.
    * **Delete**: Opens confirmation dialog to delete the list (clearly indicating comics remain in the library).
* **Compact Hero Status Bar (Single-Row Card)**:
  * **Left Column (Progress Summary)**:
    * **Unified Progress Badge**: Single bordered pill showing `"X OF Y READ"` (or `"0 COMICS"` when empty) in Anton font (`12.sp`, letterSpacing `0.5.sp`). Colored `Ochre` (`#D8B14A`) while unread/in-progress, switching to `LeafAccent` green (`#4ADE80`) when all comics are completed.
    * **Progress Bar**: Slim `3.dp` height `LinearProgressIndicator` (`completedCount / totalCount`) directly below the badge when `totalCount > 0`.
  * **Right Row (Quick Actions)**:
    * **Mark All Read / Unread Icon Button (`36.dp` × `36.dp` rounded square, `LeafSurface` background, `InkSoft` border)**:
      * **Mark All as Read** (when not all completed): Open book icon (`MenuBook` / SF Symbol `book.fill` or `book.pages`) with a `13.dp` circular `LeafAccent` (`#4ADE80`) checkmark badge (`✓`) in the bottom-right corner.
      * **Mark All as Unread** (when all completed): Closed book icon (`Book` / SF Symbol `book.closed.fill`) with a `13.dp` circular `ComicRose` (`#A15555`) minus badge (`−`) in the bottom-right corner.
    * **Primary Action Pill (`36.dp` height, `Ochre` background, `1.5.dp` `Ink` border, `8.dp` corner radius)**:
      * Play icon (`16.dp`) + Anton label (`13.sp`, `Ink`):
        * `▶ START` when no comics in the list have been read.
        * `▶ CONTINUE` when a comic in the list is in progress (`lastPage > 0` and uncompleted).
        * `▶ RESTART` when all comics in the list are completed.
* **Pinned Bottom-Right FAB (`+ ADD COMICS`)**:
  * Moved out of the hero card and pinned to `Alignment.BottomEnd` (`padding(20.dp)`), matching the main Library screen's `+ ADD` FAB.
  * Styled with `Ochre` container color, `Ink` content color, `+` icon, and `ADD COMICS` Anton label (`15.sp`).
  * Grid bottom content padding increased to `88.dp` so the FAB never obscures the bottom row of cards.

### B. Natural Custom Ordering & Reordering
* Comics in a reading list are strictly ordered by `sortOrder ASC`.
* **Issue Badges**: Each card displays its sequence number in the list: `#1`, `#2`, `#3`... reflecting its exact position in the reading order.
* **Quick Reorder Affordances**:
  * Upward arrow (`▲`): Swaps item with the preceding item (`sortOrder - 1`). Only visible if `index > 0`.
  * Downward arrow (`▼`): Swaps item with the following item (`sortOrder + 1`). Only visible if `index < size - 1`.
* **Direct Remove Button (`X`)**: Quick button on the top-right of each card with a confirmation prompt.
* **Long-Press Options Modal**:
  1. `CONTINUE READING` / `READ COMIC`
  2. `MARK AS READ` / `MARK AS UNREAD`
  3. `MOVE UP IN READING ORDER` (if not first)
  4. `MOVE DOWN IN READING ORDER` (if not last)
  5. `REMOVE FROM LIST`

### C. Multi-Select "Add Comics" Dialog
* Lists all comics in the user's library that are not already present in the reading list.
* Live search bar filtering by comic title or series name.
* Checkbox selection toggle with batch counter: `ADD (X)`.

---

## 3. Manual Verification Checklist (Tested on Android)

### Test Case 1: Create Reading List & Full-Screen Navigation
1. In the main Library screen, switch to the `LISTS` filter tab.
2. Tap the `+` / Add Tile or create list action. Enter a name (e.g. `Cosmic Crossover`).
3. **Verify**: The app navigates directly into the full-screen `ReadingListDetailScreen`.
4. **Verify**: Empty state displays "THIS LIST IS EMPTY" with an invitation to add comics.

### Test Case 2: Multi-Select Add Comics
1. Tap `ADD COMICS` in the hero card.
### Test Case 2: Multi-Select Add Comics (Bottom-Right FAB)
1. Tap the pinned `+ ADD COMICS` floating action button in the bottom-right corner.
2. Type in the search box to find specific titles.
3. Select 3 comics across different series and tap `ADD (3)`.
4. **Verify**: All 3 comics appear in the list with badges `#1`, `#2`, and `#3`.
5. **Verify**: The hero badge updates to `0 OF 3 READ`.

### Test Case 3: Reorder Reading Sequence
1. On comic `#3`, tap the Move Up arrow (`▲`).
2. **Verify**: Comic `#3` moves into position `#2`, and the previous `#2` becomes `#3`.
3. Long-press on comic `#1` and select `MOVE DOWN IN READING ORDER`.
4. **Verify**: Comic `#1` becomes `#2`.
5. Exit to the library and return: verify the updated sequence is persistent.

### Test Case 4: Dynamic Hero Bar & Book Status Badge Progression
1. With all comics unread, verify the hero bar shows `0 OF 3 READ`, the open-book-with-checkmark icon (`BookStatusBadgeIcon(isAllCompleted = false)`), and the `▶ START` pill.
2. Tap `▶ START`; verify it opens Comic #1 in the reader.
3. Read a few pages and back out.
4. **Verify**: The action pill updates to `▶ CONTINUE`.
5. Tap the book-with-checkmark button in the hero bar.
6. **Verify**: All comics in the list show 100% progress bars, the hero badge turns `LeafAccent` green (`3 OF 3 READ`), the book button switches to the closed-book-with-minus icon (`BookStatusBadgeIcon(isAllCompleted = true)`), and the action pill updates to `▶ RESTART`.

### Test Case 5: Rename and Delete
1. Tap the edit pencil in the top-right corner, enter a new name, and save.
2. **Verify**: Title updates immediately.
3. Tap the delete trash icon and confirm.
4. **Verify**: The reading list is deleted and the screen returns to the Library. The underlying comics remain intact in the library.

---

## 4. iOS Parity Requirements & Technical Guidance

All iOS work is strictly scoped to `iosApp/`.

### Architecture Mapping
| Android Component | iOS Equivalent (`iosApp/`) | Action Required |
| :--- | :--- | :--- |
| `ReadingListDetailScreen.kt` | `ReadingListDetailView.swift` | Replace multi-row hero card with single-row status bar (`X OF Y READ` + `BookStatusBadgeIcon` + `▶ START/CONTINUE/RESTART` pill) and move `+ ADD COMICS` to bottom-right FAB. |
| `BookStatusBadgeIcon` (`LeafBrand.kt`) | `BookStatusBadgeIcon` (`View`) | Open book (`book.pages` / `book.fill`) + green `✓` badge when not all read; closed book (`book.closed.fill`) + rose `−` badge when all read. |
| `Routes.READING_LIST` | SwiftUI NavigationLink / Sheet | Ensure tapping `ReadingListCard` in `LibraryView` opens full `ReadingListDetailView`. |
| `reorderReadingList()` / `moveReadingListItem()` | `ReadingListStore.reorder()` | Add item move and reorder persistence to `ReadingListStore`. |
| `markReadingListRead()` | `LibraryStore.markListRead()` | Implement batch toggle for all items in `currentList`. |
| `LibraryViewModelTest.kt` | `ReadingListStoreTests.swift` | Add unit tests covering creation, reordering, deletion, and batch mark read/unread. |

### Swift Implementation Details (`iosApp/`)

1. **Sequence Badging in `ReadingListDetailView.swift`**:
   Display position index prominently:
   ```swift
   Text("#\(index + 1)")
       .font(.anton(columns >= 4 ? 9 : 10.5))
       .foregroundColor(Leaf.cream)
       .padding(.horizontal, 6)
       .padding(.vertical, 2)
       .background(Leaf.ink.opacity(0.88))
       .clipShape(RoundedCornerShape(cornerRadius: 3))
       .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Leaf.ochre, lineWidth: 1))
   ```

2. **Book Status Badge Icon (`BookStatusBadgeIcon`)**:
   ```swift
   struct BookStatusBadgeIcon: View {
       let isAllCompleted: Bool
       var body: some View {
           ZStack(alignment: .bottomTrailing) {
               Image(systemName: isAllCompleted ? "book.closed.fill" : "book.pages.fill")
                   .font(.system(size: 16, weight: .semibold))
                   .foregroundColor(Leaf.cream)
                   .frame(width: 22, height: 22, alignment: .topLeading)
               Circle()
                   .fill(isAllCompleted ? Color(hex: 0xA15555) : Leaf.accent)
                   .frame(width: 13, height: 13)
                   .overlay(Circle().stroke(Leaf.deepSurface, lineWidth: 1.5))
                   .overlay(
                       Image(systemName: isAllCompleted ? "minus" : "checkmark")
                           .font(.system(size: 7, weight: .bold))
                           .foregroundColor(isAllCompleted ? Leaf.cream : Leaf.ink)
                   )
           }
           .frame(width: 24, height: 24)
       }
   }
   ```

3. **Compact Hero Action Pill Label**:
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

---

## 5. Architectural Boundary & Shared Engine Protocol

* **iOS Implementation Scope**: All tasks outlined in this handoff must be performed strictly within the iOS application directory (`iosApp/`).
* **Shared Engine / Storage Protocol**:
  If the iOS implementation discovers a need for new capabilities, models, or algorithms in the **Shared KMP Engine** (`shared/`) or changes to **Physical Storage** (e.g. archive decoding, database schemas, backup JSON structures):
  1. Do not edit `shared/` or Android code from the iOS workflow.
  2. Create a handoff document in `docs/` detailing the requested KMP API or storage specification.
  3. The Android & Shared KMP team will implement, test, and expose it via the `LeafShared` framework.
