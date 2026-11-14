# Android privacy reconciliation for the next candidate

Draft for Bryan’s review, prepared 13 November 2026 from isolated base `21b3fac` plus the current Android changes. The published policy and store declarations require their separately authorized publication/review steps. This document records source behavior and proposed wording.

## Verified differences and source

The hosted Android policy at https://digitalbc.net/leaf/privacy/ was retrieved during this inspection. It describes app-private SMB preferences without separate password encryption, unencrypted portable JSON exports, database-only device transfer and external support links. It does not describe the current Jellyfin development behavior. The current Play flavor disables external tips, while the F-Droid flavor enables the optional Ko-fi link through `enable_external_tips`.

Android SMB host/share/username/password are stored in app-private SharedPreferences. Portable exports whitelist reading preferences, comic metadata/progress and reading-list entries. Remote list entries can include server/account/item/library identifiers and a path-free metadata snapshot. Comic bytes, cover files, app-private file paths, SMB connection details and Jellyfin credentials are excluded from portable exports. Database-only operating-system transfer can include library file locations, remote links, cached catalogue/detail/creator metadata and pending progress state. It excludes the credential file, preferences and comic files. Actual transfer behavior needs physical device acceptance.

Jellyfin authentication uses a token encrypted with an Android Keystore key. Connections, catalogue/cover/detail/creator requests and full-archive downloads go directly to the configured server. Connected startup, open-library indexing and throttled foreground refresh can request metadata and visible covers. Browse/search requests do not reconcile reading progress or flush the outbox. Reader progress actions, existing connection/browser retry paths and explicit sync actions can exchange progress with that server. Failed writes can remain queued locally. The existing transport/identity guards and their acceptance limits are documented in Android engineering notes.

Source references include `AppSettings.kt`, `JellyfinCredentialStore.kt`, `LibraryRepository.kt`, `BackupManager.kt`, `JellyfinSnapshots.kt`, `data_extraction_rules.xml`, `MenuScreen.kt` and the flavor bool resources. Exact source/artifact evidence is retained under `docs/local/android-eng-workthrough-20261113/`.

## Proposed Android policy additions

### Optional Jellyfin connections

Leaf can connect to a Jellyfin server you choose. Sign-in sends your username and password to that server. Leaf stores the resulting access token encrypted on your device using Android Keystore protection. Later requests use that token and an installation identifier. Your server may keep its own access logs.

When configured, Leaf can load catalogue metadata and visible covers at startup, index metadata while Library is open, and refresh on request or a throttled return to the foreground. Searches, collection membership checks, details and creator browsing contact the configured server. Refresh reads Jellyfin’s existing catalogue. Reading a remote-only comic downloads and validates a complete copy before opening it. Retained copies remain available through Offline.

Reading-progress features exchange page position and read/unread status with the configured Jellyfin account. Reading actions and existing connection/browser retry paths can send queued progress updates. Failed or unconfirmed updates can remain on the device for retry. Catalogue browsing alone does not send progress updates. No comic file, Leaf reading list, rating or favorite is uploaded by progress sync.

HTTPS uses normal certificate validation. Choosing local HTTP permits unencrypted traffic to the configured server. Disconnect removes the saved authentication token and stops the session. Downloaded comic copies remain local until removed.

### Cached metadata and portable exports

Leaf can store lightweight catalogue, collection, detail and creator metadata privately on your device, scoped to the configured server and account. Cached browsing may be incomplete or stale, and the app identifies that coverage. Metadata and covers alone do not make a comic available for offline reading.

An unencrypted portable backup can contain reading lists, comic titles and reading progress, plus server/account/item/library identifiers and saved metadata for remote list entries. It excludes authentication tokens, SMB connection details, comic files, covers and internal file paths. Review an export before sharing it and store it securely.

Android’s database-only device transfer can include local file locations, library/progress/list records, remote identifiers, cached metadata and pending progress state. It excludes the separate Jellyfin credential file, SMB preferences and comic files. Availability and behavior depend on Android and the device.

### Flavor-specific optional support

The Google Play flavor omits external tip links. Builds that enable optional Ko-fi support open it in your browser or another app. The external service’s privacy policy applies. Leaf attaches no comic files, reading history or server credentials to that link.

## Review and release gates

Bryan must approve wording that matches the selected release scope before hosted-policy changes or Console declarations. Recheck the final artifact and backup/transfer behavior. Preserve separate historical release evidence and all open privacy, native dependency, upgrade and device gates. Progress conflict and Read/Continue retention decisions remain recorded under Q-019/Q-020.

## Android transport implementation evidence · 2026-11-13

The development candidate now checks the exact DNS results used for direct cleartext Jellyfin connections, rejecting public, mixed-local/public, unspecified and multicast destinations before connection. Existing explicit local-HTTP consent and hostname rules remain. Private/link-local/loopback addresses and IPv6 unique-local DNS answers are recognized; this does not broaden the shared URL-host allowlist. Cleartext bypasses a system/custom HTTP proxy so it cannot route credentials around the destination check; HTTPS retains normal resolver/proxy and certificate behavior. Redirect hops select the appropriate transport, reject POST redirects and HTTPS downgrade, enforce a three-hop limit and preserve stripped credentials across an origin-changing chain. No new endpoint, cache persistence, telemetry or account behavior.

Android's base network-security XML still permits cleartext globally. Its explanatory comment now states that the Jellyfin application layer enforces these checks; the XML alone does not enforce LAN-only traffic or consent. This is not an OS/VPN/routing guarantee or a claim of native iOS parity. Fifteen new hermetic transport cases and both flavor suites pass (243passed/one expected SMB-live skip each); HTTPS downgrade uses a synthetic response and the real localhost HTTPS-proxy case rejects CONNECT, rather than establishing TLS-handshake acceptance. [Frozen Android evidence](local/android-eng-workthrough-20261113/transport/verification.json). Exact committed native checks and final hosted-policy/Console review remain separate; nothing published.
