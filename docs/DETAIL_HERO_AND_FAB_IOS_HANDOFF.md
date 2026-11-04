# Series & Reading List Detail Hero Bar Redesign: iOS Parity Handoff

## 1. Overview
Following on-device user testing on Android (`ReadingListDetailScreen.kt` and `SeriesDetailScreen.kt`), the multi-row Hero Card at the top of both detail screens was found to be visually busy and cramped on narrower viewports.

We redesigned both screens to use a **compact single-row Hero Status Bar** paired with a **bottom-right pinned `+ ADD COMICS` Floating Action Button** (matching the main Library screen).

This handoff documents the exact visual and behavioral updates needed in `iosApp/Sources/ReadingListDetailView.swift` and `iosApp/Sources/SeriesDetailView.swift`.

---

## 2. Key UI Changes (Verified on Android)

### A. Bottom-Right Pinned `+ ADD COMICS` FAB
* **Removed** `+ ADD COMICS` from the top Hero Card.
* **Added** an `ExtendedFloatingActionButton` pinned to `Alignment.BottomEnd` (`padding = 20pt`), matching the `+ ADD` button on the main `ALL` Library screen:
  * Background: `Leaf.ochre` (`#D8B14A`)
  * Foreground: `Leaf.ink` (`#0D1510`)
  * Icon: `plus` (`18pt`)
  * Label: `ADD COMICS` in `Anton` (`15pt`, tracking `0.8pt`)
* **Grid Bottom Inset**: Increased the bottom padding of the `LazyVGrid` content to `88pt` so the floating button never covers the bottom row of cards when scrolled to the end.

### B. Unified Read-Progress Badge (Merged Count + Read Status)
* **Removed** the redundant separate `[3 COMICS]` / `[6 ISSUES]` badge that sat next to `0 OF 3 READ`.
* **Replaced** with a single bordered progress pill on the left side of the Hero Status Bar:
  * **Text**:
    * Reading List: `"\(completedCount) OF \(listComics.count) READ"` (or `"0 COMICS"` if empty)
    * Series Detail: `"\(completedCount) OF \(seriesComics.count) READ"` (or `"0 ISSUES"` if empty)
  * **Typography**: `Anton` (`12pt`, tracking `0.5pt`)
  * **Color & Border**:
    * When `allCompleted == false`: Text and `1pt` border use `Leaf.ochre` (`#D8B14A` / `0.5` alpha border), background `Leaf.ink.opacity(0.6)`.
    * When `allCompleted == true`: Text and `1pt` border switch to `Leaf.accent` (`#4ADE80` / `0.6` alpha border).
* **Slim Progress Bar**:
  * Directly beneath the unified badge (`6pt` spacing), render a slim `3pt` height horizontal progress bar (`completedCount / totalCount`) in `Leaf.ochre` on a `Leaf.inkSoft.opacity(0.25)` track.

### C. Custom Book Status Toggle Icon (`BookStatusBadgeIcon`)
* **Replaced** the text-based `MARK READ` / `MARK UNREAD` button (and plain checkmark button) with a dedicated `36×36pt` rounded-square (`8pt` radius) icon button containing a book-with-badge composite icon:
  * **Mark All as Read (`isAllCompleted == false`)**:
    * Base Icon: Open book (`MenuBook` on Android / `book.pages.fill` or `book.fill` on iOS, `16pt`, `Leaf.cream`).
    * Corner Badge (`13×13pt` circle at bottom-trailing with `1.5pt` `Leaf.deepSurface` stroke): Filled with `Leaf.accent` (`#4ADE80`) and a bold `checkmark` (`✓`, `Leaf.ink`).
    * Meaning: *"Mark all issues/books in this series or list as finished."*
  * **Mark All as Unread (`isAllCompleted == true`)**:
    * Base Icon: Closed book (`Book` on Android / `book.closed.fill` on iOS, `16pt`, `Leaf.cream`).
    * Corner Badge (`13×13pt` circle at bottom-trailing with `1.5pt` `Leaf.deepSurface` stroke): Filled with `ComicRose` (`#A15555`) and a bold `minus` (`−`, `Leaf.cream`).
    * Meaning: *"Reset progress back to unread from page one."*

