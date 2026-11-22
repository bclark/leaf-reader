# Leaf Privacy Policies

_Last updated: 9 November 2026_

Leaf has platform-specific privacy policies. Choose the policy for the app you use:

- **Android:** https://digitalbc.net/leaf/privacy/
- **iPhone and iPad:** https://digitalbc.net/leaf/privacy/ios/

The iOS policy is reproduced below so the privacy link in existing iOS builds remains useful. Android behavior is described separately at the Android URL above; iOS statements below do not describe Android.

---

# Leaf Privacy Policy for iOS

_Last updated: 9 November 2026_

Leaf is a comic and manga reader developed by Bryan Clark under the DigitalBC name. This policy describes Leaf for iPhone and iPad, including optional SMB and Jellyfin imports and optional tips through Apple.

## Your library stays under your control

Leaf does not require a Leaf account. Imported comic copies, cover thumbnails, library metadata, reading lists, reading progress and preferences are stored on your device. Leaf does not operate a service that receives your comic library or reading history. The reviewed iOS app contains no advertising, analytics or integrated automatic crash-reporting SDK.

When you import a file, Leaf reads the file you select and stores an app-managed copy for offline reading. If you choose a cloud storage provider in the Files picker, that provider may transfer the selected file under its own policies.

## Optional SMB network shares

If you configure an SMB connection, Leaf connects directly to the server you choose to authenticate, browse folders and import comics. The server receives the connection and the authentication and file requests needed for those actions. Its operator may keep logs. The requests do not pass through a DigitalBC server.

Leaf stores connection settings locally and saves SMB passwords in the iOS Keychain with device-only, non-synchronizing protection, accessible while the device is unlocked. Transport protection depends on your server and SMB configuration; Leaf does not require encryption for every SMB connection. Use servers and networks you trust.

## Optional Jellyfin servers

Jellyfin is optional and requires your own account on a server you choose. Signing in sends your username and password to that server. Leaf stores the returned access token, not your Jellyfin password, in device-only, non-synchronizing Keychain storage. Server connection details and a random installation identifier are stored locally.

Browsing libraries, requesting covers and downloading comics sends authenticated requests and the installation identifier to your configured Jellyfin server. The server operator may record access logs. Leaf does not provide or operate a Jellyfin server, and these requests do not pass through DigitalBC.

HTTPS connections use normal certificate validation. If you explicitly allow local HTTP, credentials and content can be transmitted without encryption. This iOS version imports comics for local reading; it does not send reading progress back to Jellyfin. Leaf retains local source identifiers to associate downloaded comics with their server items.

## Exports and device backups

Leaf's export feature creates an unencrypted JSON file containing library metadata, reading progress, reading lists, relevant timestamps and selected preferences. It does not include comic files, cover images, saved SMB passwords or Jellyfin tokens. You choose the export destination. A backup can reveal your reading choices, so store it securely and review it before sharing it.

Restore reads the backup you select and applies its data locally. Apple device backup or transfer features may separately include app data according to your settings and Apple's behavior. Leaf's device-only credentials are not configured to synchronize through iCloud Keychain. Leaf has no DigitalBC-hosted library backup service.

## Optional tips and Apple services

Tips are optional, repeatable one-time purchases processed by Apple. They unlock no features or content. Apple handles payment information under its own policies; you do not enter payment-card details in Leaf.

Leaf uses StoreKit on your device to load products and verify and finish transactions, including transactions completed after an interruption. A pending tip's product identifier may be stored locally to show its status. Leaf does not send receipts or transaction details to a DigitalBC-operated server.

Apple may provide transaction, sales and financial reports to the developer through its App Store services. Apple also processes App Store, TestFlight and optional diagnostic information under its own policies and your settings. Uninstalling Leaf does not remove records held by Apple. See https://www.apple.com/legal/privacy/.

## Diagnostics, links and support

Leaf may write errors to local device logs. Such logs can include file paths or error messages from a file provider. Leaf does not automatically upload those logs to DigitalBC. If you choose to share diagnostics or TestFlight feedback through Apple, Apple may make that information available to the developer.

Leaf's menu links to external project, license and privacy pages. Opening a link sends you to your browser or another app, where the destination's privacy practices apply. Leaf does not attach your comics, server credentials or reading history to those links.

If you contact Bryan Clark / DigitalBC at bclark@digitalbc.net, we receive the contact details, message and attachments you choose to send. We use that correspondence to respond and troubleshoot, and retain it as needed for those purposes and applicable obligations. Please do not send passwords, private server credentials, or comic files you lack permission to share. You may contact us to request deletion of information we hold; legal recordkeeping obligations may limit immediate deletion.

## Removing your data

You can remove imported comics and library entries in Leaf. This does not delete the original files or copies on your servers. Use Forget Connection for SMB and Disconnect for Jellyfin to remove saved credentials before uninstalling. Removing a server connection does not remove comics already imported into your library.

Deleting the app removes its local app data, but iOS Keychain items may survive deletion. Offloading an app is not the same as deleting its data. Original files, exported backups, Apple/device backups, server logs and external-service records must be managed separately.

## Changes and contact

We will publish updates to this policy with a revised date when Leaf's practices change.

Privacy contact: Bryan Clark / DigitalBC — bclark@digitalbc.net.

---

## Development candidate disclosures

The following sections record local development candidates pending final review and separately authorized publication.

### iOS Jellyfin feature candidate

