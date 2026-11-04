# Leaf iOS Reader: 3D Page Flip & Curl Handoff Document

**Target Component:** iOS Reader View (`iosApp/Sources/ReaderView.swift`, `iosApp/Sources/PageCurlRenderer.swift`, `iosApp/Sources/PageCurlView.swift`, `iosApp/Sources/ReaderTurnLogic.swift`)  
**Branch:** `feat/ios-reader-page-flip-curl`  
**Date:** November 4, 2026  

---

## 1. Executive Summary

Flat 2D slide transitions in the iOS reader have been replaced with a GPU-accelerated Metal 3D cylindrical page curl deformation engine. The turn pipeline supports automated 260ms tap turns, real-time interactive drag tracking with settle/snap-back thresholds, single and two-page spreads with center book spine deformation, and full LTR/RTL reading direction reversal.

A standalone logic module, `ReaderTurnLogic.swift`, centralizes gesture mathematics, threshold arbitrations, and UI formatting, completely decoupling calculation rules from UI rendering and enabling fast, automated unit and UI testing.

---

## 2. Feature Architecture & State Machine

```
  +------------------+         Tap Zone / Drag         +----------------------+
  |                  | ------------------------------> |                      |
  |   Resting State  |                                 |    Turning State     |
  |  (2D Comic Page  | <------------------------------ |  (CAMetalLayer 36x4  |
  |   or 2-Page      |     Snap-Back (< 35% Drag)      |   Cylindrical Mesh   |
  |     Spread)      |                                 |   4 Lighting Layers) |
  |                  |                                 |                      |
  +------------------+                                 +----------------------+
           ^                                                      |
           |                  Progress = 1.0                      |
           +------------------------------------------------------+
                           Commit Turn & Update Page
```

### States
1. **Resting State**:
   - Displays the current page or two-page spread with center spine shadow via SwiftUI `Image` with shared KMP `CameraTransform` framing (`fill = 0.98`).
2. **Turning State**:
   - Swaps view to interactive `PageCurlView` hosting `MetalCurlUIView` (`CAMetalLayer`).
   - Deforms a $36 \times 4$ vertex mesh along an unrolled vertical cylinder.
   - Computes 4 lighting and material passes:
     - **Drop Shadow:** Cast onto the revealed next page underneath.
     - **Crease Shadow:** Darkens inner concavity of the roll.
     - **Specular Highlight:** Sheen along the cylindrical ridge.
     - **Reverse Paper Tint:** Authentic pulp-cream tint (`#FBF0D9`) on reverse side.
3. **Completion State**:
   - Once curl progress reaches `1.0`, `page` index updates to target, incoming `DisplaySpread` is committed, and Metal overlay resets back to resting state.

---

## 3. Gesture Zones & Interaction Rules

When `zoom <= 1.05` (default reading mode), screen interaction is split into three horizontal zones:

```
+--------------------+--------------------+--------------------+
|                    |                    |                    |
|     LEFT THIRD     |    CENTER THIRD    |    RIGHT THIRD     |
|    (x < 1/3 W)     | (1/3 W <= x <= 2/3)|    (x > 2/3 W)     |
|                    |                    |                    |
|  LTR: Turn Back    |   Toggle Chrome    |  LTR: Turn Next    |
|  RTL: Turn Next    |    (HUD / Bars)    |  RTL: Turn Back    |
|                    |                    |                    |
+--------------------+--------------------+--------------------+
```

### 1. Tap Zone Turns
- **Action**: Tap Right Third (LTR) or Left Third (RTL).
- **Behavior**:
  1. Starts an automated 260ms 3D curl animation via `fastOutSlowIn` easing (cubic-bezier `0.4, 0.0, 0.2, 1.0`).
  2. The page peels back smoothly, revealing the next page underneath.
  3. Upon reaching 260ms, the top bar status text updates (e.g. from `"Page 1/24"` to `"Page 2/24"`), and the bottom page coin updates from `1` to `2`.
- **Backward Turn**: Tap Left Third in LTR (or Right Third in RTL) to turn back over 260ms.