### D. Compact Primary Action Pill (`▶ START` / `▶ CONTINUE` / `▶ RESTART`)
* **Replaced** the full-width bottom CTA bar (and standalone square play icon) with a compact `36pt`-tall pill on the right side of the Hero Status Bar, immediately to the right of the `BookStatusBadgeIcon` button (`8pt` spacing):
  * Background: `Leaf.ochre` (`#D8B14A`), corner radius `8pt`, `1.5pt` `Leaf.ink` border.
  * Horizontal padding: `12pt`.
  * Content: `play.fill` icon (`14pt`, `Leaf.ink`) + `4pt` spacing + `Anton` (`13pt`, `Leaf.ink`, tracking `0.6pt`) label:
    * `"START"` when `completedCount == 0` and `nextUnread` has `page == 0`.
    * `"CONTINUE"` when `nextUnread` has `page > 0` or `completedCount > 0` (and not all completed).
    * `"RESTART"` when `allCompleted == true`.

---

## 3. Reference SwiftUI Implementation (`iosApp/`)

```swift
struct BookStatusBadgeIcon: View {
    let isAllCompleted: Bool

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Image(systemName: isAllCompleted ? "book.closed.fill" : "book.pages.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(Leaf.cream)
                .frame(width: 22, height: 22, alignment: .topLeading)

            Circle()
                .fill(isAllCompleted ? Color(red: 0.63, green: 0.33, blue: 0.33) : Leaf.accent)
                .frame(width: 13, height: 13)
                .overlay(
                    Circle().stroke(Leaf.deepSurface, lineWidth: 1.5)
                )
                .overlay(
                    Image(systemName: isAllCompleted ? "minus" : "checkmark")
                        .font(.system(size: 7, weight: .black))
                        .foregroundColor(isAllCompleted ? Leaf.cream : Leaf.ink)
                )
        }
        .frame(width: 24, height: 24)
    }
}
```

### Compact Hero Bar Layout (`ReadingListDetailView.swift` & `SeriesDetailView.swift`)
```swift
HStack(spacing: 12) {
    // Left: Unified Read Badge + Slim Progress Bar
    VStack(alignment: .leading, spacing: 6) {
        let badgeColor = allCompleted ? Leaf.accent : Leaf.ochre
        Text(items.isEmpty ? emptyLabel : "\(completedCount) OF \(items.count) READ")
            .font(.anton(12))
            .tracking(0.5)
            .foregroundColor(badgeColor)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(Leaf.ink.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: 4))
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(badgeColor.opacity(0.55), lineWidth: 1)
            )

        if !items.isEmpty {
            ProgressView(value: Double(completedCount), total: Double(items.count))
                .tint(Leaf.ochre)
                .scaleEffect(x: 1, y: 0.75, anchor: .center)
        }
    }
    .frame(maxWidth: .infinity, alignment: .leading)

    // Right: Book Status Toggle + Compact Start/Continue/Restart Pill
    if !items.isEmpty {
        HStack(spacing: 8) {
            Button(action: toggleAllRead) {
                BookStatusBadgeIcon(isAllCompleted: allCompleted)
                    .frame(width: 36, height: 36)
                    .background(Leaf.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Leaf.inkSoft.opacity(0.35), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)

            if let target = primaryTargetComic {
                Button {
                    onOpenComic(target)
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text(actionLabel)
                            .font(.anton(13))
                            .tracking(0.6)
                    }
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 12)
                    .frame(height: 36)
                    .background(Leaf.ochre)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Leaf.ink, lineWidth: 1.5)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}
.padding(.horizontal, 14)
.padding(.vertical, 10)
.background(Leaf.deepSurface)
.clipShape(RoundedRectangle(cornerRadius: 10))
.overlay(
    RoundedRectangle(cornerRadius: 10)
        .stroke(Leaf.inkSoft.opacity(0.28), lineWidth: 1)
)
```

---

## 4. Architectural Boundary
* All changes described in this document belong strictly in `iosApp/Sources/ReadingListDetailView.swift` and `iosApp/Sources/SeriesDetailView.swift`.
* No changes to `shared/` or physical storage are required.
