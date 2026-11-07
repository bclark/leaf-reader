package net.digitalbc.leafreader.data.jellyfin

import kotlinx.coroutines.CancellationException
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
    fun credentialStoreEncryptAndDecryptRoundTrip() {
        val context = RuntimeEnvironment.getApplication()
        val store = JellyfinCredentialStore(context)
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
}
