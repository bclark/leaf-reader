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
  * Subtitle: `CUSTOM STORYLINE / READING LIST`.
  * Quick action icons in toolbar:
    * **Edit / Rename**: Opens dialog to update the reading list name.
    * **Delete**: Opens confirmation dialog to delete the list (clearly indicating comics remain in the library).
* **Hero Card**:
  * Badge: `X COMICS IN LIST` / `1 COMIC IN LIST`.
  * Actions row:
    * **ADD COMICS**: Opens multi-select dialog to add library comics not yet in the list.
    * **MARK READ / MARK UNREAD**: Toggles all comics in the reading list between completed (`lastPage = pageCount - 1`) and unread (`lastPage = 0`).
  * **Dynamic Primary Action Button**:
    * `START: [FIRST COMIC]` when no comics in the list have been read.
    * `CONTINUE: [TITLE] (PG X/Y)` when a comic in the list is in progress (`lastPage > 0` and uncompleted).
    * `READ FROM START: [FIRST COMIC]` when all comics in the list are completed.

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
2. Type in the search box to find specific titles.
3. Select 3 comics across different series and tap `ADD (3)`.
4. **Verify**: All 3 comics appear in the list with badges `#1`, `#2`, and `#3`.
5. **Verify**: The hero badge updates to `3 COMICS IN LIST`.

### Test Case 3: Reorder Reading Sequence
1. On comic `#3`, tap the Move Up arrow (`▲`).
2. **Verify**: Comic `#3` moves into position `#2`, and the previous `#2` becomes `#3`.
3. Long-press on comic `#1` and select `MOVE DOWN IN READING ORDER`.
4. **Verify**: Comic `#1` becomes `#2`.
5. Exit to the library and return: verify the updated sequence is persistent.

### Test Case 4: Dynamic Hero CTA Progression
1. With all comics unread, verify the hero button says `START: [FIRST COMIC]`.
2. Tap the button; verify it opens Comic #1 in the reader.
3. Read a few pages and back out.
4. **Verify**: The hero button updates to `CONTINUE: [COMIC #1] (PG X/Y)`.
5. Tap `MARK READ` in the hero header.
6. **Verify**: All comics in the list show 100% progress bars, the header button changes to `MARK UNREAD`, and the hero button updates to `READ FROM START: [FIRST COMIC]`.

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
| `ReadingListDetailScreen.kt` | `ReadingListDetailView.swift` | Enhance existing view with sequence badges (`#1`, `#2`), quick Move Up/Down controls, and hero CTA. |
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

2. **Reorder Actions**:
   Provide both quick buttons and context menu items:
   ```swift
   Button {
       library.moveItemInList(listId: listId, comicURL: comic, direction: -1)
   } label: {
       Image(systemName: "arrow.up")
   }
   ```

3. **Dynamic Hero CTA**:
   ```swift
   var heroButtonText: String {
       if allCompleted, let first = listComics.first {
           return "READ FROM START: \(first.deletingPathExtension().lastPathComponent.uppercased())"
       } else if let next = nextUnread, let p = ReadingProgress.get(next), p.page > 0 {
           return "CONTINUE: \(next.deletingPathExtension().lastPathComponent.uppercased())"
       } else if let next = nextUnread {
           return "START: \(next.deletingPathExtension().lastPathComponent.uppercased())"
       }
       return "READ"
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
