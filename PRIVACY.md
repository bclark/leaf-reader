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
