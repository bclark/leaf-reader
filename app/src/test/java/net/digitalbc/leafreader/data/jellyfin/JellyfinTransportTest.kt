package net.digitalbc.leafreader.data.jellyfin

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class JellyfinTransportTest {
    private lateinit var server: MockWebServer
    private val auth = JellyfinAuth("synthetic-token", "user", "server", "Reader")

    @Before fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After fun teardown() { server.shutdown() }

    private fun config(host: String = "127.0.0.1") = JellyfinServerConfig(
        server.url("/").newBuilder().host(host).build().toString(), true)

    private fun client(builder: OkHttpClient.Builder = OkHttpClient.Builder()) =
        JellyfinClient("transport-test", customHttpClient = builder
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build())

    private fun emptyViews() = MockResponse().setBody("""{"Items":[]}""")

    private suspend fun failure(action: suspend () -> Unit): JellyfinException {
        try { action() } catch (e: JellyfinException) { return e }
        throw AssertionError("Expected transport rejection")
    }

    private fun address(ip: String) = InetAddress.getByName(ip)

    private fun dns(resolve: (String) -> List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String) = resolve(hostname)
    }

    @Test fun localNameResolvingPublicIsRejectedBeforeAnyConnection() = runBlocking {
        val connects = AtomicInteger()
        val lookups = AtomicInteger()
        val c = client(OkHttpClient.Builder()
            .dns(dns { lookups.incrementAndGet(); listOf(address("203.0.113.1")) })
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    connects.incrementAndGet()
                }
            }))
        val error = failure { c.authenticate(config("reader.local"), "reader", "synthetic-password") }
        assertEquals(JellyfinFailureKind.INVALID_CONFIGURATION, error.kind)
        assertEquals(1, lookups.get())
        assertEquals(0, connects.get())
        assertEquals(0, server.requestCount)
    }

    @Test fun mixedLocalAndPublicDnsResultsAreRejectedBeforeFallbackCanConnect() = runBlocking {
        val connects = AtomicInteger()
        val c = client(OkHttpClient.Builder()
            .dns(dns { listOf(address("127.0.0.1"), address("203.0.113.1")) })
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    connects.incrementAndGet()
                }
            }))
        assertEquals(JellyfinFailureKind.INVALID_CONFIGURATION,
            failure { c.getViews(config("reader.lan"), auth) }.kind)
        assertEquals(0, connects.get())
        assertEquals(0, server.requestCount)
    }

    @Test fun connectionUsesTheValidatedDnsResultWithoutASecondLookup() = runBlocking {
        val lookups = AtomicInteger()
        val c = client(OkHttpClient.Builder().dns(dns {
            listOf(address(if (lookups.incrementAndGet() == 1) "127.0.0.1" else "203.0.113.1"))
        }))
        server.enqueue(emptyViews())
        assertTrue(c.getViews(config("reader.home.arpa"), auth).isEmpty())
        assertEquals(1, lookups.get())
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(request.getHeader("Authorization")!!.contains("synthetic-token"))
        assertTrue(request.getHeader("Host")!!.startsWith("reader.home.arpa:"))
    }

    @Test fun resolvedAddressPolicyIncludesPrivateV6ButRejectsUnspecifiedAndMulticast() {
        for (ip in listOf("127.0.0.1", "10.1.2.3", "172.16.1.1", "192.168.2.2",
                "169.254.1.2", "::1", "fe80::1", "fc00::1", "fd12::1")) {
            val expected = listOf(address(ip))
            assertEquals(ip, expected, LocalNetworkDns(dns { expected }).lookup("reader.local"))
        }
        for (ip in listOf("0.0.0.0", "203.0.113.1", "224.0.0.1", "::", "ff02::1", "2001:db8::1")) {
            try {
                LocalNetworkDns(dns { listOf(address(ip)) }).lookup("reader.local")
                fail("Accepted nonlocal/unspecified destination "+ip)
            } catch (e: JellyfinException) {
                assertEquals(ip, JellyfinFailureKind.INVALID_CONFIGURATION, e.kind)
            }
        }
    }

    @Test fun cleartextRequestDoesNotSendCredentialsThroughConfiguredHttpProxy() = runBlocking {
        val proxyServer = MockWebServer()
        proxyServer.start()
        try {
            server.enqueue(emptyViews())
            proxyServer.enqueue(MockResponse().setResponseCode(500))
            val c = client(OkHttpClient.Builder().proxy(
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyServer.port))))
            assertTrue(c.getViews(config(), auth).isEmpty())
            assertEquals(1, server.requestCount)
            assertEquals(0, proxyServer.requestCount)
        } finally { proxyServer.shutdown() }
    }

    @Test fun relativeSameOriginRedirectsRetainAuthorization() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/second"))
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/last"))
        server.enqueue(emptyViews())
        assertTrue(client().getViews(config(), auth).isEmpty())
        val requests = (1..3).map { server.takeRequest(5, TimeUnit.SECONDS)!! }
        assertEquals(listOf("/Users/user/Views", "/second", "/last"), requests.map { it.path })
        for (request in requests) {
            assertTrue(request.getHeader("Authorization")!!.contains("synthetic-token"))
            assertNotNull(request.getHeader("X-Emby-Authorization"))
        }
    }

    @Test fun crossPortRedirectReturningToOriginalOriginDoesNotRestoreCredentials() = runBlocking {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/middle")))
            other.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/last")))
            server.enqueue(emptyViews())
            assertTrue(client().getViews(config(), auth).isEmpty())
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            for (request in listOf(other.takeRequest(5, TimeUnit.SECONDS)!!,
                    server.takeRequest(5, TimeUnit.SECONDS)!!)) {
                assertNull(request.getHeader("Authorization"))
                assertNull(request.getHeader("X-Emby-Authorization"))
                assertNull(request.getHeader("X-MediaBrowser-Token"))
            }
        } finally { other.shutdown() }
    }

    @Test fun redirectToLocalNameWithPublicDnsIsRejectedBeforeSecondConnection() = runBlocking {
        val connects = AtomicInteger()
        val c = client(OkHttpClient.Builder()
            .dns(dns { listOf(address("203.0.113.1")) })
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    connects.incrementAndGet()
                }
            }))
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", "http://blocked.local/target"))
        assertEquals(JellyfinFailureKind.INVALID_CONFIGURATION,
            failure { c.getViews(config(), auth) }.kind)
        assertEquals(1, connects.get())
        assertEquals(1, server.requestCount)
    }

    @Test fun publicIpRedirectIsRejectedBeforeAnotherConnection() = runBlocking {
        val connects = AtomicInteger()
        val c = client(OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                connects.incrementAndGet()
            }
        }))
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", "http://203.0.113.1/target"))
        assertEquals(JellyfinFailureKind.INVALID_CONFIGURATION,
            failure { c.getViews(config(), auth) }.kind)
        assertEquals(1, connects.get())
        assertEquals(1, server.requestCount)
    }

    @Test fun httpsStillUsesConfiguredProxyWithoutSendingHttpOriginCredentials() = runBlocking {
        val proxyServer = MockWebServer()
        proxyServer.start()
        try {
            // Reject the tunnel: test proxy selection, not a successful TLS handshake.
            proxyServer.enqueue(MockResponse().setResponseCode(502))
            val c = client(OkHttpClient.Builder().proxy(
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyServer.port))))
            failure { c.getViews(JellyfinServerConfig("https://reader.example"), auth) }
            val tunnel = proxyServer.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("CONNECT reader.example:443 HTTP/1.1", tunnel.requestLine)
            assertNull(tunnel.getHeader("Authorization"))
            assertNull(tunnel.getHeader("X-Emby-Authorization"))
            assertEquals(0, server.requestCount)
        } finally { proxyServer.shutdown() }
    }

    @Test fun redirectLimitStopsBeforeTheFourthHop() = runBlocking {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again"))
        }
        assertEquals(JellyfinFailureKind.INVALID_CONFIGURATION,
            failure { client().getViews(config(), auth) }.kind)
        assertEquals(4, server.requestCount) // Initial request plus three permitted hops.
    }

    @Test fun customClientCannotAutomaticallyFollowAuthenticationPostRedirect() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/not-allowed"))
        server.enqueue(emptyViews())
        val c = client(OkHttpClient.Builder().followRedirects(true).followSslRedirects(true))
        assertEquals(JellyfinFailureKind.AUTHENTICATION,
            failure { c.authenticate(config(), "reader", "synthetic-password") }.kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun httpsDowngradeIsRejectedBeforeAnHttpRequest() = runBlocking {
        // Synthetic HTTPS response tests hop policy; this is not a TLS-handshake test.
        val c = client(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(302).message("Found").header("Location", server.url("/downgrade").toString())
                .body("redirect".toResponseBody()).build()
        })
        assertEquals(JellyfinFailureKind.INVALID_CONFIGURATION,
            failure { c.getViews(JellyfinServerConfig("https://reader.example"), auth) }.kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun httpsPublicHostIsNotSubjectToCleartextHostnamePolicy() = runBlocking {
        // Synthetic terminal response: prove cleartext hostname policy is not applied to HTTPS.
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9))
        val c = client(OkHttpClient.Builder().proxy(proxy).addInterceptor { chain ->
            assertEquals("https", chain.request().url.scheme)
            assertEquals("reader.example", chain.request().url.host)
            assertTrue(chain.request().header("Authorization")!!.contains("synthetic-token"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("""{"Items":[]}""".toResponseBody()).build()
        })
        assertTrue(c.getViews(JellyfinServerConfig("https://reader.example"), auth).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellationDuringRedirectedResponseCancelsTheCurrentCall() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val c = client(OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callFailed(call: Call, ioe: java.io.IOException) {
                if (call.isCanceled()) cancelled.complete(Unit)
            }
        }))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/held"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) { c.getViews(config(), auth) }
        try {
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/held", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            withTimeout(5_000) { job.cancelAndJoin(); cancelled.await() }
            assertTrue(job.isCancelled)
            assertEquals(2, server.requestCount)
        } finally { job.cancelAndJoin() }
    }
}
