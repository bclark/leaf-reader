# iOS handoff: Android 0.3.6

## Purpose and current state

Bring the Android 0.3.6 reader dismissal, appearance controls, and library presentation refinements to iOS. This document describes the final behavior after user testing; it supersedes the earlier idea of a page attached directly to the finger with immediate dismissal at 66%.

Android implementation is complete. iOS runtime behavior is still to be implemented and tested. The iOS AppIcon catalog has already been updated separately with light and dark 1024px images; preserve that work. Do not assume importing icon appearances implements app theming or manual alternate icons.

Relevant Android sources:

- `app/src/main/java/net/digitalbc/leafreader/ui/reader/ReaderScreen.kt`
- `shared/src/commonMain/kotlin/net/digitalbc/leafreader/ui/reader/ReaderDismissGesture.kt`
- `shared/src/commonTest/kotlin/net/digitalbc/leafreader/ui/reader/ReaderDismissGestureTest.kt`
- `app/src/main/java/net/digitalbc/leafreader/data/settings/{AppSettings,ThemeMode,LauncherIcons}.kt`
- `app/src/main/java/net/digitalbc/leafreader/ui/theme/Theme.kt`
- `app/src/main/java/net/digitalbc/leafreader/ui/menu/MenuScreen.kt`
- `app/src/main/java/net/digitalbc/leafreader/ui/library/{LibraryScreen,LibraryViewModel,SeriesDetailScreen,ReadingListDetailScreen}.kt`
- `app/src/test/java/net/digitalbc/leafreader/ui/library/LibraryViewModelTest.kt`

Likely iOS integration points: `ReaderGestures.swift`, `ReaderView.swift`, `LeafSettings.swift`, `LeafApp.swift`, `LeafTheme.swift`, `MenuView.swift`, `LibraryStore.swift`, `LibraryView.swift`, `SeriesDetailView.swift`, and `ReadingListDetailView.swift`.

## 1. Flick downward to dismiss a comic

### Gesture ownership

Use the existing transparent UIKit gesture surface in `ReaderGestures.swift`; do not install an independent competing full-screen SwiftUI drag recognizer.

A dismissal candidate must start with one finger and downward-dominant intent: dy > 0 and dy > abs(dx). Horizontal intent retains page flipping. Pinching or a second finger cancels dismissal, and zoomed pages retain panning.

Only allow dismissal when user zoom <= 1.05 and the currently framed content fits vertically in the viewport (Android allows a 1px rounding tolerance). A tall Fit Width page must scroll vertically instead. Use the existing shared framing functions to make that decision for single pages and spreads.

UIKit's pan recognizer currently delivers both ended and cancelled through `onPanEnded`, and `onPanChanged` does not report touch count. Extend those callbacks or provide explicit cancellation/touch-count information before wiring dismissal. Cancellation must never be treated as a release that can navigate back.

### Tuning and units

The pure shared Kotlin `ReaderDismissGesture` object owns portable intent and release policy:

| Setting | Current value | Meaning |
| --- | --- | --- |
| ENGAGE_DISTANCE_DP | 48 | Initial downward travel before artwork starts moving |
| DRAG_RESISTANCE | 0.55 | Fraction of subsequent drag applied to artwork |
| DISMISS_FRACTION | 0.66 | Projected vertical travel as fraction of viewport height |
| VELOCITY_PROJECTION_SECONDS | 0.30 | Momentum projection horizon |

Android converts 48dp to device pixels. For iOS, start with 48 logical points. Feed translation, velocity, and viewport size in consistent units: points and points/second are valid together. Do not mix physical pixels with UIKit points.

Rebuild the shared framework and inspect its generated Swift interface. Reuse `isDownward`, `progress`, and `shouldDismiss` rather than copying their policy. Swift callers must pass all three `shouldDismiss` arguments explicitly; Kotlin default arguments are not exported as Swift defaults. If an exported constant's Swift name differs, use the actual generated name rather than guessing an accessor.

### Artwork motion and release

