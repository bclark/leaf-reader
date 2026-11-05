package net.digitalbc.leafreader.data.smb

import kotlin.test.*

class SmbPolicyTest {
    @Test fun savedAndroidSettingsAndIpv6ParseWithoutSecrets() {
        val config = SmbConnectionConfig.fromInputs("smb://nas.local:1445/", "Comics/漫画/Volume 1", "DOMAIN\\reader")
        assertEquals("nas.local", config.host)
        assertEquals(1445, config.port)
        assertEquals("Comics", config.share)
        assertEquals("漫画/Volume 1", config.basePath)
        assertEquals("reader", config.username)
        assertEquals("DOMAIN", config.domain)
        assertEquals("2001:db8::1", SmbConnectionConfig.fromInputs("[2001:db8::1]:445", "Comics", "").host)
        assertEquals(445, SmbConnectionConfig.fromInputs("nas", "Comics", "").port)
        for (host in listOf("", "nas:", "nas:65536", "nas:no", "2001:db8::1", "nas/path", "user@nas")) {
            assertFailsWith<IllegalArgumentException> { SmbConnectionConfig.fromInputs(host, "Comics", "") }
        }
        assertFailsWith<IllegalArgumentException> { SmbConnectionConfig.fromInputs("nas", "Comics/../private", "") }
    }

    @Test fun relativePathsCannotEscapeRootAndPreserveUnicode() {
        assertEquals("漫画/Issue 2.cbz", SmbPaths.normalize("漫画\\./Issue 2.cbz"))
        assertEquals("base/漫画/Issue 2.cbz", SmbPaths.join("base", "漫画/Issue 2.cbz"))
        assertEquals("漫画", SmbPaths.parent("漫画/Issue 2.cbz"))
        assertEquals("", SmbPaths.parent(""))
        for (path in listOf("../x", "folder/../../x", "/absolute", "\\absolute", "C:/file", "a\u0000b", ".. /private", "folder/.../private")) {
            assertFailsWith<IllegalArgumentException> { SmbPaths.normalize(path) }
        }
        for (name in listOf("..", "../x.cbz", "a/b.cbz", "a\\b.cbz")) {
            assertFailsWith<IllegalArgumentException> { SmbPaths.child("", name) }
        }
    }

    @Test fun listingFiltersAndSortsDirectoriesThenNaturalNames() {
        fun entry(name: String, dir: Boolean = false) = SmbEntry(name, "漫画/$name", dir)
        val entries = listOf(entry("issue10.cbz"), entry("issue2.CBZ"), entry(".hidden"),
            entry("notes.txt"), entry("folder"), entry("Volume 10", true), entry("Volume 2", true),
            entry("../escape.cbz"), SmbEntry("foreign.cbz", "elsewhere/foreign.cbz", false),
            entry("issue2.CBZ"), entry("comic.pdf"), entry("book.rar"))
        val result = SmbListing.prepare("漫画", entries)
        assertEquals(listOf("Volume 2", "Volume 10", "book.rar", "comic.pdf", "issue2.CBZ", "issue10.cbz"),
            result.map { it.name })
        assertTrue(SmbListing.isImportable("book.ZIP"))
        assertFalse(SmbListing.isImportable(".hidden.cbz"))
    }

    @Test fun staleListingAndCancellationCannotReplaceCurrentDirectory() {
        val first = SmbBrowserLogic.begin(SmbBrowserState.empty(), "first")
        val second = SmbBrowserLogic.begin(first, "second")
        assertEquals(second, SmbBrowserLogic.complete(second, first.generation, emptyList()))
        val shown = SmbBrowserLogic.complete(second, second.generation, listOf(SmbEntry("book.cbz", "second/book.cbz", false)))
        val selected = SmbBrowserLogic.toggle(shown, "second/book.cbz")
        val pending = SmbBrowserLogic.begin(selected, "denied")
        val failed = SmbBrowserLogic.fail(pending, pending.generation, SmbFailureKind.ACCESS_DENIED)
        assertEquals("second", failed.path)
        assertEquals(selected.entries, failed.entries)
        assertEquals(selected.selectedPaths, failed.selectedPaths)
        val cancelled = SmbBrowserLogic.cancel(pending)
        assertEquals(cancelled, SmbBrowserLogic.complete(cancelled, pending.generation, emptyList()))
        assertEquals(cancelled, SmbBrowserLogic.fail(cancelled, pending.generation, SmbFailureKind.IO))
        val reset = SmbBrowserLogic.reset(pending)
        assertTrue(reset.entries.isEmpty())
        assertTrue(reset.selectedPaths.isEmpty())
        assertEquals("", reset.path)
        assertEquals(reset, SmbBrowserLogic.complete(reset, pending.generation, emptyList()))
    }

    @Test fun selectionOnlyContainsVisibleFilesAndClearsOnSuccessfulNavigation() {
        val request = SmbBrowserLogic.begin(SmbBrowserState.empty(), "")
        val shown = SmbBrowserLogic.complete(request, request.generation,
            listOf(SmbEntry("folder", "folder", true), SmbEntry("book.cbz", "book.cbz", false)))
        assertEquals(shown, SmbBrowserLogic.toggle(shown, "folder"))
        assertEquals(shown, SmbBrowserLogic.toggle(shown, "foreign.cbz"))
        val selected = SmbBrowserLogic.selectAll(shown)
        assertEquals(setOf("book.cbz"), selected.selectedPaths)
        val pending = SmbBrowserLogic.begin(selected, "folder")
        assertEquals(pending, SmbBrowserLogic.toggle(pending, "book.cbz"))
        assertTrue(SmbBrowserLogic.complete(pending, pending.generation, emptyList()).selectedPaths.isEmpty())
    }

    @Test fun unknownTotalsAreIndeterminateAndDownloadIsNotImportCompletion() {
        val unknown = SmbTransferProgress(100, 0, 25)
        assertTrue(unknown.isIndeterminate)
        assertNull(SmbImportProgress.fraction(0, 1, unknown, SmbImportPhase.DOWNLOADING))
        val finishedDownload = SmbTransferProgress(200, 200, 25)
        assertEquals(1f, finishedDownload.fraction)
        assertEquals(0.95f, SmbImportProgress.fraction(0, 1, finishedDownload, SmbImportPhase.DOWNLOADING))
        assertEquals(1f, SmbImportProgress.fraction(1, 1, finishedDownload, SmbImportPhase.COMPLETE))
        assertEquals(1f, SmbTransferProgress(300, 200, 0).fraction)
        assertEquals(0f, SmbTransferProgress(-1, 200, 0).fraction)
    }
}