### 2. Interactive Drag-to-Peel (Pan Gesture)
- **Initiation**: Forward turn initiates when drag displacement $|\Delta x| > 6\text{ pt}$ (leftwards $\Delta x < -6$ in LTR, rightwards $\Delta x > +6$ in RTL).
- **Tracking**: Progress tracks 1:1 with finger displacement:
  $$\text{progress} = \min\left(\max\left(\frac{\text{dragDistance}}{\text{screenWidth}}, 0.0\right), 1.0\right)$$
- **Release Thresholds**:
  - **Settle Forward (Commit Turn)**: Released when $\text{dragDistance} > 35\%$ screen width ($> 0.35$) OR release fling velocity $> 500\text{ pt/s}$ in turn direction (with opposing velocity $> -400\text{ pt/s}$). The curl animates smoothly to `1.0` and increments page.
  - **Snap-Back (Cancel Turn)**: Released at $\le 35\%$ with low velocity. The curl springs back to `0.0`, keeping the current page index unchanged.

### 3. Center Tap (Chrome Toggle)
- **Action**: Tap Middle Third ($\frac{1}{3}W \le x \le \frac{2}{3}W$).
- **Behavior**: Toggles visibility of top header bar (title, back button, page status, chips) and bottom bar (page coin and scrubber slider).

### 4. Two-Page Spread Mode
- **Action**: Toggle `SpreadChip` in top bar, or rotate device to landscape.
- **Behavior**: Two pages displayed side-by-side with vertical center spine. Right-hand page peels across center spine over onto left-hand page, deforming around book spine coordinates.

### 5. Pinch-to-Zoom & Pan
- **Action**: Pinch outward ($1.0\times - 5.0\times$).
- **Behavior**: When `zoom > 1.05`, page curl gestures are inhibited to allow viewport panning. Double-tap resets zoom back to $1.0\times$.

---

## 4. Key UI Identifiers & Accessibility Selectors

The following accessibility identifiers are wired into SwiftUI components for automated UI testing and element queries:

| UI Element | Selector / Identifier | Component / Location | Expected Value / Behavior |
|---|---|---|---|
| **Top Bar Title** | `TopBarTitle` | `Text(title)` in `topBar` | Comic archive name without extension |
| **Page Status** | `PageStatus` | `Text(pageStatus)` in `topBar` | `"Page 1/<N>"` or `"Pages 1-2/<N>"` |
| **Back Button** | `BackButton` | `Button { dismiss() }` in `topBar` | Dismisses reader back to library |
| **Page Coin** | `PageCoin` | `PageCoin` badge in `bottomBar` | Bookmark container element |
| **Page Coin Number** | `PageCoinCurrentPage` | `Text("\(page)")` in `PageCoin` | Current page number (1-indexed) |
| **Scrubber Slider** | `ComicScrubberSlider` | `ComicScrubberSlider` in `bottomBar` | Slider with range `0 ... pageCount - 1` |
| **Spread Mode Chip** | `SpreadChip` | `SpreadChip` in `topBar` | Toggles `"SINGLE"` vs `"SPREAD"` |
| **Reading Direction Chip**| `DirectionChip` | `DirectionChip` in `topBar` | Toggles `"LTR"` vs `"RTL"` |
| **Fit Mode Chip** | `FitModeChip` | `FitModeChip` in `topBar` | Toggles `"PAGE"` vs `"WIDTH"` |
| **Reader Canvas** | `ReaderCanvas` | `ReaderGestures` overlay | Touch and gesture reception layer |

---

## 5. Verification & Test Suite

