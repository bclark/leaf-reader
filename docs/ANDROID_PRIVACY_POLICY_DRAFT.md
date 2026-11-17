# Android privacy policy draft for 0.3.8

Prepared 17 November 2026 against source `6455ccc`. For Bryan’s review; not published and not a submitted Data safety declaration. This covers the Android Full/Google Play flavor. Confirm the contact address, artwork provenance and final release scope separately. The current hosted Android policy was retrieved with HTTP 200 and still displays 6 November 2026.

---

# Leaf Privacy Policy for Android

_Last updated: pending approval and publication_

Leaf is a comic and manga reader developed by Bryan Clark under the DigitalBC name. This policy describes the Google Play version, net.digitalbc.leafreader.

## Local reading

Leaf does not require a Leaf account. Imported comic copies, cover thumbnails, library metadata, reading lists, progress and preferences are stored on your device. Leaf has no advertising, analytics, tracking or integrated automatic crash-reporting SDK. DigitalBC does not operate a service that receives your comic library or reading history automatically.

When you select a comic, Leaf reads it and stores an app-managed copy for offline reading. A cloud provider selected through Android’s file picker may transfer the chosen file under its own policies.

## Optional SMB connections

Leaf connects directly to an SMB server you configure to test a connection, browse folders and import comics. Your server receives authentication and file requests and may keep logs. These requests do not pass through DigitalBC.

SMB host, share, username and password are saved in app-private preferences, including when Save & Test Connection fails. Saved SMB passwords do not have separate application-level encryption. Protection depends on Android’s sandbox and device security. SMB transport protection depends on your server and protocol settings; Leaf does not require encryption for every connection.

## Optional Jellyfin connections

Jellyfin uses an account on a server you choose. Sign-in sends your username and password to that server. Leaf stores the resulting access token encrypted using an Android Keystore key. Connection details and a random installation identifier are stored locally. Later authenticated requests use that token and identifier; the server may keep access logs.

When connected, startup and the open Library can request catalogue metadata and visible covers. Leaf indexes metadata while Library is open, searches your selected library, loads details and creator results, and refreshes on request or a throttled return to the foreground. Refresh reads the existing server catalogue; it does not trigger a server scan. Reading a remote-only comic downloads and validates a complete copy first. Retained copies remain available offline until you remove them.

Linked reading actions and sync/retry actions can exchange page position and read/unread status with your configured Jellyfin account. Failed or unconfirmed updates can remain queued locally for later retry. Use Sync reading position in comic details to reconcile progress. Conflicting positions remain available for your choice. Catalogue browsing alone does not send progress updates. Progress sync does not upload comic files, Leaf reading lists, ratings or favorites.

HTTPS uses normal certificate validation. If you explicitly permit local HTTP, authentication and content can travel without encryption to that configured server. Leaf does not claim that every supported server connection is encrypted. Disconnect removes the saved Jellyfin authentication token and stops the session; downloaded comics remain local.

## Metadata, exports and transfer

Leaf privately caches catalogue, list, detail and creator metadata, scoped to the configured server and account. Cached metadata may be incomplete or stale. Metadata and covers alone do not make a comic readable offline; a complete validated file is required.

Export Backup creates an unencrypted JSON file containing comic metadata, progress, reading lists, timestamps and selected preferences. Remote list entries can include server/account/item/library identifiers and saved metadata. Exports exclude authentication tokens, SMB connection details, comic files, covers and internal device file paths. You choose where to save the file. It can reveal your reading choices, so review it before sharing and store it securely. Restore reads the selected backup and applies its data locally.

Automatic Android cloud backup is disabled. Android’s database-only device transfer rules can include library/progress/list records, local file locations, remote identifiers, cached metadata and pending progress. The separate credential file, SMB preferences and comic files are excluded. Transfer availability and behavior depend on Android and the device. These rules do not remove older backups or exported files.

## Diagnostics, external pages and support

Local diagnostic logs can include file paths or provider error messages. Leaf does not automatically send those logs to DigitalBC. Google Play and Android may separately collect installation and diagnostic information under their own policies and your settings; Google may make opted-in diagnostic reports available to the developer.

Opening project, license or privacy links uses your browser or another app. Those services’ privacy policies apply. Leaf attaches no comic files, reading history or credentials to those links. The Google Play flavor omits external tip links. Other flavors may offer an optional Ko-fi link; Ko-fi and its payment providers handle those interactions under their own policies.

If you contact bclark@digitalbc.net, we receive the contact details, message and attachments you choose to send. We use support correspondence to respond and troubleshoot and retain it as needed for those purposes and applicable obligations. Do not send passwords, private credentials or comic files you lack permission to share. Contact us to request deletion of information we hold; required legal records may need to be retained.

## Retention and removal

Removing an app-managed comic does not delete its original file or server copy. Use Leaf’s connection-removal controls to remove saved connections. Clear app storage or uninstall to remove local app data. Manage exported files, original comics, older backups, server logs and external-service records separately. Removing a server connection does not delete downloaded comics.

We will update the policy date when approved changes are published. Privacy contact: Bryan Clark / DigitalBC, bclark@digitalbc.net.

---

## Evidence and approval scope

Source: AppSettings.kt (SMB preferences/installation ID), JellyfinCredentialStore.kt (encrypted token file), JellyfinClient.kt (auth/transport/redirect policy), LibraryRepository.kt (durable scoped progress/sync), BackupManager.kt and JellyfinSnapshots.kt (portable fields), backup_rules.xml/data_extraction_rules.xml (OS backup scope), MenuScreen.kt and flavor bool resources (links/tips). These are implementation observations, not blanket certification of platform transfer or store compliance.

The hosted policy, Console declarations and listing require their own reviewed publication. Google’s Data safety collection definition includes off-device transmissions even to third-party servers; developer-hosted analytics being absent does not by itself justify a no-data declaration. See https://support.google.com/googleplay/android-developer/answer/10787469 .
