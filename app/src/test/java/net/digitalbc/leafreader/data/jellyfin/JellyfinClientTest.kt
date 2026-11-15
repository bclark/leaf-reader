package net.digitalbc.leafreader.data.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class JellyfinClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyfinClient
    private lateinit var testDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = JellyfinClient(deviceId = "test-device-id", clientVersion = "0.3.7")
        val context = RuntimeEnvironment.getApplication()
        testDir = File(context.cacheDir, "jellyfin-test-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        testDir.deleteRecursively()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun creatorQueryRetainsStructuredPeopleAndScopedPagination() = runBlocking {
        val person = JellyfinPerson("A. Example", "Writer", id = "abc")
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"deep","Name":"Unloaded title",
            "Type":"Book","Container":"cbz","People":[{"Id":"abc","Name":"A. Example","Type":"Writer"},
            {"Name":"B. Example","Role":"Letterer"}]}],"StartIndex":30,"TotalRecordCount":31}"""))
        val page = client.getCatalogue(JellyfinServerConfig(baseUrl(), true),
            JellyfinAuth("token", "user", "server", "Reader"),
            JellyfinCatalogueQuery("books", startIndex = 30, creator = person))
        val request = server.takeRequest()
        assertEquals("abc", request.requestUrl!!.queryParameter("PersonIds"))
        assertEquals("books", request.requestUrl!!.queryParameter("ParentId"))
        assertNull(request.requestUrl!!.queryParameter("SearchTerm"))
        assertEquals(person, page.entries.single().people.first())
        assertEquals("Letterer", page.entries.single().people.last().role)
        val details = JellyfinItemDetails(JellyfinItemIdentity("server", "user", "deep"),
            page.entries.single().item, people = page.entries.single().people,
            overview = "Verified synopsis", genres = listOf("Adventure"))
        val saved = JellyfinSnapshots.details(JellyfinSnapshots.details(details), details.identity)
        assertEquals(details.people, saved.people)
        assertEquals(details.overview, saved.overview)
        assertTrue(saved.fromCache)
        assertFalse(JellyfinSnapshots.details(details).contains("token"))
    }

    @Test
    fun observedServerProgressRefreshesContinueRecencyWithoutInventingDates() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Id":"resume","UserData":{"Played":false,
            "PlaybackPositionTicks":40000,"PlayedPercentage":40,"LastPlayedDate":"2026-11-14T12:00:00Z"}}"""))
        val snapshot = client.getProgress(JellyfinServerConfig(baseUrl(), true),
            JellyfinAuth("token", "user", "server", "Reader"), "resume")
        val entry = JellyfinCatalogueEntry(JellyfinItem("resume", "Fixture", false, "cbz"), "Book", false,
            lastPlayedDate = "2026-11-10T00:00:00Z")
        val update = JellyfinProgressUpdate(JellyfinItemIdentity("server", "user", "resume"), baseUrl(), snapshot, 1)
        val refreshed = update.apply(listOf(entry)).single()
        assertEquals("2026-11-14T12:00:00Z", refreshed.lastPlayedDate)
        assertEquals(40000L, refreshed.item.playbackPositionTicks)
        assertEquals(entry.lastPlayedDate, update.copy(snapshot = snapshot.copy(lastPlayedDate = null)).apply(listOf(entry)).single().lastPlayedDate)
        assertEquals(entry, update.copy(identity = update.identity.copy(itemId = "another")).apply(listOf(entry)).single())
    }

    @Test
    fun catalogueRetainsContinueMetadataAndOldSnapshotsStayReadable() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"resume","Name":"Continue fixture",
            "Type":"Book","Container":"cbz","UserData":{"Played":false,"PlaybackPositionTicks":40000,
            "PlayedPercentage":25.5,"LastPlayedDate":"2026-11-14T12:00:00Z"}}],"TotalRecordCount":1}"""))
        val entry = client.getCatalogue(JellyfinServerConfig(baseUrl(), true),
            JellyfinAuth("token", "user", "server", "Reader"), JellyfinCatalogueQuery("books")).entries.single()
        assertEquals("2026-11-14T12:00:00Z", entry.lastPlayedDate)
        assertEquals(25.5, entry.playedPercentage!!, 0.0)
        assertEquals(entry, JellyfinSnapshots.decode(JellyfinSnapshots.encode(entry)))
        val old = org.json.JSONObject(JellyfinSnapshots.encode(entry))
        old.remove("lastPlayedDate"); old.remove("playedPercentage")
        val restored = JellyfinSnapshots.decode(old.toString())
        assertNull(restored.lastPlayedDate)
        assertNull(restored.playedPercentage)
        assertEquals(40000L, restored.item.playbackPositionTicks)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("books", request.requestUrl!!.queryParameter("ParentId"))
    }

    @Test
    fun folderFallbackRequiresRealFolderAndSelectedLibraryAncestry() = runBlocking {
        val config = JellyfinServerConfig(baseUrl() + "/proxy", true)
        val auth = JellyfinAuth("token", "user", "server", "Reader")
        server.enqueue(MockResponse().setBody("""{"Id":"run","Name":"Hellboy in Hell","IsFolder":true}"""))
        server.enqueue(MockResponse().setBody("""[{"Id":"franchise"},{"Id":"books"}]"""))
        val folder = client.getCatalogueFolder(config, auth, "books", "run")!!
        assertEquals("Hellboy in Hell", folder.name)
        assertEquals("books", folder.libraryId)
        val detail = server.takeRequest()
        assertEquals("GET", detail.method)
        assertTrue(detail.getHeader("Authorization")!!.contains("token"))
        assertTrue(server.takeRequest().path!!.startsWith("/proxy/Items/run/Ancestors"))
        server.enqueue(MockResponse().setBody("""{"Id":"run","Name":"Same name","IsFolder":true}"""))
        server.enqueue(MockResponse().setBody("""[{"Id":"another-library"}]"""))
        assertNull(client.getCatalogueFolder(config, auth, "books", "run"))
        server.enqueue(MockResponse().setResponseCode(403))
        assertNull(client.getCatalogueFolder(config, auth, "books", "denied"))
        assertNull(client.getCatalogueFolder(config, auth, "books", "books"))
    }

    @Test
    fun cataloguePagingPreservesUnknownStateAndRejectsStalledPages() = runBlocking {
        val auth = JellyfinAuth("token", "user", "server", "Reader")
        val config = JellyfinServerConfig(baseUrl() + "/proxy", true)
        server.enqueue(MockResponse().setBody("""{"Items":[
            {"Id":"unknown","Name":"Unknown","Type":"Book","Container":"cbz"},
            {"Id":"unread","Name":"Unread","Type":"Book","Container":"cbz","UserData":{"Played":false}}
        ],"StartIndex":100,"TotalRecordCount":250}"""))
        val page = client.getCatalogue(config, auth, JellyfinCatalogueQuery("books", startIndex = 100))
        assertEquals(102, page.nextIndex)
        assertTrue(page.hasMore)
        assertNull(page.entries.first().played)
        assertEquals(false, page.entries.last().played)
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/proxy/Items?"))
        assertEquals("books", request.requestUrl!!.queryParameter("ParentId"))
        assertEquals("true", request.requestUrl!!.queryParameter("Recursive"))
        assertTrue(request.getHeader("Authorization")!!.contains("token"))
        server.enqueue(MockResponse().setBody("""{"Items":[],"StartIndex":102,"TotalRecordCount":250}"""))
        try {
            client.getCatalogue(config, auth, JellyfinCatalogueQuery("books", startIndex = 102))
            fail("Empty intermediate page must not silently complete")
        } catch (error: JellyfinException) { assertEquals(JellyfinFailureKind.PARSE_ERROR, error.kind) }
    }

    @Test
    fun collectionMembersRequireBookTypeAndMatchingLibraryAncestry() = runBlocking {
        val config = JellyfinServerConfig(baseUrl(), true)
        val auth = JellyfinAuth("token", "user", "server", "Reader")
        server.enqueue(MockResponse().setBody("""{"Items":[
            {"Id":"inside","Type":"Book","Container":"cbz"},
            {"Id":"outside","Type":"Book","Container":"pdf"},
            {"Id":"movie","Type":"Movie","Container":"mkv"}
        ],"TotalRecordCount":3}"""))
        server.enqueue(MockResponse().setBody("""[{"Id":"folder"},{"Id":"books"}]"""))
        server.enqueue(MockResponse().setBody("""[{"Id":"other-library"}]"""))
        val page = client.getCatalogue(config, auth,
            JellyfinCatalogueQuery("books", JellyfinCatalogueView.LISTS, "collection"))
        assertEquals(listOf("inside"), page.entries.map { it.item.id })
        assertEquals(3, page.nextIndex)
        assertFalse(page.hasMore)
        assertEquals("collection", server.takeRequest().requestUrl!!.queryParameter("ParentId"))
        assertTrue(server.takeRequest().path!!.startsWith("/Items/inside/Ancestors"))
        assertTrue(server.takeRequest().path!!.startsWith("/Items/outside/Ancestors"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun authenticateSuccess() = runBlocking {
        val mockJson = """
            {
                "AccessToken": "token-xyz-123",
                "ServerId": "server-abc",
                "User": {
                    "Id": "user-456",
                    "Name": "Bryan"
                },
                "ServerInfo": {
                    "Name": "Home NAS"
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = client.authenticate(config, "Bryan", "secret")

        assertEquals("token-xyz-123", auth.accessToken)
        assertEquals("user-456", auth.userId)
        assertEquals("server-abc", auth.serverId)
        assertEquals("Bryan", auth.username)
        assertEquals("Home NAS", auth.serverName)

        val recorded = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", recorded.path)
        val authHeader = recorded.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.contains("DeviceId=\"test-device-id\""))
        assertTrue(authHeader.contains("Version=\"0.3.7\""))
    }

    @Test
    fun authenticationRequiresAnAuthoritativeServerIdentity() = runBlocking {
        val config = JellyfinServerConfig(baseUrl(), true)
        for (identity in listOf("", "\"ServerId\":\"\",", "\"ServerId\":null,", "\"ServerId\":false,", "\"ServerId\":0,", "\"ServerId\":\"jellyfin-server\",")) {
            server.enqueue(MockResponse().setBody("""{$identity"AccessToken":"token","User":{"Id":"user"}}"""))
            try {
                client.authenticate(config, "Reader", "password")
                fail("Missing or shared server identity must be rejected")
            } catch (error: JellyfinException) {
                assertEquals(JellyfinFailureKind.PARSE_ERROR, error.kind)
            }
        }
        server.enqueue(MockResponse().setBody("""{"AccessToken":"token","User":{"Id":"user"},
            "SessionInfo":{"ServerId":"verified-server"}}"""))
        assertEquals("verified-server", client.authenticate(config, "Reader", "password").serverId)
    }

    @Test
    fun legacySharedIdentityCannotBeLoadedOrOverwriteValidCredentials() {
        val context = RuntimeEnvironment.getApplication()
        val key = javax.crypto.spec.SecretKeySpec("LeafTestKey12345678901234567890!".toByteArray(), "AES")
        val store = JellyfinCredentialStore(context, keyProvider = { key })
        store.clear()
        val legacy = """{"accessToken":"token","userId":"user","serverId":"jellyfin-server","username":"Reader"}"""
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val file = File(context.filesDir, "jellyfin_auth.enc")
        file.outputStream().use {
            it.write(cipher.iv.size)
            it.write(cipher.iv)
            it.write(cipher.doFinal(legacy.toByteArray()))
        }
        assertNull(store.loadAuth())
        assertTrue("Keep the encrypted legacy file available for explicit reconnection", file.exists())
        val valid = JellyfinAuth("valid-token", "user", "verified-server", "Reader")
        store.saveAuth(valid)
        try {
            store.saveAuth(valid.copy(serverId = "jellyfin-server"))
            fail("Shared identity must not replace valid credentials")
        } catch (_: IllegalArgumentException) {
            assertEquals(valid, store.loadAuth())
        }
        store.clear()
    }

    @Test
    fun authenticate401ThrowsAuthenticationError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Invalid user or pw"))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        try {
            client.authenticate(config, "Bryan", "wrong")
            fail("Expected JellyfinException")
        } catch (e: JellyfinException) {
            assertEquals(JellyfinFailureKind.AUTHENTICATION, e.kind)
        }
    }

    @Test
    fun getViewsSuccess() = runBlocking {
        val mockJson = """
            {
                "Items": [
                    { "Id": "v1", "Name": "Comics", "CollectionType": "books" },
                    { "Id": "v2", "Name": "Movies", "CollectionType": "movies" }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")
        val views = client.getViews(config, auth)

        assertEquals(2, views.size)
        assertEquals("Comics", views[0].name)
        assertEquals("books", views[0].collectionType)
    }

    @Test
    fun getItemsSuccess() = runBlocking {
        val mockJson = """
            {
                "TotalRecordCount": 2,
                "Items": [
                    {
                        "Id": "folder1",
                        "Name": "Batman Series",
                        "IsFolder": true,
                        "Type": "Folder"
                    },
                    {
                        "Id": "item2",
                        "Name": "Batman #01.cbz",
                        "IsFolder": false,
                        "Type": "Book",
                        "Container": "cbz",
                        "Size": 15000000
                    }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")
        val result = client.getItems(config, auth, parentId = "v1")

        assertEquals(2, result.totalRecordCount)
        assertEquals(2, result.items.size)
        assertTrue(result.items[0].isFolder)
        assertFalse(result.items[0].isImportable)

        assertFalse(result.items[1].isFolder)
        assertTrue(result.items[1].isImportable)
        assertEquals("cbz", result.items[1].container)
    }

    @Test
    fun detailFetchUsesAuthenticatedMetadataOnlyRequestAndPreservesAvailableFields() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"Id":"item2","Name":"King Spawn - Vol. 01","Type":"Book",
             "Overview":"A synopsis.","SeriesName":"King Spawn","ProductionYear":2021,
             "IndexNumber":1,"ParentIndexNumber":2,"Etag":"metadata-version",
             "MediaSources":[{"Container":"pdf","Size":123456,"Path":"/private/comics/book.pdf"}],
             "ImageTags":{"Primary":"cover-tag"},
             "People":[{"Name":"Writer Name","Type":"Writer"},{"Name":"Artist Name","Role":"Pencils"},{}],
             "Genres":["Comics",null," "],
             "UserData":{"PlaybackPositionTicks":110000,"Played":false,
                 "PlayedPercentage":12.5,"LastPlayedDate":"2026-11-10T12:00:00Z"}}
        """.trimIndent()))
        val result = client.getItemDetails(
            JellyfinServerConfig(baseUrl(), true), JellyfinAuth("tok", "usr", "srv", "reader"), "item2",
        )
        assertEquals(JellyfinItemIdentity("srv", "usr", "item2"), result.identity)
        assertEquals("King Spawn - Vol. 01", result.item.name)
        assertEquals("pdf", result.item.container)
        assertEquals(123456L, result.item.sizeBytes)
        assertEquals("cover-tag", result.item.imageTag)
        assertEquals(110000L, result.item.playbackPositionTicks)
        assertEquals("A synopsis.", result.overview)
        assertEquals(1, result.indexNumber)
        assertEquals(2, result.parentIndexNumber)
        assertEquals(listOf(JellyfinPerson("Writer Name", "Writer"), JellyfinPerson("Artist Name", role = "Pencils")), result.people)
        assertEquals(listOf("Comics"), result.genres)
        assertEquals(12.5, result.playedPercentage)
        assertEquals("2026-11-10T12:00:00Z", result.lastPlayedDate)
        assertEquals("metadata-version", result.etag)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/Items/item2?UserId=usr", request.path)
        assertTrue(request.getHeader("Authorization")!!.contains("tok"))
        assertEquals(1, server.requestCount)
        assertTrue(testDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun detailsHandleNullMetadataWithoutInventingProgressOrCreators() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"Id":"item","Name":"Minimal.cbz","Container":"cbz","Overview":null,
             "ProductionYear":null,"IndexNumber":null,"People":null,"Genres":null,
             "ImageTags":{"Primary":null},"UserData":{"PlayedPercentage":101,"LastPlayedDate":null}}
        """.trimIndent()))
        val result = client.getItemDetails(
            JellyfinServerConfig(baseUrl(), true), JellyfinAuth("tok", "usr", "srv", "reader"), "item",
        )
        assertNull(result.overview)
        assertNull(result.indexNumber)
        assertNull(result.item.productionYear)
        assertNull(result.item.imageTag)
        assertNull(result.playedPercentage)
        assertNull(result.lastPlayedDate)
        assertTrue(result.people.isEmpty())
        assertTrue(result.genres.isEmpty())
    }

    @Test
    fun detailErrorsKeepAuthenticationAndNotFoundDistinctFromBadMetadata() = runBlocking {
        val config = JellyfinServerConfig(baseUrl(), true)
        val auth = JellyfinAuth("tok", "usr", "srv", "reader")
        for ((response, expected) in listOf(
            MockResponse().setResponseCode(401) to JellyfinFailureKind.AUTHENTICATION,
            MockResponse().setResponseCode(404) to JellyfinFailureKind.NOT_FOUND,
            MockResponse().setBody("not json") to JellyfinFailureKind.PARSE_ERROR,
            MockResponse().setBody("""{"Id":"wrong","Name":"Wrong book"}""") to JellyfinFailureKind.PARSE_ERROR,
        )) {
            server.enqueue(response)
            val error = runCatching { client.getItemDetails(config, auth, "item") }.exceptionOrNull()
            assertTrue(error is JellyfinException)
            assertEquals(expected, (error as JellyfinException).kind)
        }
    }

    @Test
    fun listingAndDetailsShareNullSafeMediaSourceParsing() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"Items":[{"Id":"item","Name":"Comic","Container":null,"Path":null,
            "ParentId":null,"SeriesName":null,"ProductionYear":null,
            "MediaSources":[{"Container":"cbz","Size":500,"Path":"/comics/book.cbz"}]}],
            "TotalRecordCount":1}
        """.trimIndent()))
        val item = client.getItems(
            JellyfinServerConfig(baseUrl(), true), JellyfinAuth("tok", "usr", "srv", "reader"), "folder",
        ).items.single()
        assertEquals("cbz", item.container)
        assertEquals(500L, item.sizeBytes)
        assertEquals("/comics/book.cbz", item.path)
        assertEquals("folder", item.parentId)
        assertNull(item.seriesName)
        assertNull(item.productionYear)
        assertTrue(item.isImportable)
    }

    @Test
    fun downloadToFileSuccess() = runBlocking {
        val payload = "Simulated comic payload content 123456789".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", payload.size)
                .setBody(okio.Buffer().write(payload))
        )

        val partFile = File(testDir, "test.part")
        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")

        var reportedProgress = 0f
        client.downloadToFile(
            config = config,
            auth = auth,
            itemId = "item1",
            partFile = partFile,
            onProgress = { progress -> reportedProgress = progress.fraction },
        )

        assertTrue(partFile.exists())
        assertEquals(payload.size.toLong(), partFile.length())
        assertEquals(1.0f, reportedProgress, 0.01f)
    }

    @Test
    fun downloadRejectsHtmlResponse() = runBlocking {
        val htmlPayload = "<html><body>Please login to captive portal</body></html>"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(htmlPayload)
        )

        val partFile = File(testDir, "test_html.part")
        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")

        try {
            client.downloadToFile(config, auth, "item1", partFile)
            fail("Expected JellyfinException for HTML response")
        } catch (e: JellyfinException) {
            assertEquals(JellyfinFailureKind.PARSE_ERROR, e.kind)
            assertFalse("Part file must be deleted on error", partFile.exists())
        }
    }

    @Test
    fun downloadCancellationCleansUpPartFile() = runBlocking {
        val payload = ByteArray(1024 * 1024) { 1 } // 1MB
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", payload.size)
                .setBody(okio.Buffer().write(payload))
        )

        val partFile = File(testDir, "test_cancelled.part")
        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")

        var isCancelled = false
        try {
            client.downloadToFile(
                config = config,
                auth = auth,
                itemId = "item1",
                partFile = partFile,
                onProgress = { isCancelled = true },
                isCancelled = { isCancelled },
            )
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertFalse("Part file must be removed when cancelled", partFile.exists())
        }
    }

    @Test
    fun leavingCatalogueCancelsAnUnresponsiveCoverRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) {
            client.loadCoverBitmap(
                JellyfinServerConfig(baseUrl(), allowInsecureHttp = true),
                JellyfinAuth("token", "user", "server", "Reader"), "comic",
            )
        }
        try {
            val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(request)
            assertTrue(request!!.path!!.startsWith("/Items/comic/Images/Primary"))
            job.cancel()
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun postAuthenticationRedirectIsRejected() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://127.0.0.1:8096/other/endpoint")
        )
        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        try {
            client.authenticate(config, "bclark", "mypassword")
            fail("Expected JellyfinException for POST redirect")
        } catch (e: JellyfinException) {
            assertEquals(JellyfinFailureKind.AUTHENTICATION, e.kind)
            assertTrue(e.message?.contains("POST") == true)
        }
    }

    @Test
    fun crossOriginRedirectStripsAuthorizationHeaders() = runBlocking {
        val redirectServer = MockWebServer()
        redirectServer.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "${redirectServer.url("/target/cover.jpg")}")
            )
            redirectServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody(okio.Buffer().write(ByteArray(16) { 1 }))
            )

            val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
            val auth = JellyfinAuth("secret_access_token", "user1", "srv1", "Bryan")

            client.loadCoverBitmap(config, auth, "item123")

            val initialReq = server.takeRequest()
            assertNotNull(initialReq.getHeader("Authorization"))
            assertNotNull(initialReq.getHeader("X-Emby-Authorization"))

            val redirectedReq = redirectServer.takeRequest()
            // Headers must be stripped across differing origin ports
            assertNull("Authorization must be stripped on cross-origin redirect", redirectedReq.getHeader("Authorization"))
            assertNull("X-Emby-Authorization must be stripped on cross-origin redirect", redirectedReq.getHeader("X-Emby-Authorization"))
        } finally {
            redirectServer.shutdown()
        }
    }

    @Test
    fun credentialStoreEncryptAndDecryptRoundTrip() {
        val context = RuntimeEnvironment.getApplication()
        val testKey = javax.crypto.spec.SecretKeySpec("LeafTestKey12345678901234567890!".toByteArray(), "AES")
        val store = JellyfinCredentialStore(context, keyProvider = { testKey })
        store.clear()

        assertNull(store.loadAuth())

        val auth = JellyfinAuth(
            accessToken = "super-secret-token",
            userId = "user-1",
            serverId = "server-99",
            username = "leaf_reader",
            serverName = "My Media Server",
        )
        store.saveAuth(auth)

        val loaded = store.loadAuth()
        assertNotNull(loaded)
        assertEquals(auth.accessToken, loaded?.accessToken)
        assertEquals(auth.userId, loaded?.userId)
        assertEquals(auth.serverId, loaded?.serverId)
        assertEquals(auth.username, loaded?.username)
        assertEquals(auth.serverName, loaded?.serverName)

        store.clear()
        assertNull(store.loadAuth())
    }

    @Test
    fun credentialStoreFailsClosedWhenKeystoreUnavailable() {
        val context = RuntimeEnvironment.getApplication()
        // No keyProvider supplied: AndroidKeyStore provider is absent in standard JVM/Robolectric
        val store = JellyfinCredentialStore(context)
        store.clear()

        val auth = JellyfinAuth(
            accessToken = "super-secret-token",
            userId = "user-1",
            serverId = "server-99",
            username = "leaf_reader",
        )
        try {
            store.saveAuth(auth)
            fail("Expected exception when Keystore is unavailable")
        } catch (e: Exception) {
            // Must fail closed without writing unencrypted or fallback-encrypted data
            assertNull("Auth file must not be loadable after failure", store.loadAuth())
        }
    }

    @Test
    fun getItemsParsesUserDataPlaybackTicksAndPlayed() = runBlocking {
        val mockJson = """
            {
                "TotalRecordCount": 2,
                "Items": [
                    {
                        "Id": "item-read",
                        "Name": "Read Comic.cbz",
                        "IsFolder": false,
                        "Type": "Book",
                        "Container": "cbz",
                        "UserData": {
                            "PlaybackPositionTicks": 500000,
                            "Played": true
                        }
                    },
                    {
                        "Id": "item-in-progress",
                        "Name": "Reading Comic.cbz",
                        "IsFolder": false,
                        "Type": "Book",
                        "Container": "cbz",
                        "UserData": {
                            "PlaybackPositionTicks": 120000,
                            "Played": false
                        }
                    }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")
        val result = client.getItems(config, auth, parentId = "v1")

        assertEquals(2, result.items.size)
        assertTrue(result.items[0].played)
        assertEquals(500000L, result.items[0].playbackPositionTicks)

        assertFalse(result.items[1].played)
        assertEquals(120000L, result.items[1].playbackPositionTicks)
    }

    @Test
    fun progressStateWritesBothFieldsForTheAuthenticatedUser() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val config = JellyfinServerConfig(baseUrl(), true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")
        client.writeProgressState(config, auth, "item-xyz", 14, played = true, updatedAt = 1_700_000_000_123L)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/UserItems/item-xyz/UserData?userId=usr", request.path)
        assertTrue(request.getHeader("Authorization")!!.contains("tok"))
        val payload = org.json.JSONObject(request.body.readUtf8())
        assertEquals(3, payload.length())
        assertEquals("2023-11-14T22:13:20.123Z", payload.getString("LastPlayedDate"))
        assertEquals(140000L, payload.getLong("PlaybackPositionTicks"))
        assertTrue(payload.getBoolean("Played"))
    }

    @Test
    fun explicitUnreadWritesZeroPositionAndFalseTogether() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        client.writeProgressState(JellyfinServerConfig(baseUrl(), true),
            JellyfinAuth("tok", "usr-1", "srv", "Bryan"), "item-xyz", 0, played = false, updatedAt = 1_700_000_000_123L)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/UserItems/item-xyz/UserData?userId=usr-1", request.path)
        val payload = org.json.JSONObject(request.body.readUtf8())
        assertEquals(2, payload.length())
        assertEquals(0L, payload.getLong("PlaybackPositionTicks"))
        assertFalse(payload.getBoolean("Played"))
    }

    @Test
    fun failedStateWriteIsReportedToTheQueueCaller() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = runCatching { client.writeProgressState(JellyfinServerConfig(baseUrl(), true),
            JellyfinAuth("tok", "usr-1", "srv", "Bryan"), "item-xyz", 4, played = false, updatedAt = 1_700_000_000_123L) }
        assertTrue(result.isFailure)
        assertEquals("/UserItems/item-xyz/UserData?userId=usr-1", server.takeRequest().path)
    }
    @Test fun authoritativeProgressDoesNotCoerceUnknownOrMalformedValues() = runBlocking {
        val config = JellyfinServerConfig(baseUrl(), true)
        val auth = JellyfinAuth("token", "u", "srv", "Reader")
        for (userData in listOf("{}", "null", "{\"PlaybackPositionTicks\":null,\"Played\":null}",
            "{\"PlaybackPositionTicks\":\"0\",\"Played\":\"false\"}",
            "{\"PlaybackPositionTicks\":-1,\"Played\":false}")) {
            server.enqueue(MockResponse().setBody("{\"Id\":\"item\",\"UserData\":$userData}"))
            assertFalse(client.getProgress(config, auth, "item").isAvailable)
            assertTrue(server.takeRequest().getHeader("Authorization")!!.contains("token"))
        }
        server.enqueue(MockResponse().setBody("""{"Id":"item","UserData":{"PlaybackPositionTicks":0,"Played":false}}"""))
        assertEquals(JellyfinProgressSnapshot(0, false), client.getProgress(config, auth, "item"))
        server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"Id":"other","UserData":{"PlaybackPositionTicks":0,"Played":false}}"""))
        assertTrue(runCatching { client.getProgress(config, auth, "item") }.isFailure)
    }

    @Test fun progressPercentageUsesOnlyAvailableNumericServerValues() = runBlocking {
        val config = JellyfinServerConfig(baseUrl(), true)
        val auth = JellyfinAuth("token", "u", "srv", "Reader")
        for ((value, expected) in listOf("25.5" to 25.5, "0" to 0.0, "100" to 100.0,
            "null" to null, "\"25\"" to null, "-1" to null, "101" to null)) {
            server.enqueue(MockResponse().setBody("""{"Id":"item","UserData":{"PlaybackPositionTicks":20000,"Played":false,"PlayedPercentage":$value}}"""))
            assertEquals(expected, client.getProgress(config, auth, "item").playedPercentage)
            server.takeRequest()
        }
    }

}
