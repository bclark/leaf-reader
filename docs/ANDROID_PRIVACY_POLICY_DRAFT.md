# Android privacy policy draft for 0.3.8

Prepared 17 November 2026 against source `6455ccc`. For Bryan’s review; not published and not a submitted Data safety declaration. This covers the Android Full/Google Play flavor. Bryan confirmed the support/privacy address and support-email retention/deletion practice on 17 November 2026. These are owner-confirmed commitments, not independently audited practices. Confirm final release scope separately. Bryan has confirmed that Gemini generated the current Leaf logo and Android launcher artwork without third-party artwork inputs; this is owner-reported provenance, not a legal ownership determination. The current hosted Android policy was retrieved with HTTP 200 and still displays 6 November 2026.

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

Jellyfin uses an account on a server you choose. Sign-in sends your username and password to that server. Leaf stores the resulting access token encrypted using an Android Keystore key. Connection details and a random installation identifier are stored locally. Later authenticated requests use that token and identifier and identify the configured account; the server may keep access logs. Searching a remote catalogue sends the search terms to that server. Requests for items, covers and downloads identify the requested content.

When connected, startup and the open Library can request catalogue metadata and visible covers. Leaf indexes metadata while Library is open, searches your selected library, loads details and creator results, and refreshes on request or a throttled return to the foreground. Refresh reads the existing server catalogue; it does not trigger a server scan. Reading a remote-only comic downloads and validates a complete copy first. Retained copies remain available offline until you remove them.

Linked reading actions and sync/retry actions can exchange page position, read/unread status and reading timestamps with your configured Jellyfin account. Failed or unconfirmed updates can remain queued locally for later retry. Use Sync reading position in comic details to reconcile progress. Conflicting positions remain available for your choice. Catalogue browsing alone does not send progress updates. Progress sync does not upload comic files, Leaf reading lists, ratings or favorites.

HTTPS uses normal certificate validation. If you explicitly permit local HTTP, authentication and content can travel without encryption to that configured server. Leaf does not claim that every supported server connection is encrypted. Disconnect removes the saved Jellyfin authentication token and stops the session; downloaded comics remain local.

## Metadata, exports and transfer

Leaf privately caches catalogue, list, detail and creator metadata, scoped to the configured server and account. Cached metadata may be incomplete or stale. Metadata and covers alone do not make a comic readable offline; a complete validated file is required.

Export Backup creates an unencrypted JSON file containing comic metadata, progress, reading lists, timestamps and selected preferences. Remote list entries can include server/account/item/library identifiers and saved metadata. Exports exclude authentication tokens, SMB connection details, comic files, covers and internal device file paths. You choose where to save the file. It can reveal your reading choices, so review it before sharing and store it securely. Restore reads the selected backup and applies its data locally.

Automatic Android cloud backup is disabled. Android’s database-only device transfer rules can include library/progress/list records, local file locations, remote identifiers, cached metadata and pending progress. The separate credential file, SMB preferences and comic files are excluded. Transfer availability and behavior depend on Android and the device. These rules do not remove older backups or exported files.

## Diagnostics, external pages and support

Local diagnostic logs can include file paths or provider error messages. Leaf does not automatically send those logs to DigitalBC. Google Play and Android may separately collect installation and diagnostic information under their own policies and your settings; Google may make opted-in diagnostic reports available to the developer.

Opening project, license or privacy links uses your browser or another app. Those services’ privacy policies apply. Leaf attaches no comic files, reading history or credentials to those links. The Google Play flavor omits external tip links. Other flavors may offer an optional Ko-fi link; Ko-fi and its payment providers handle those interactions under their own policies.

If you contact bclark@digitalbc.net, we receive the contact details, message and attachments you choose to send. We use support emails to respond and help you, keep them for as long as needed to provide that help, and delete them when they are no longer needed. You can request deletion of your support emails by contacting bclark@digitalbc.net. Do not send passwords, private credentials or comic files you lack permission to share.

## Retention and removal

Removing an app-managed comic does not delete its original file or server copy. Use Leaf’s connection-removal controls to remove saved connections. Clear app storage or uninstall to remove local app data. Manage exported files, original comics, older backups, server logs and external-service records separately. Removing a server connection does not delete downloaded comics or erase account records, progress or logs held by the server operator.