The iOS Jellyfin feature adds optional connections to a server you choose. Sign-in sends your username and password to that server; Leaf retains the resulting access token in the device-only, non-synchronizing Keychain, not the password. Browsing, cover requests and downloads send the token and a random installation identifier to the configured server. In the development candidate, connected startup and the main library request catalogue metadata and visible covers from that server; the explicit Jellyfin browser remains available. Browsing does not download comic archives or flush pending progress writes. Your server may keep its own access logs.

HTTPS uses normal certificate validation. Local HTTP requires an explicit choice and sends credentials and content without encryption. Disconnect removes the saved token and stops the session; downloaded comics stay in your local library. Tokens are excluded from Leaf exports and portable backups. Local server/item identifiers are stored separately to associate downloaded files with their source.

In the current development candidate, choosing **Sync reading position** on a downloaded comic explicitly reads and updates that comic’s page position and read/unread status on your configured Jellyfin account. A conflict preserves both positions until you choose one; you can keep both for now. Sync does not send the comic file, local library lists, ratings or favorites, and does not run in the background or while reading offline. Failed or unconfirmed updates remain pending for a later explicit retry. Downloaded comics remain readable offline. This candidate is separate from previously published builds; this implementation work has not uploaded or published a release or this policy.

The iOS development candidate caches lightweight catalogue metadata privately, scoped to the server and account, separately from complete downloaded comics. Remote Leaf list membership can be saved before downloading; portable backups can include server/account/item identifiers, but exclude tokens and server filesystem paths. Read downloads a complete retained copy before opening it. An unreachable server switches the library to Downloaded with an explanation and retry; offline reading remains available. No automatic eviction or discovery is added.

In the iOS development candidate, foreground catalogue refresh and search query metadata already indexed by the configured Jellyfin server. Leaf pages through lightweight metadata while the library is open and may validate folder ancestry for grouping. Refresh does not trigger a server scan or download archives. Cached metadata can include previously loaded collections and historical item/folder identities to preserve downloaded and list context. Removing a retained copy keeps unsent local reading intent; it does not send a server update.

The iOS development candidate also caches previously loaded descriptions and structured creator credits. Selecting a creator requests matching comics within the selected Jellyfin library using a person identifier or credited name. Saved detail and creator-result metadata is scoped to the account and server, excludes server filesystem paths and credentials, and can be shown offline with stale or incomplete coverage identified. This lookup does not change the server library or reading progress.

### Android Jellyfin development candidate — 13 November 2026

In connected Android development builds, Leaf opens the configured Jellyfin comic catalogue when starting a new app session. Library metadata and visible covers are requested from that server using the saved authentication token. The startup catalogue request does not download comic archives, change saved reading positions or flush pending progress updates. Offline remains available for local reading when the server is unavailable, and local-only startup makes no Jellyfin requests.

Connected All, Series and Lists views, with an independent Unread only filter, request paginated catalogue metadata, search results, covers and, when opening collections, book ancestry from the configured server. These catalogue requests do not reconcile saved positions or flush pending progress updates. Choosing Read for a remote-only comic downloads and validates the complete archive before opening it; you can cancel. Offline shows validated readable copies already on the device across import sources. Read-triggered copies follow the existing retained-import behavior; no automatic eviction or server collection edits are added.

Android retains its existing user-configured Jellyfin reading-progress queue and browser/import behavior; reading changes and queued retries can communicate with the configured server when those features are used. The new catalogue landing screen does not add a background sync service, automatic cache eviction, telemetry or requests to a developer-operated server. This development wording does not establish a published Android release or a hosted-policy update.

Android may retain lightweight catalogue and list metadata in the app’s private database, separately from downloaded comic files. While the library is open, Leaf incrementally indexes metadata from the configured server, validates folder ancestry for Series grouping, and refreshes on request or a throttled return to the foreground. Refresh reads Jellyfin’s existing index; it does not trigger a server scan or download comic archives. Cached metadata supports offline browsing and search, with incomplete coverage labelled. Failed or partial refreshes preserve cached entries; a complete successful scoped refresh can remove stale catalogue metadata without deleting retained comics, list membership or reading history. Saved catalogue pages are scoped to the configured server and account and can be shown during connection failures; signing in as another account does not expose the previous account’s remote metadata. Adding a remote comic to a Leaf list does not download it. User-initiated portable backups can include these list references, titles and server/account/item identifiers, but not authentication tokens or server filesystem paths. Removing a downloaded copy preserves its Leaf list membership.

Android also caches previously loaded descriptions and structured creator credits. Selecting a creator requests matching comics within the selected library using a person identifier or credited name. These account/server-scoped metadata requests do not change the server library or send reading progress. Saved metadata can be shown with stale or incomplete coverage identified. The Google Play flavor omits external tip links; the F-Droid flavor can open optional Ko-fi support externally.

The Android Library now offers All, Series, Lists and Offline, with one Unread only filter that carries across views. Offline availability requires a validated complete local copy. Cached metadata, cover images and incomplete transfers do not qualify. This development wording awaits review with the selected final release scope and separately authorized hosted-policy/Console updates.


Android development note (22 November 2026, pending candidate): previously viewed Jellyfin artwork can also be retained as bounded thumbnails in app-private cache storage. This cache contains no credentials or request headers, is excluded from library exports, and is cleared by disconnect/account invalidation. Cached artwork does not make a remote comic available offline. See `docs/features/COVER_ARTWORK_CACHE.md` for retention limits and verification scope. This note does not publish a hosted policy or change the iOS policy above.
