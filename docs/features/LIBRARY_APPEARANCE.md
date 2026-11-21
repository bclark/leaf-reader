# Shared Library appearance contract

Status: Bryan approved this direction on 21 November 2026 and explicitly requested an iOS handoff. Android implementation is committed and isolated native validation passed from main d4c5a5b; physical acceptance remains open. iOS is not implemented or accepted by this document. Keep this separate from the active iOS 1.2 Files candidate and the private Guided View prototype.

## Agreed behavior

Keep Leaf calm, tactile and cover-led, with existing lettering, palette and Series peel. Replace repeated source/read-state captions with a quieter default. Provide three independent persisted Library appearance controls:

| Control | Default | Effect |
|---|---|---|
| Show titles | On | Show comic/Series/list names beneath grid covers; off removes that caption and its spacing. |
| Show source and file details | Off | Optional compact source, page-count/file-size or list-count caption. Reading-status wording does not return through this toggle. |
| Show reading indicators | On | Compact completed checkmark and in-progress strip/marker on covers; off hides visual reading indicators only. |

Apply the defaults to existing and new installations when keys are absent. Choices survive restart and apply consistently to Library, Series, reading-list and creator-result grids. Do not apply them to Continue Reading, screen headings, details, Reader or mandatory errors. Generated covers retain identifying lettering when artwork is missing. Series/list position/count badges retain their navigation meaning and the peel gesture is unchanged.

## State and accessibility rules

- Preserve existing authoritative completion, progress, unread and unknown-state policies. Preferences only change presentation; never write progress, mark read, filter, sync or alter source identity.
- A completed marker requires known completed state. Unknown Jellyfin state must not become unread or completed.
- Show a determinate strip only when a genuine fraction is available. For known in-progress remote items without a denominator, use a small in-progress marker with an accessible card state; never fabricate a percentage.
- Keep download/local-availability indicators and actionable unavailable/error messages independent of the reading-indicator toggle. Cached cover/catalogue metadata is not a readable offline copy. Preserve explicit retry/download/removal semantics.
- Hidden visual captions remain available in accessibility names and comic details. Avoid duplicate title announcements; decorative artwork/icons are silent when the card names them. Keep selected states and nested removal/context actions accessible.
- Controls use native switches with full-row activation, named on/off state, at least 48dp Android / 44pt iOS targets, theme contrast and wrapping at large type. No device accessibility/language setting changes are authorized for QA.

## Platform handoff

Android: native appearance controls in Settings and a discoverable Library overflow entry; one shared control implementation. Persist three non-sensitive preferences; include optional backward-compatible backup fields with the same defaults for older backups. Android’s restore-recovery journal embeds these fields, so database 9→10 adds defaulted columns while retaining any pending restore. Source labels retain Device/SMB/Jellyfin identity. Details, availability and reading state remain separate values rather than parsing a concatenated caption.

iOS: retain existing Files/linked source naming rather than replacing it with Android’s Device label. Use the existing native Library/settings organization (a menu or sheet and native toggles), not Android tab/header geometry. Use the same defaults, scopes and state distinctions. Linked Files retain their own availability identity and must not be relabeled as downloaded copies. Decide integration against the exact approved iOS baseline; do not fold this into 1.2 or alter linked Files behavior without owner scope. If backed-up settings are supported, map fields explicitly; do not change authentication/provider permissions or claim cross-platform backup parity from matching labels.

## Acceptance and limits

Test all eight combinations, persistence/relaunch, older-backup defaults and restore, local/SMB/Jellyfin known-read/in-progress/unread/unknown, downloaded vs remote-only, missing art, long names, empty/error states and edit/removal actions. Verify light/dark and default/large text on compact/expanded native sizes, retained logical grid position and Series peel. Record exact source/artifact and unrun cases. Spoken TalkBack/VoiceOver remains a separate gate, not established by semantics inspection.

New copy requires locale review. Existing French preparation stays separate; these labels must be added to the future French review packet before activation. No publication, store, account, build-slot exception or device-settings authorization comes from this handoff. Update Android and iOS engineering notes when implementation/evidence changes; preserve release checklist history.

## Android implementation record

Branch `codex/android/library-appearance-20261121`, based on `d4c5a5b`: Settings and Library overflow use the same native switches, backed by a single observable preference value. Grid cards keep identity and reading state on their actionable accessibility node when captions are hidden; decorative artwork and repeated visual titles are silent. Completed and in-progress indicators are independent of remote download badges. Valid server percentages are used when supplied; ticks alone never become a percentage.

Backup exports use optional `libraryShowTitles`, `libraryShowDetails` and `libraryShowReadingIndicators` booleans. Absent fields use true/false/true. The recovery journal migration is database 9→10; an old pending restore receives those defaults and retains all prior settings. Existing database upgrades and non-default restore/recovery require regression coverage. iOS should use equivalent native preferences and explicitly map any backup support; the Android database migration is not an iOS schema instruction.

Implementation validation and any remaining acceptance gates are recorded in the Android engineering notes and the feature PR. Native semantics inspection does not establish spoken screen-reader acceptance. No regular-device install, merge, build version bump or distribution is part of this feature change.

### Android verification — 21 November 2026

Feature code `8dcf77083ca39ce19cd3f59ff250875d3af2fda0`: 144 shared JVM tests passed; Full and F-Droid each passed 323 tests with one expected live-SMB skip. Both debug lints have zero errors and 87 existing warnings. Recovery tests include non-default appearance choices, failed preference commit/restart, and a populated version-9 pending journal upgraded and reopened at version 10.

Minified synthetic QA APK SHA-256 `662f7183b7f7710066e25b60fd38bf9cc4e66cbc1f486501bd62728d19424f3a` passed on a disposable read-only API-37 emulator. Actual controls exercised all eight combinations and force-stop/relaunch persistence. Native checks covered independent download descriptions/badges with captions/reading indicators off, hidden Series identity, compact/expanded Library, English and large-type dark Spanish controls, the scrollable 2x sheet and reachable Done, plus the real Settings entry. UIAutomator exposes Compose's synthetic content-description/role children and omits stateDescription; this is not proof of spoken TalkBack behavior. Detailed logs/captures remain in the local task evidence directory.

Remaining: physical visual/peel acceptance, hidden-caption navigation/removal checks across Series/list/creator routes, and separately authorized spoken screen-reader checks. Fresh real-account writes, regular app install, merge, French activation and release were not performed. This feature is ready for review, not a release-acceptance claim.

### Scroll versus peel follow-up

Bryan accepted the Fold appearance controls but reported accidental issue peeling when captions were hidden: the cover previously claimed any downward drag after touch slop, leaving few places to start scrolling. Android’s bounded fix makes ordinary swipes over covers scroll the shelf in either direction; a native platform press-and-hold arms the existing downward peel. Retain the same peel animation, thresholds, issue count badge, tap-to-open and state retention. Use the platform long-press timing rather than a new hardcoded delay. Apply the common handler to Series and reading-list stacks, with no appearance-dependent gesture mode, extra setting or blank caption spacer.

This is the engineering response to the physical report, pending native regression and owner recheck. iOS should carry the same scroll-first intent through its native gesture arbitration, preserve its source/linked-item rules, and choose its approved integration baseline. It is not authorization to change the active iOS release. Verify quick up/down drags do not change the selected cover, held downward drags still peel, incomplete peels return cleanly, and taps still open the stack.
