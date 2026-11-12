# Leaf Privacy Policy

_Last updated: 6 November 2026_

Leaf reads and stores your comics on your device. Reading and local Files imports work without an internet connection.

## Data we collect

**None is collected by us.** Leaf has no telemetry or service that collects your data.

- Leaf has no telemetry, tracking, or cloud sync. Android and iOS support opt-in connections to your own SMB/Samba server. SMB connections send authentication and file requests only to the server explicitly configured for that operation. There is no automatic discovery or background sync.
- iOS SMB passwords are stored in the device-only, non-synchronizing Keychain and are accessible while the device is unlocked. Completed downloads are imported locally and remain readable without a network connection.
- Comics you import are copied into the app's private storage on your device and are readable only by Leaf.
- Reading progress, library metadata, and settings are stored locally on your device. The optional iOS Jellyfin candidate sync described below can exchange a comic’s reading position with your configured server.
- Where a build includes panel detection, it runs entirely on-device. The iOS App Store packaging excludes the model. No page images or derived data are ever uploaded anywhere.
- Leaf contains no analytics, no crash reporting, no advertising, and no third-party tracking SDKs.

## Data sharing

Leaf does not upload your library, reading activity, or settings to us. Optional SMB imports communicate with your chosen server; optional iOS tips use Apple’s purchase services as described below.

## Optional iOS tips

Leaf offers optional, repeatable one-time tips through Apple’s in-app purchase system. All app features remain free, and a tip unlocks no content or privileges. Loading tip options and making a purchase require access to Apple’s services; reading, Files imports, settings, and backups do not depend on purchases or connectivity.

Apple processes payments and purchase information under its own privacy policy. Leaf uses StoreKit on your device to verify and finish tip transactions, including purchases completed after an interruption. Leaf does not send receipts or transaction details to a developer-operated server and does not add analytics. A pending tip’s product identifier may be stored locally to show its status; it is not a record of your library or payment details. Apple retains its purchase records independently of uninstalling Leaf.

## Data deletion

Uninstalling the app deletes imported comic copies, covers, reading progress, and app settings. iOS Keychain items can survive uninstall; removing an SMB connection must explicitly delete its saved password. Use Forget Connection in SMB Drive settings to delete the saved password.

## External links

The app's menu contains a link to the project's GitHub page. Opening it hands off to your browser; the standard privacy terms of GitHub then apply.

## Children

Leaf does not collect data from anyone, including children.

## Changes

Any change to this policy will be published at this URL alongside the app's source code.

## Contact

Questions? Open an issue at <https://github.com/bclark/leaf-reader/issues>.


### iOS Jellyfin feature candidate

The iOS Jellyfin feature adds optional connections to a server you choose. Sign-in sends your username and password to that server; Leaf retains the resulting access token in the device-only, non-synchronizing Keychain, not the password. Browsing, cover requests and downloads send the token and a random installation identifier to the configured server. In the development candidate, connected startup and the main library request catalogue metadata and visible covers from that server; the explicit Jellyfin browser remains available. Browsing does not download comic archives or flush pending progress writes. Your server may keep its own access logs.

HTTPS uses normal certificate validation. Local HTTP requires an explicit choice and sends credentials and content without encryption. Disconnect removes the saved token and stops the session; downloaded comics stay in your local library. Tokens are excluded from Leaf exports and portable backups. Local server/item identifiers are stored separately to associate downloaded files with their source.

In the current development candidate, choosing **Sync reading position** on a downloaded comic explicitly reads and updates that comic’s page position and read/unread status on your configured Jellyfin account. A conflict preserves both positions until you choose one; you can keep both for now. Sync does not send the comic file, local library lists, ratings or favorites, and does not run in the background or while reading offline. Failed or unconfirmed updates remain pending for a later explicit retry. Downloaded comics remain readable offline. This candidate is separate from previously published builds; this implementation work has not uploaded or published a release or this policy.

The iOS development candidate caches lightweight catalogue metadata privately, scoped to the server and account, separately from complete downloaded comics. Remote Leaf list membership can be saved before downloading; portable backups can include server/account/item identifiers, but exclude tokens and server filesystem paths. Read downloads a complete retained copy before opening it. An unreachable server switches the library to Downloaded with an explanation and retry; offline reading remains available. No automatic eviction or discovery is added.

In the iOS development candidate, foreground catalogue refresh and search query metadata already indexed by the configured Jellyfin server. Leaf pages through lightweight metadata while the library is open and may validate folder ancestry for grouping. Refresh does not trigger a server scan or download archives. Cached metadata can include previously loaded collections and historical item/folder identities to preserve downloaded and list context. Removing a retained copy keeps unsent local reading intent; it does not send a server update.

The iOS development candidate also caches previously loaded descriptions and structured creator credits. Selecting a creator requests matching comics within the selected Jellyfin library using a person identifier or credited name. Saved detail and creator-result metadata is scoped to the account and server, excludes server filesystem paths and credentials, and can be shown offline with stale or incomplete coverage identified. This lookup does not change the server library or reading progress.

### Android Jellyfin development candidate — 11 November 2026

In connected Android development builds, Leaf opens the configured Jellyfin comic catalogue when starting a new app session. Library metadata and visible covers are requested from that server using the saved authentication token. The startup catalogue request does not download comic archives, change saved reading positions or flush pending progress updates. My Comics remains available for local reading when the server is unavailable, and local-only startup makes no Jellyfin requests.

Connected All, Series, Unread and Lists views request paginated catalogue metadata, search results, covers and, when opening collections, book ancestry from the configured server. These catalogue requests do not reconcile saved positions or flush pending progress updates. Choosing Read for a remote-only comic downloads and validates the complete archive before opening it; you can cancel. Downloaded shows readable copies already on the device across import sources. Read-triggered copies follow the existing retained-import behavior; no automatic eviction or server collection edits are added.

Android retains its existing user-configured Jellyfin reading-progress queue and browser/import behavior; reading changes and queued retries can communicate with the configured server when those features are used. The new catalogue landing screen does not add a background sync service, automatic cache eviction, telemetry or requests to a developer-operated server. This development wording does not establish a published Android release or a hosted-policy update.

Android may retain lightweight catalogue and list metadata in the app’s private database, separately from downloaded comic files. While the library is open, Leaf incrementally indexes metadata from the configured server, validates folder ancestry for Series grouping, and refreshes on request or a throttled return to the foreground. Refresh reads Jellyfin’s existing index; it does not trigger a server scan or download comic archives. Cached metadata supports offline browsing and search, with incomplete coverage labelled. Failed or partial refreshes preserve cached entries; a complete successful scoped refresh can remove stale catalogue metadata without deleting retained comics, list membership or reading history. Saved catalogue pages are scoped to the configured server and account and can be shown during connection failures; signing in as another account does not expose the previous account’s remote metadata. Adding a remote comic to a Leaf list does not download it. User-initiated portable backups can include these list references, titles and server/account/item identifiers, but not authentication tokens or server filesystem paths. Removing a downloaded copy preserves its Leaf list membership.