The Recommended Test Suite Checklist is fully implemented in [`iosApp/Tests/ReaderTurnTests.swift`](file:///Users/bryanclark/Developer/leaf-reader/iosApp/Tests/ReaderTurnTests.swift):

```text
Test Suite 'ReaderTurnTests' passed at 2026-11-04 08:16:47.704.
	 Executed 17 tests, with 0 failures (0 unexpected) in 0.661 seconds
Test Suite 'LeafTests.xctest' passed at 2026-11-04 08:16:47.704.
	 Executed 23 tests, with 0 failures (0 unexpected) in 0.675 seconds
```

### Implemented Checklist Tests:
1. **`testTapForwardAdvancesPage`**:
   - Verifies initial status `"Page 1/24"`.
   - Simulates tap at $x = 0.85W$ (Right Third) in LTR mode $\to$ initiates forward turn.
   - Drives 260ms `fastOutSlowIn` animation and verifies completion within 300ms.
   - Asserts page updates to `"Page 2/24"` and `PageCoin` updates to `2`.
2. **`testInteractiveDragThresholdCommit`**:
   - Simulates 50% drag swipe ($x = 0.9W \to 0.4W$, $\Delta x = -200\text{ pt}$).
   - Verifies 1:1 progress tracking ($0.50$).
   - Validates $> 35\%$ threshold commit $\to$ animates to `1.0` and advances to `"Page 2/10"`.
3. **`testInteractiveDragSnapBack`**:
   - Simulates 15% drag swipe ($x = 0.9W \to 0.75W$, $\Delta x = -60\text{ pt}$).
   - Validates 1:1 progress tracking ($0.15$).
   - Validates $\le 35\%$ with low velocity triggers snap-back to `0.0` $\to$ page remains on `"Page 1/10"`.
4. **`testRTLDirectionInvertsTurnZones`**:
   - Tests `rightToLeft = true`.
   - Asserts Left Third ($x = 0.15W$) advances forward to `"Page 2/15"`.
   - Asserts Right Third ($x = 0.85W$) turns backward.
5. **Additional Regression Tests**:
   - `testCenterTapTogglesChrome`: Middle third ($\frac{1}{3}W \le x \le \frac{2}{3}W$) resolves to `nil` (toggles HUD bars).
   - `testFlingVelocityOverridesDragDistance`: Fling velocity $> 500\text{ pt/s}$ settles forward even with drag $< 35\%$.
   - `testPinchZoomDisablesPageCurl`: Zoom $> 1.05\times$ inhibits curl gestures.
   - `testSpreadModePageStatusFormatting`: Verifies single (`"Page 1/20"`) and spread (`"Pages 2-3/20"`) string formatting.

---

## 6. How to Run Tests Locally

Follow the standard iOS build chain:

```bash
# 1. Verify shared KMP tests pass
./gradlew :shared:jvmTest

# 2. Build iOS Simulator framework
./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64

# 3. Generate Xcode project
xcodegen generate --spec iosApp/project.yml --project iosApp

# 4. Install CocoaPods dependencies
(cd iosApp && pod install)

# 5. Run test suite via xcodebuild on booted simulator
xcodebuild test -workspace iosApp/Leaf.xcworkspace -scheme Leaf -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build ARCHS=arm64 \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
```

---

## 7. Modified & Created Files Inventory

| File | Status | Description |
|---|---|---|
| [`iosApp/Sources/ReaderTurnLogic.swift`](file:///Users/bryanclark/Developer/leaf-reader/iosApp/Sources/ReaderTurnLogic.swift) | **New** | Pure logic and calculation engine for tap zones, drag math, thresholds, and page status formatting. |
| [`iosApp/Sources/ReaderView.swift`](file:///Users/bryanclark/Developer/leaf-reader/iosApp/Sources/ReaderView.swift) | **Modified** | Integrated `ReaderTurnLogic`, added accessibility identifiers on all controls and chrome elements. |
| [`iosApp/Sources/LeafBrand.swift`](file:///Users/bryanclark/Developer/leaf-reader/iosApp/Sources/LeafBrand.swift) | **Modified** | Added `.accessibilityIdentifier("PageCoin")` and `"PageCoinCurrentPage"` on `PageCoin`. |
| [`iosApp/Tests/ReaderTurnTests.swift`](file:///Users/bryanclark/Developer/leaf-reader/iosApp/Tests/ReaderTurnTests.swift) | **Modified** | Implemented the 4 recommended tests plus additional gesture/state invariants. |
| [`.gitignore`](file:///Users/bryanclark/Developer/leaf-reader/.gitignore) | **Modified** | Added `docs/PAGE_FLIP_CURL_HANDOFF.md` and `*HANDOFF*.md` pattern. |