We will update the policy date when approved changes are published. Privacy contact: Bryan Clark / DigitalBC, bclark@digitalbc.net.

---

## Evidence and approval scope

Source: AppSettings.kt (SMB preferences/installation ID), JellyfinCredentialStore.kt (encrypted token file), JellyfinClient.kt (auth/transport/redirect policy), LibraryRepository.kt (durable scoped progress/sync), BackupManager.kt and JellyfinSnapshots.kt (portable fields), backup_rules.xml/data_extraction_rules.xml (OS backup scope), MenuScreen.kt and flavor bool resources (links/tips). These are implementation observations, not blanket certification of platform transfer or store compliance.

The hosted policy, Console declarations and listing require their own reviewed publication. Google’s Data safety collection definition includes off-device transmissions even to third-party servers; developer-hosted analytics being absent does not by itself justify a no-data declaration. See https://support.google.com/googleplay/android-developer/answer/10787469 .


## Reviewer-only reconciliation (17 November 2026; do not publish this section)

Current PR head reviewed: `ef84dee`; implementation evidence is pinned to candidate `6455ccc`, not a moving worktree. The saved Play Console Data safety summary (last edited 8 November) says “App doesn’t collect or share data” and “Data isn’t encrypted.” The full declaration form did not load during inspection, so its detailed answers have not been audited. Publishing this policy alone does not reconcile the no-collection answer.

### Proposed Data safety answers, not approved or submitted

| Field | Proposed review outcome | Implementation evidence / remaining qualification |
| --- | --- | --- |
| Collects data | Yes | Optional configured-server traffic leaves the device; it is not limited to DigitalBC-operated services. |
| User IDs | Collected, optional, app functionality | Jellyfin account/user identifiers and SMB authentication; `JellyfinClient.kt:66–80`, `SmbClient.kt:95–96`. |
| Device or other IDs | Collected, optional, app functionality | Random installation ID in Jellyfin requests; `AppSettings.kt:107–113`, `JellyfinClient.kt:79–80`. |
| App interactions | Collected, optional, app functionality | Reading position/read status/timestamp; `JellyfinClient.kt:508–512`. |
| In-app search history | Collected, optional, app functionality | Remote search terms; shared `JellyfinCatalogue.kt:85`. |
| Files and docs | Review transmitted file identifiers/paths and selected export destinations | SMB requests identify remote files (`SmbClient.kt:35–40,70–75`). This is not a claim that progress sync uploads comic files or that local-only imports are collected. |
| All collected data encrypted in transit | No | Explicit local HTTP and SMB connections without universally required transport encryption remain supported. |
| Ephemeral processing | Do not assert without evidence | Progress persists remotely; retention/logging at arbitrary configured servers is not established. |
| Sharing | Unresolved per flow | Review expected user-initiated transfers and automatic sync/retries against the sharing exclusions. A chosen server or manual sync button does not establish an exemption for every transfer. |
| Deletion mechanism | Unresolved | Connection removal is not remote account/data deletion. Do not promise deletion of independently operated server records. |

The classification above is a technical proposal, not a legal certification. Optional describes the ability to use Leaf locally without connecting a server. Do not select analytics, advertising, account-management or other purposes merely by inference from authentication. Evaluate any additional applicable data categories against the final form and actual flows; do not infer broad personal-data collection from arbitrary support attachments.

### Owner facts and release state

- **Owner-confirmed on 17 November:** `bclark@digitalbc.net` is Leaf’s support/privacy address. Bryan agreed to keep support emails as long as needed to help the person, then delete them when no longer needed; users can request deletion at that address. This resolves the support wording question only. It does not establish deletion of independently operated server data, a complete Play deletion mechanism, or approval to publish this policy.
- **Answered:** artwork source/inputs; existing privacy hosting route; B-05 recovery test reported successful by Bryan. Recovery is owner-reported, not independently observed by this reviewer.
- **Console observed:** registered upload certificate matches the candidate; all-app-bundles view lists 30600 and 30700, not 30800; managed publishing is off; the pending Alpha feedback-channel change remains untouched. These observations do not authorize uploading, submitting, publishing or discarding existing changes.
- **Publication gate:** approve the final policy and reconciled declaration together, then verify the hosted page and Console separately through their authorized workflows. Keep this draft and reviewer notes out of the public policy until the public wording is approved; do not publish the preparatory header, evidence section or this reconciliation section.
