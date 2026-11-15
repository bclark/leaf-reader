# Leaf design system

Version 0.2 · Consolidated 2026-11-08 · Android, iOS and website

## Authority and status

Shared design reference; platform action/evidence lives in [Android notes](ANDROID_ENG_NOTES.md) and [iOS notes](IOS_ENG_NOTES.md). Those local Git-ignored links may not resolve in a public clone. [RELEASE_COMPLIANCE.md](../RELEASE_COMPLIANCE.md) remains the final-candidate evidence record. This document is not release clearance.

**Observed** = November 7 audit finding, not fresh verification of every current build. **Proposed** = recommendation, not owner approval. **Acceptance** = required evidence to close a gap. Existing native implementation is the starting point. No blanket redesign, account change, publication or submission is authorized. Resolve conflicts with repository instructions explicitly; AGENTS.md's older crimson guidance is a known discrepancy, not a reason to recolor the app.

Sources: Leaf_Design_System.docx v0.1 (`libfile_13c13c304bc8819190658fa7480dd9f7`) and Leaf_Design_Gap_Register.docx v0.1 (`libfile_e16241b0090081919e11059e7c720781`), November 7, 2026. This consolidates their guidance/gap IDs, not a new visual/code audit. Original platform handoffs and qualifications remain in local archives.

## Principles

- Cover-first libraries; quiet reader with unobscured artwork.
- Forest/sage/gold identity; restrained comic accents in organization/navigation, never behind errors or small text.
- Share meaning and behavior, not identical pixels. Retain Material 3, SwiftUI and web-native conventions.
- Respect system/light/dark and supported AMOLED modes; app selection and system appearance are separate test variables.
- Accessibility, localized expansion, recoverable errors and offline continuity are core behavior.
- UI language must not override reading direction or page-turn preferences.

## Color roles

Observed app primitives: forest `#1C3A27`, deep forest `#122519`, sage `#4A7C59`, gold `#D4AF37`, rose `#935B6D`, paper `#F9F9FB`, ink `#14191C`, raised ink `#1E2529`.

Proposed vocabulary: canvas, surface, text.primary, text.secondary, brand.surface/onBrand, accent.surface/onAccent, accent.text, border, focus, status.error. Use native adapters; no token compiler. Legacy Crimson/crimson/ochre aliases do not define semantic meaning. Forest is not an error role.

Observed iOS light/dark: primary text `#14191C / #F9F9FB`; secondary `#4A544C / #A0A2A3`; canvas `#FAFAF7 / #14191C`; raised `#EAEDE5 / #1E2529`; accent text `#795C00 / #D4AF37`. AMOLED black/`#101010`; Android raised AMOLED `#0D1113` is a native variation, not automatically a defect.

Proposed pairings: paper on forest, ink on gold, dark accent text on light surfaces. Do not use gold-on-paper body text. Measure rendered alpha and surroundings: target 4.5:1 normal text, 3:1 large text and meaningful controls/boundaries. Errors need non-color cues. A semantic-role source fix is not rendered contrast evidence.

## Type, spacing and motion

Observed native fonts: Anton for short display headings, Archivo for UI. Preserve variable-font weights; use native semantic styles and relative scaling. Sentence case for actions/errors; uppercase wrappers must not impair localization or accessible names. Never solve clipping by shrinking labels.

Proposed iOS starting sizes: 17pt body/action, 13–15pt supporting text, subject to actual scaling/layout. Android uses scalable sp/native styles. Website Inter remains appropriate for the DigitalBC shell/prose; Anton Leaf headings are proposed, not approved.

Proposed spacing roles: 4/8/12/16/24/32 native units, adopted incrementally. Retain existing small radii/restrained hard shadows where useful; no mandatory cross-platform shadow/radius framework. Proposed motion: feedback 120–180ms, navigation 200–260ms, optional decoration. Respect platform reduced-motion settings separately from reader motion preferences.

## Component/state contracts

Small native components where repetition warrants: header, icon action, primary/secondary/destructive button, filter/segmented choice, cover card/stack, metadata/settings row, progress badge, connection form, transfer status, empty/error state, confirmation sheet.

Document default/pressed/selected/disabled/loading/error/focus where applicable. Keep action names while loading and prevent duplicate submission. Distinguish empty from offline/denied/auth-expired. Give recoverable error text. Show known bytes or indeterminate progress; cancellation remains reachable during download/validation. Validate Files, SMB and Jellyfin separately even when controls look alike.

Preserve local content/progress on recoverable failure. Explain consequences of disconnect, delete, replacement and sync changes. iOS StoreKit tips and Android distribution-specific support remain distinct; visual parity does not establish policy parity.

## Accessibility and localization

