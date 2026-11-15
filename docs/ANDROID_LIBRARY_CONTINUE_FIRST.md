# Android Continue-first Library

Implementation branch: `codex/android-library-continue-first-20261114`, based on
`2fe9c1bffd1adc19e6d463a76d8959cbea61336f`. This is an isolated implementation,
not the installed or signed release candidate.

Design authority: `leaf-reader-design@21f960d2e14aeb3ed1f9e70fd97b4919b698f0f8`,
`docs/LIBRARY-CONTINUE-FIRST-2026-11-14.txt`, revision 5, SHA-256
`5c1bc72d1b3e97cc253f6873ecb625c3fd4e6d637c98370b4ca97239a21686dd`.
Android phone dark/light and empty exports and the wide reference were inspected.
Android retains its text tabs below the app bar at all widths.

## Behavior

- Preserve Series / Lists / All / Offline ordering, cold Series default, explicit
  Offline/unreachable fallback, session filter state, scroll restoration and Series peel.
- Continue uses unfiltered readable local comics and the current account's catalogue.
  Started, unfinished copies are ordered by last opened on Default, outside composition work. A linked local copy owns its
  progress, including pending offline changes and completion, instead of duplicating
  or replacing it with server state.
- Remote-only candidates require a reachable, authenticated catalogue, known unfinished
  state, saved page and a valid server last-played date. Offline and unreachable
  catalogue views use local copies only. Connected tab changes may browse the saved index
  without changing reachability. Book metadata queries do not download archives
  or write reading progress.
- Phone windows show one card; windows of at least 600 dp show up to five cards in a
  horizontal row. The breakpoint is an engineering choice, pending broader device QA.
  Existing grid-column preferences are not rewritten.
- Cards scroll with the grid in all four views and disappear during search or selection.
  Local Continue forces archive validation before opening; unreadable copies are omitted
  without deleting history. Remote Continue uses the existing session-fenced retrieve/read
  path, with inline cancellation and failure recovery. Missing remote items are omitted;
  transient failures leave the candidate available. Leaving Library or opening search/selection
  retires the read intent while preserving the requested copy and existing import cancellation.
- Local page counts/progress match the grid. Jellyfin's existing book API does not supply
  a reliable total page count here: remote-only cards show `Page X` and a server percentage
  when supplied. No page totals or recency timestamps are inferred. Older private metadata
  snapshots remain readable; undated server candidates wait for refreshed metadata.
- One named button target per idle Continue card; large text stacks cover above text.
  The action target is at least 48 dp. Covers use existing sized/authenticated loaders
  and the existing generated placeholder without blocking the grid.
- A reserved bottom band contains Add comics or Create Reading List. Import activity keeps
  the label and displays progress while rejecting a second start. Selection and the dedicated
  empty state hide the band. The Load Comic tile is removed; the Create List tile remains.
- Menu opens a native dropdown with Select comics and Settings and more. Back/outside dismiss
  it; dismissal returns focus to Menu. Existing selection actions and Done remain.
- The empty Library has the approved copy, Add comics and Connect Jellyfin. Add uses the existing
  source chooser. Connect opens the existing settings form at its Jellyfin section, preserving
  Back navigation and all existing connection/HTTP confirmation behavior. No connection or
  authentication occurs merely by opening this section. Separate first-launch success/skip
  policies are unchanged.
- Unread uses the existing shared toggle/chip across views and detail screens; the selected
  chip is gold/ink. Material 3 tabs retain native interaction with semantic accent text and
  a 3 dp indicator. English and Mexican Spanish strings are supplied for new controls.

## Evidence and limits

Local detailed evidence is retained under `docs/local/android-library-continue-first-20261114/`
inside the Android bootstrap workspace, including validation logs, input/artifact hashes,
synthetic UI captures and failed attempts. These local paths are not public release artifacts.

Final source validation 5 passed 144 shared JVM tests and 260 tests per Android flavor,
plus one expected unconfigured SMB live-test skip per flavor. Both debug APKs and the separate
synthetic QA APK built. Lint reported zero errors/fatal findings and the existing 87 warnings
in each flavor. Some unchanged tasks reused exact-input Gradle results; this was not a forced
fresh run of every task. Exact input and artifact hashes are in `validation-5-results.json`.

Native synthetic checks on core commit `2014371` confirmed direct local resume at page 3/10,
return to Library, Continue in all four views, search/selection hiding, Unread persistence, menu
selection/Done, corrected empty Connect setup/Back, logical 600/840 dp widths, five-card horizontal
scrolling and the fifth card opening its own comic at page 3/10. Phone light/dark/AMOLED and Spanish
light plus 200% Spanish card/empty states were captured and visually inspected.

AMOLED inspection found the title inheriting the muted surface-variant text role. The only
subsequent runtime change explicitly uses `onSurface` for that title. Both debug APKs and the QA
APK were rebuilt, lint remained at zero errors/fatal and 87 warnings each, and targeted AMOLED/light
captures confirmed the correction. Native menu Back dismissal, normal Settings and more entry and
Back return also passed on that rebuilt APK. Test/logic files remain byte-identical to the core
commit; the full unit suite was not repeated for a one-line appearance correction. Exact delta,
input/APK hashes and captured assertions are in the local `final-results.json` and
`final-core-native-results.json` receipts. Failed attempts remain preserved separately.

Outstanding acceptance includes real TalkBack/keyboard behavior, landscape and physical tablet/Fold
geometry, physical S25/upgrade and real Jellyfin retrieval/cancellation/session-change scenarios on
this source. A logical-width fixture is not physical tablet/Fold acceptance. No owner app, real
account or signed candidate was modified during synthetic QA. No performance acceptance is inferred
from these UI checks; release-like traces remain separate. Shared constructor compatibility is
retained; combined iOS integration still requires its own exact-source validation.

KEEP BUILDING. The broad [Leaf Delivery card](https://github.com/users/bclark/projects/1)
remains In Progress until its acceptance gates are evidenced. No unit-test result implies Done,
merge, Play eligibility, publication, artwork permission, signing recovery or release acceptance.
See the append-only local Android engineering notes and existing release checklist for those gates.