1. Reserve the first 48 points of downward travel for intent. The page stays put during this portion.
2. Preserve the drag angle when subtracting that initial travel: start = translation * (48 / translation.height).
3. Artwork offset = prior artwork offset + (translation - start) * 0.55. Clamp artwork Y to >= 0.
4. Translate only artwork. The gesture surface, controls, and coordinate system remain stable.
5. Add gentle tilt: clamp(offset.x / viewport.width * 14 degrees, -10, +10).
6. Uniform scale = 1 - clamp(offset.y / viewport.height, 0, 1) * 0.08.
7. On release, dismiss if offset.y + velocity.y * 0.30 >= viewport.height * 0.66. The decision is made on release; crossing 66% while holding does not immediately navigate.
8. If accepted, continue in the release direction. Android uses slope = vx/vy when vy > 0, otherwise offset.x/max(offset.y, 1); clamp slope to [-2, 2]. Add a target travel vector of (slope * viewportHeight * 1.3, viewportHeight * 1.3).
9. Animate off-screen for about 280ms with a decelerating curve, then invoke the normal back/dismiss action exactly once. Preserve the existing progress-save path.
10. A rejected or cancelled gesture returns to zero with a gentle spring. Android tuning is damping ratio 0.78 and medium-low stiffness; use a comparable UIKit/SwiftUI spring rather than copying incompatible units.

Interrupt a return animation when a new valid drag starts. Do not let a second dismissal, tap, or page turn fire during the committed fly-out. Clean up animations on lifecycle changes; if an outgoing animation is cancelled without navigating, restore the idle/commit guard so the reader cannot remain locked.

This gesture is separate from the library's cover-cycling drag below. It does not change page curl rendering, reading direction, panel ordering, or slot conventions.

## 2. App appearance and icon choice

Add independent settings in Menu -> Appearance:

- App theme: System / Light / Dark, persisted in UserDefaults.
- AMOLED Black: retained as a dark-mode option; hidden while explicitly in Light mode. In System mode it applies only when the resolved system appearance is dark.
- App icon: Light / Dark illustration, independent of the app theme. Native iOS icon appearance handling differs from Android; see below.

New/default app theme is System. If an existing install has AMOLED enabled and no explicit theme choice, migrate it to Dark so its selected true-black appearance is preserved. Once a mode is chosen, toggling AMOLED must not implicitly change it on the next launch.

Apply the selected appearance at the app root: System uses no forced color scheme, Light/Dark force their corresponding scheme. Replace fixed dark surfaces/text throughout library, list/series details, settings, dialogs, SMB screens, and reader support UI with semantic theme colors. Audit nested sheets that currently force dark appearance.

Android light colors for matching the current forest/gold design:

| Role | Color |
| --- | --- |
| Background / surface | #FAFAF7 |
| Raised surface | #EAEDE5 |
| Main text | #14191C |
| Secondary text | #4A544C |
| Forest primary/header | #1C3A27 |
| Gold button/container | #D4AF37 |
| Gold-colored text on pale surfaces | #795C00 |

Keep text/icons on forest headers white. Add Comic/Add Comics buttons use the bright gold container with dark text, not the darker gold text color. Dark mode retains the current forest/ink palette. AMOLED makes main grounds black and raised surfaces near-black.

The iOS `Assets.xcassets/AppIcon.appiconset/Contents.json` already references `leaf-reader-light-1024.png` and `leaf-reader-dark-1024.png`. Those are automatic OS-supported appearances of a single icon, not separately registered alternate icons. If offering a manual illustration selector on iOS, implement native alternate icon catalogs/plist configuration and the platform's supported icon-changing API. Do not copy Android launcher aliases into iOS or promise that an in-app picker overrides all OS home-screen tint/appearance settings.

Reconcile the native icon choice with iOS availability and behavior before shipping its picker. Preserve automatic light/dark catalog appearances even if manual alternate icons are deferred.

## 3. Series/list cover stacks

Preserve the existing neat series stack and scattered/rotated reading-list geometry.

### Borders and shadows