- Actual hit/accessibility bounds: at least 48dp Android, 44pt iOS; aim for 44px web (existing website buttons 48px minimum). Visible icon size alone is not proof.
- Name, role, value and selected state; hide decoration/duplicate logo announcements; discoverable context actions; restore focus after dismissal.
- Spoken TalkBack/VoiceOver, keyboard/switch paths and visible focus require task evidence, not only source semantics.
- Test narrow/large-text/multiline, landscape/tablet/Fold transitions; preserve selection and reading position.
- Confirmed validation pair: US/English and Mexico/Spanish. Localized Apple prices do not prove app localization. Fluent review remains pending; Bryan has a reviewer.
- Android resources and iOS catalogs/native matching; system per-app language is implemented, with owner approval recorded separately in iOS notes. Do not advertise reviewed Spanish prematurely.

## Website adapter

Observed DigitalBC: background `#0B0F12`, surface `#12181B`, text `#E9E6DC`, secondary `#B7BCB9`, muted `#9AA49E`, accent `#C0AD83`, Leaf override `#D4DCC3`, border `#303936`, focus `#E5C991`; Inter. Max width 1280px; gutter `clamp(24px,5vw,80px)`; buttons minimum48px/radius5px, nav46px; hover200ms/entrance750ms; breakpoints1050/800/540px. Privacy prose roughly760px/16px/1.8 line height.

Inventory, not a mandate for native apps. Proposed: retain quiet editorial DigitalBC shell and dark-only website initially; selectively add Leaf identity. Typography/accent convergence/light-mode expansion require explicit design decisions. Verify reduced motion, keyboard focus and responsive reflow. No publication authorized here.

## Assets and truthful content

Canonical observed leaf/page-curl icon: `docs/leaf-icon.png`; retain native light/dark variants. `docs/logo-lockup.png` is upstream CHIKA artwork, not approved Leaf artwork; preserve attribution. Register asset path/purpose/source/license/approval/export sizes. Secondary starbursts outside repo need provenance. Proposed small-mark checks16/24/32/48px and quarter-icon clear space are review proposals, not frozen rules or new assets.

Rights-cleared screenshots/covers; label synthetic QA art. Old captures are not final-build acceptance. Reconcile privacy/support/store copy with exact platform/flavor, SMB credentials, unencrypted exports/transfer, optional tips and Jellyfin requests. Audit's Android Ko-fi discrepancy needs rechecking against shipped flavor. Do not claim all reading data stays local when remote progress is enabled; do not advertise EPUB, reviewed Spanish, reliable two-way sync or public-store availability without evidence. Draft/commit/internal build does not mean published.

## Shared gap register

Stable IDs; platform notes hold current implementation status/evidence. Preserve reported improvements and distinguish narrower implementation from complete acceptance.

| ID | Priority | Acceptance work | Owner |
|---|---|---|---|
| DS01 | P0 affected build | Android Jellyfin semantic error fix reported; rendered theme/large-text contrast remains | Android |
| DS02 | P0 affected build | Compact iOS controls; targeted fixes reported, broader matrix remains | iOS |
| DS03 | P0 public content | Exact behavior/privacy/support/claims; separately approved publication | Both/web |
| DS04 | P1 | Reconcile stale guidance, particularly AGENTS crimson text | Shared |
| DS05 | P1 | Scalable typography without clipping/tiny labels | All |
| DS06 | P1 | Actual targets, spoken semantics, focus | All |
| DS07 | P1 | String resources, expansion, fluent Spanish review | Both |
| DS08 | P1 | Small component state/recovery contracts | All |
| DS09 | P1 | Asset provenance, variants, export register | Shared |
| DS10 | P1 | Decide intentional web/native type and appearance differences | Web/Bryan |
| DS11 | P2 | Incremental spacing/motion and reduced-motion behavior | All |
| DS12 | P1 | Exact-build reference gallery across states/themes/sizes/locales | All |
| DS13 | P2 | Focused deterministic regression for risky states | All |

## Adoption and upkeep

Fix concrete legibility/errors, then high-value accessibility/state gaps. No blanket retokenization. Platform notes contain scoped action items/links/evidence; edit shared rules here instead of copying palettes into each file. Record observed/proposed/approved status, rationale/date/platform exceptions. Native differences can be intentional.

Completion evidence: source/build including dirty manifest, device/OS/window, theme, text size, locale, tests/result and evidence link; artwork rights for captures. BLOCKED is not PASS. Archive resolved discussion with stable IDs, never the active persistent release checklist. Keep local coordination notes/archives Git-ignored. CI stays disabled unless Bryan changes direction; local focused tests remain possible.

2026-11-08 change: consolidated existing system and gap register at Bryan's request. No new design approval, code/device test or publication implied.
