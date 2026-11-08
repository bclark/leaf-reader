# iOS handoff: Android 0.3.7 (Jellyfin Integration & Progress Sync)

## Purpose and current state

Bring the Android 0.3.7 Jellyfin media server integration and two-way reading progress synchronization to iOS (`iosApp/`).

Android implementation is complete, merged into `main`, and verified with automated test suites (`JellyfinClientTest`, `LibraryRepositoryJellyfinTest`, `JellyfinModelsTest`, `AppDatabaseMigrationTest`). iOS runtime implementation is ready to be developed using the shared KMP models and protocol specifications detailed below.

Relevant Android sources:
- `shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinModels.kt`
- `shared/src/commonMain/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinProgress.kt`
- `shared/src/commonTest/kotlin/net/digitalbc/leafreader/data/jellyfin/JellyfinModelsTest.kt`
- `app/src/main/java/net/digitalbc/leafreader/data/jellyfin/JellyfinClient.kt`
- `app/src/main/java/net/digitalbc/leafreader/data/jellyfin/JellyfinCredentialStore.kt`
- `app/src/main/java/net/digitalbc/leafreader/data/db/RemoteComicLinkEntity.kt`
- `app/src/main/java/net/digitalbc/leafreader/data/db/RemoteComicLinkDao.kt`
- `app/src/main/java/net/digitalbc/leafreader/data/library/LibraryRepository.kt`
- `app/src/main/java/net/digitalbc/leafreader/ui/library/JellyfinBrowserScreen.kt`
- `app/src/main/java/net/digitalbc/leafreader/ui/menu/MenuScreen.kt`

Likely iOS integration points: `JellyfinClient.swift`, `JellyfinStore.swift`, `KeychainHelper.swift`, `LibraryStore.swift`, `ReaderView.swift`, and `MenuView.swift`.

---

## 1. Protocol & Ticks Specification

Jellyfin represents playback position in `PlaybackPositionTicks` (100-nanosecond intervals), where **1 second = 10,000,000 ticks**.

For book/comic formats in Jellyfin:
- Page index $N$ (0-based) is mapped directly to seconds: `ticks = N * 10,000,000L`.
- Page index derivation: `pageIndex = ticks / 10,000,000L`.

Shared reference:
```kotlin
object JellyfinProgress {
    const val TICKS_PER_SECOND = 10_000_000L
    const val TICKS_PER_PAGE = TICKS_PER_SECOND

    fun pageToTicks(pageIndex: Int): Long = pageIndex.coerceAtLeast(0).toLong() * TICKS_PER_PAGE
    fun ticksToPage(ticks: Long): Int = (ticks.coerceAtLeast(0) / TICKS_PER_PAGE).toInt()
}
```

---

## 2. Remote Link Persistence

When a comic is downloaded from Jellyfin into the local library, create a remote link mapping the local comic ID to its remote Jellyfin item:

| Field | Type | Description |
| --- | --- | --- |
| `comicId` | String (PK) | Local comic identifier |
| `provider` | String | `"jellyfin"` |
| `remoteItemId` | String | Jellyfin item GUID |
| `remoteServerUrl` | String | Base URL of the Jellyfin server |
| `lastSyncedTicks` | Long | Ticks value last confirmed synced |
| `lastSyncedAt` | Long | Epoch timestamp of last sync |

---

## 3. Two-Way Progress Sync Flow

1. **Import / Download from Jellyfin:**
   - Seed local `currentPage` with `JellyfinProgress.ticksToPage(item.playbackPositionTicks)`.
   - Seed local `isRead` with `item.played`.
   - Insert `RemoteComicLinkEntity`.
2. **Local Page Turn / Reader Close:**
   - Check if the comic has a `remote_comic_links` record for provider `"jellyfin"`.
   - If connected and online, post updated ticks:
     `POST /Sessions/Playing/Progress` with `{ "ItemId": remoteItemId, "PositionTicks": pageToTicks(currentPage) }`.
   - If the user finishes the comic:
     `POST /Users/{userId}/PlayedItems/{remoteItemId}`.
   - If the user marks an issue unread:
     `DELETE /Users/{userId}/PlayedItems/{remoteItemId}`.
3. **Error Resilience:**
   - Progress sync failures must never interrupt the local reading experience or throw unhandled exceptions. Failures should fail silently or log locally.

---

## 4. API Endpoints & Headers

### Client Authorization Header
Every request to Jellyfin requires the standard authorization header:
```http
X-Emby-Authorization: MediaBrowser Client="Leaf", Device="iOS", DeviceId="<Stable-UUID>", Version="<Version>"
```
Once authenticated, include `X-Emby-Token: <AccessToken>`.

### Key Endpoints
1. **Authenticate by Username:**
   `POST /Users/AuthenticateByName`
   - Body: `{"Username": "...", "Pw": "..."}`
   - Returns: `AccessToken`, `User.Id`, `ServerId`.
2. **Fetch User Views (Top-Level Libraries):**
   `GET /Users/{userId}/Views`
   - Returns list of views (`CollectionType` = `books`, `comics`, etc.).
3. **Fetch Folder Items:**
   `GET /Users/{userId}/Items?parentId={parentId}&fields=Path,MediaSources,UserData,Overview&sortBy=SortName`
   - Filter/identify importable items by file extension (`.cbz`, `.cbr`, `.pdf`, `.epub`) or `isFolder`.
4. **Stream / Download File:**
   `GET /Items/{itemId}/File` (includes `X-Emby-Token` header).
5. **Update Progress:**
   `POST /Sessions/Playing/Progress`
6. **Mark / Unmark Played:**
   `POST /Users/{userId}/PlayedItems/{itemId}`
   `DELETE /Users/{userId}/PlayedItems/{itemId}`

---

## 5. Security & Insecure HTTP Handling

1. **HTTPS Preference:** HTTPS is strongly recommended.
2. **Local HTTP Opt-In:** If a user specifies an `http://` URL:
   - Require explicit toggle confirmation ("Allow unencrypted local HTTP") and display a warning dialog noting credentials and tokens will travel unencrypted across the local network.
   - On iOS, configure App Transport Security (`NSAppTransportSecurity`) with `NSAllowsLocalNetworking: true` rather than opening arbitrary HTTP traffic.
3. **Secure Credential Storage:** Store `AccessToken` and server configuration in the iOS Keychain. Never persist plain text passwords or auth tokens in `UserDefaults`.
4. **Privacy:** Zero analytics, zero crash telemetry, 100% offline-first reading.
