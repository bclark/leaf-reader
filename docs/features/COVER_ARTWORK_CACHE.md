# Cover artwork retention

Owner requested this bounded Android fix after Fold scrolling exposed previously loaded covers returning as placeholders. This extends the existing Library artwork path; it does not change the visual composition or activate the pending Fold layout proposals.

## Reader-facing behavior

Recently viewed covers should reappear quickly when returning through the shelf. A memory hit is available on the card's first composition. Once memory evicts a thumbnail, an existing disk thumbnail can be decoded without another server fetch. New/unavailable artwork can still show the generated fallback while loading. Cached art is **not a downloaded comic**: offline availability, Read/Continue, downloads, progress and authentication rules remain independent. Preserve complete Series rear artwork and the deliberate hold-then-pull peel.

## Android implementation

- Keep the existing decoded-memory byte budget and shared in-flight cancellation policy.
- Store only bounded decoded cover thumbnails, encoded as PNG, in app-private `cacheDir/jellyfin-thumbnails`. No HTTP headers, tokens, URLs, comic titles, archives or credentials are serialized. Hashed filenames distinguish server URL, server/user IDs, item, image tag and requested width. Ephemeral process/session generation is excluded from disk identity; explicit invalidation still purges the store.
- Limit encoded thumbnails to 128 MiB total, 1,024 entries and 4 MiB per entry, evicting least recently accessed entries. Tagged entries expire after one day; untagged entries after one hour. Freshness is measured from creation, independently of access order. Android may clear this disposable cache at any time.
- Atomic writes and cleanup of abandoned staging files, decode/storage failures and corrupt/expired entries fall back to the existing source path. A cache failure must not block a cover or comic. Disk reads/encoding stay on the existing IO paths; at most two cached reads/decode operations run concurrently, queued reads are cancellable, and writes/invalidation stay exclusive; composition does a memory-only lookup with account/session/item/tag/width identity.
- Existing cache invalidations (disconnect, successful reconnect/account change, observed authentication/access denial, restore/import/removal) clear thumbnails too. Cancellation/epoch guards prevent old in-flight work republishing after clear. Do not recycle bitmaps that Compose may still display.
- App cache is excluded from library backups. Clearing cached artwork must never remove explicit downloads, Files/SMB originals, library records, progress, lists or queued sync.

## Front-first stack loading

- Front artwork for visible Series/list stacks and ordinary comic cards takes priority over queued secondary artwork. Keep full rear covers; populate them opportunistically after pending fronts finish, including when a front fails.
- Share a two-load bound across local cover decoding and remote artwork requests. Cache hits remain immediately usable; retain the existing memory and disk budgets, freshness and invalidation rules. Running loads finish normally rather than being interrupted for a new front.
- Starting the existing deliberate native hold promotes the next cover's same queued request. Moving artwork between rear/front roles retains comic-keyed image state and must not reload it or flash the outgoing cover. Reset the reveal intent when the selected front changes and demote a former front that is still queued behind current front work; gesture duration, threshold and animations stay unchanged.
- No new setting, lower-resolution rear artwork, disk-cache exclusion, readable-download state or reading-progress operation. Test load precedence, cancellation, promotion without restarting, failed-front fallback and retained peel/tap/scroll behavior. These are behavioral goals for iOS coordination, not a prescribed iOS queue implementation or active release assignment.

## Cross-platform meaning and limits

This is an Android implementation, not an iOS integration assignment or a change to iOS release scope. An iOS implementation should retain its Files/linked-source identity, native artwork loaders and existing privacy/invalidation behavior. Share the user-facing expectation and distinction between artwork and readable copies; Android's file format, budgets and synchronization are not prescribed iOS architecture.

Verify long traversal/return with constrained memory, repository recreation, first-frame memory hits, account/tag/size isolation, stale/corrupt/unwritable storage, byte limits, clear/cancellation races and unchanged Series peel. Record synthetic versus physical evidence separately. Source request-count reduction does not prove faster cold start, frame pacing or all-device performance. Real Fold acceptance remains a separately coordinated check; no store upload or release authorization follows from this document.
