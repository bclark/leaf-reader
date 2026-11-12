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
    fun reportProgressSendsCorrectTicksPayload() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr", "srv", "Bryan")
        client.reportProgress(config, auth, itemId = "item-xyz", pageIndex = 14)

        val recorded = server.takeRequest()
        assertEquals("/Sessions/Playing/Progress", recorded.path)
        assertEquals("POST", recorded.method)
        val bodyStr = recorded.body.readUtf8()
        val json = org.json.JSONObject(bodyStr)
        assertEquals("item-xyz", json.getString("ItemId"))
        assertEquals(140000L, json.getLong("PositionTicks"))
        assertEquals(true, json.getBoolean("CanSeek"))
        assertEquals(false, json.getBoolean("IsPaused"))
        assertEquals("DirectPlay", json.getString("PlayMethod"))
    }

    @Test
    fun markPlayedSendsPostToPlayedItems() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr-1", "srv", "Bryan")
        client.markPlayedStatus(config, auth, itemId = "item-xyz", played = true)

        val recorded = server.takeRequest()
        assertEquals("/Users/usr-1/PlayedItems/item-xyz", recorded.path)
        assertEquals("POST", recorded.method)
    }

    @Test
    fun markUnplayedSendsDeleteToPlayedItems() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val config = JellyfinServerConfig(baseUrl = baseUrl(), allowInsecureHttp = true)
        val auth = JellyfinAuth("tok", "usr-1", "srv", "Bryan")
        client.markPlayedStatus(config, auth, itemId = "item-xyz", played = false)

        val recorded = server.takeRequest()
        assertEquals("/Users/usr-1/PlayedItems/item-xyz", recorded.path)
        assertEquals("DELETE", recorded.method)
    }
}