- Make black cover outlines fully transparent while retaining their stroke widths, so this remains an easy visual experiment to reverse. Apply to standalone issue cards, stack fronts/promoted middle covers, and issue grids in series and reading-list details.
- Preserve gold selection indicators and gold metadata badge outlines; these communicate state and are not the black cover frame.
- The backmost visible cover alone casts the stack's external shadow: third layer for 3+ items, second for exactly two, front for zero/one.
- Use a soft shadow following that cover's rounded rectangle and its actual rotation/scale. Do not paint an axis-aligned opaque black rectangle behind the front cover.
- Android currently uses native elevation equivalent to about 4.8dp in dense grids or 8dp otherwise, 4dp rounded corners, and low-alpha ambient/spot shadows (0.18 / 0.24). Approximate visually on iOS; elevation is not a SwiftUI shadow radius.
- Keep shadow placement consistent while dragging/cycling. Do not grow a new front or middle shadow when another back layer exists.

### Depth dimming

Render back-cover images fully opaque, then dim them using black overlays. Image opacity alone washes them toward the theme background and produces a different effect in light mode.

- Middle cover overlay: black at 0.16 alpha.
- Back cover overlay: black at 0.28 alpha.
- Existing library cycle threshold: 35% of card height. Keep it distinct from reader dismissal's 66% projected threshold.
- When drag progress >= 0.35, animate a shared dim multiplier from 1 to 0 over 180ms. Thus both back layers brighten as the next cover is revealed.
- Returning below the threshold animates dimming back in. On cycling, the promoted front is undimmed and newly exposed back layers regain their depth.
- Android retains the existing library flick commit behavior as well: a downward release >600px/s with at least 15% travel can cycle; a strong upward cancellation avoids distance-only commits. Respect existing native gesture arbitration and scrolling.
- Existing front-cover translation/fade, issue ordering, progress bars, and badges remain in place.

## 4. Include reading lists in All

Update `LibraryStore.libraryItems(filter:searchQuery:)`, which feeds `LibraryView.currentItems`.

- All keeps its existing standalone comic and grouped-series behavior, then includes reading-list cards using the same mapping as the Lists filter.
- Include empty reading lists, even in an otherwise empty comic library.
- List membership does not remove the original comic or series entry from All.
- Search list names using trimmed, case-insensitive text, matching the Lists tab. A list-name match retains its complete ordered member preview; it does not filter the list's contents down to matching comic titles.
- Android appends lists after existing comic/series entries, preserving those entries' order and the Lists tab's list order.
- Preview members retain explicit list order; Android takes up to four previews and reports the complete member count.
- Series and Unread filters remain scoped to their existing content types.

## Acceptance checks

- Slow/short dismissal attempt springs back; a fast downward or diagonal flick exits once, following its direction.
- Sub-48-point movement does not move artwork. Upward/horizontal swipes, pinch, second fingers, cancelled recognizers, and tall Fit Width scrolling do not accidentally dismiss.
- Retry after a cancelled gesture or lifecycle interruption; no stuck input guard.
- Progress and back navigation match the regular Close/Back path. Test a spread and both reading directions.
- System appearance changes while running and after relaunch; explicit modes override it. AMOLED works only in resolved dark mode and migration preserves existing users' choice.
- Light mode has readable headers, dialogs, fields, gold actions, and system bars; inspect SMB sheets too.
- Verify iOS automatic icon appearances and any separately implemented manual alternate icon path on supported OS versions.
- Inspect 1-, 2-, and 3+-cover series/lists in both themes and several grid densities: transparent black frames, natural rotated shadow silhouette, one external stack shadow, dimmed back covers, threshold brightening, reverse animation, no washed-out artwork.
- All shows mixed comics/series/lists; empty lists remain visible; list-name search works without imported comics. Lists/Series/Unread stay correctly filtered.

## Build and verification

Android's shared and app tests, both debug flavors, and lint are the release checks. `ReaderDismissGestureTest`, `AppSettingsTest`, and `LibraryViewModelTest` cover the new portable/settings/filter policies. User device testing guided Android's final gesture and visual tuning; do not treat that as iOS verification.

Follow repository iOS instructions in order: build the shared framework, run XcodeGen, install pods, then build the workspace. Run shared JVM and iOS simulator tests, add native gesture/theme/filter checks where appropriate, and manually verify the acceptance cases on iPhone and iPad. Do not commit generated Xcode projects, workspaces, Pods, private SMB settings, or local signing files.
