package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinModelsTest {

    @Test
    fun urlNormalizationPreservesReverseProxyAndStripsTrailingSlash() {
        assertEquals(
            "https://myhost.example.com/jellyfin",
            JellyfinServerConfig.normalizeUrl("https://myhost.example.com/jellyfin/"),
        )
        assertEquals(
            "http://192.168.1.100:8096",
            JellyfinServerConfig.normalizeUrl("192.168.1.100:8096"),
        )
        assertEquals(
            "https://server.example.com",
            JellyfinServerConfig.normalizeUrl("server.example.com/"),
        )
    }

    @Test
    fun localNetworkHostDetection() {
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("localhost"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("127.0.0.1"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("::1"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("192.168.1.50"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("10.0.1.2"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("172.16.0.10"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("172.31.255.254"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("tower.local"))
        assertTrue(JellyfinServerConfig.isLocalNetworkHost("nas.home.arpa"))

        assertFalse(JellyfinServerConfig.isLocalNetworkHost("example.com"))
        assertFalse(JellyfinServerConfig.isLocalNetworkHost("8.8.8.8"))
        assertFalse(JellyfinServerConfig.isLocalNetworkHost("172.32.0.1"))
    }

    @Test
    fun httpRejectedForRemoteHost() {
        assertFailsWith<IllegalArgumentException> {
            JellyfinServerConfig(
                baseUrl = "http://example.com:8096",
                allowInsecureHttp = true,
            )
        }
    }

    @Test
    fun httpRequiresExplicitConfirmationOnLocalHost() {
        assertFailsWith<IllegalArgumentException> {
            JellyfinServerConfig(
                baseUrl = "http://192.168.1.100:8096",
                allowInsecureHttp = false,
            )
        }

        val config = JellyfinServerConfig(
            baseUrl = "http://192.168.1.100:8096",
            allowInsecureHttp = true,
        )
        assertEquals("http://192.168.1.100:8096", config.baseUrl)
        assertFalse(config.isHttps)
        assertEquals("192.168.1.100", config.host)
    }

    @Test
    fun rawUrlWithoutSchemeNormalizesBaseUrl() {
        val config = JellyfinServerConfig(
            baseUrl = "192.168.1.100:8096",
            allowInsecureHttp = true,
        )
        assertEquals("http://192.168.1.100:8096", config.baseUrl)
        assertEquals("192.168.1.100", config.host)
        assertFalse(config.isHttps)
    }

    @Test
    fun httpsAllowedForAnyValidHost() {
        val config = JellyfinServerConfig(
            baseUrl = "https://jellyfin.example.com",
            allowInsecureHttp = false,
        )
        assertTrue(config.isHttps)
        assertEquals("jellyfin.example.com", config.host)
    }

    @Test
    fun itemImportabilityRules() {
        val folder = JellyfinItem(id = "1", name = "Batman", isFolder = true)
        assertFalse(folder.isImportable)

        val cbz = JellyfinItem(id = "2", name = "Issue #1", isFolder = false, container = "cbz")
        assertTrue(cbz.isImportable)

        val cbr = JellyfinItem(id = "3", name = "Issue #2", isFolder = false, container = "cbr")
        assertTrue(cbr.isImportable)

        val pdf = JellyfinItem(id = "4", name = "Artbook.pdf", isFolder = false)
        assertTrue(pdf.isImportable)

        // EPUB is explicitly NOT supported by the offline reader
        val epub = JellyfinItem(id = "5", name = "Novel.epub", isFolder = false, container = "epub")
        assertFalse(epub.isImportable)

        val mkv = JellyfinItem(id = "6", name = "Movie.mkv", isFolder = false, container = "mkv")
        assertFalse(mkv.isImportable)
    }

    @Test
    fun authHeaderFormatting() {
        val header = JellyfinEndpoints.authHeader(
            clientName = "Leaf",
            deviceName = "Pixel 8",
            deviceId = "dev-123",
            version = "0.3.7",
            token = "tok-abc",
        )
        assertEquals(
            "MediaBrowser Client=\"Leaf\", Device=\"Pixel 8\", DeviceId=\"dev-123\", Version=\"0.3.7\", Token=\"tok-abc\"",
            header,
        )

        val loginHeader = JellyfinEndpoints.authHeader(
            clientName = "Leaf",
            deviceName = "Pixel 8",
            deviceId = "dev-123",
            version = "0.3.7",
            token = null,
        )
        assertEquals(
            "MediaBrowser Client=\"Leaf\", Device=\"Pixel 8\", DeviceId=\"dev-123\", Version=\"0.3.7\"",
            loginHeader,
        )
    }

    @Test
    fun progressTickConversions() {
        assertEquals(0L, JellyfinProgress.pageToTicks(0))
        assertEquals(10_000L, JellyfinProgress.pageToTicks(1))
        assertEquals(50_000L, JellyfinProgress.pageToTicks(5))

        assertEquals(0, JellyfinProgress.ticksToPage(0L))
        assertEquals(1, JellyfinProgress.ticksToPage(10_000L))
        assertEquals(5, JellyfinProgress.ticksToPage(50_000L))
        assertEquals(5, JellyfinProgress.ticksToPage(59_999L))

        // Clamping to maxPages
        assertEquals(23, JellyfinProgress.ticksToPage(300_000L, maxPages = 24))
        assertEquals(0, JellyfinProgress.ticksToPage(-10_000L, maxPages = 24))
    }
}
