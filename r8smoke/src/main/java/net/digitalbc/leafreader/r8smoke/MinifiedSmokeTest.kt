package net.digitalbc.leafreader.r8smoke

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the real optimized APK. No app dependency, keep rules, credentials or data reset. */
@RunWith(AndroidJUnit4::class)
class MinifiedSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.context.createPackageContext(
        "net.digitalbc.leafreader",
        android.content.Context.CONTEXT_INCLUDE_CODE or android.content.Context.CONTEXT_IGNORE_SECURITY,
    )
    private val probes = JSONObject(instrumentation.context.assets.open("r8-probes.json")
        .bufferedReader().use { it.readText() })

    @Test fun annotationDiscoveredSmbCallbacksSurviveMinification() {
        assertEquals("net.digitalbc.leafreader", target.packageName)
        assertEquals(0, target.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val handlers = probes.getJSONArray("handlers")
        assertEquals(3, handlers.length())
        for (index in 0 until handlers.length()) {
            val handler = handlers.getJSONObject(index)
            val owner = target.classLoader.loadClass(handler.getString("owner"))
            val event = target.classLoader.loadClass(handler.getString("event"))
            val method = owner.getDeclaredMethod(handler.getString("method"), event)
            assertTrue("Runtime handler annotation missing on $method", method.declaredAnnotations.any {
                it.annotationClass.java.name == probes.getString("annotation")
            })
        }
    }

    @Test fun nativeArchiveCallbacksWorkInFullAndAreAbsentInFdroid() {
        val loader = target.classLoader
        if (!probes.getBoolean("sevenzip")) {
            assertThrows(ClassNotFoundException::class.java) { loader.loadClass("net.sf.sevenzipjbinding.SevenZip") }
            return
        }
        val file = java.io.File.createTempFile("r8-jni-", ".zip", instrumentation.context.cacheDir)
        val expected = "Leaf native callback fixture".toByteArray()
        try {
            java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("page1.jpg"))
                zip.write(expected)
                zip.closeEntry()
            }
            assertArchiveExtracts(file, expected)
            instrumentation.context.assets.open("rar4.rar").use { input -> file.outputStream().use { input.copyTo(it) } }
            assertArchiveExtracts(file, "test text document\r\n".toByteArray())
            instrumentation.context.assets.open("rar5.rar").use { input -> file.outputStream().use { input.copyTo(it) } }
            val rar5 = java.nio.ByteBuffer.allocate(1200).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (k in 1..300) rar5.putInt((k * k - 3 * k + 1).coerceAtLeast(0))
            assertArchiveExtracts(file, rar5.array())
        } finally {
            assertTrue("Temporary archive leaked", file.delete())
        }
    }

    private fun assertArchiveExtracts(file: java.io.File, expected: ByteArray) {
        val loader = target.classLoader
        java.io.RandomAccessFile(file, "r").use { input ->
            val stream = loader.loadClass("net.sf.sevenzipjbinding.impl.RandomAccessFileInStream")
                .getConstructor(java.io.RandomAccessFile::class.java).newInstance(input)
            val sevenzip = loader.loadClass("net.sf.sevenzipjbinding.SevenZip")
            val archive = sevenzip.getMethod("openInArchive",
                loader.loadClass("net.sf.sevenzipjbinding.ArchiveFormat"),
                loader.loadClass("net.sf.sevenzipjbinding.IInStream")).invoke(null, null, stream)
            val api = loader.loadClass("net.sf.sevenzipjbinding.IInArchive")
            try {
                assertTrue((api.getMethod("getNumberOfItems").invoke(archive) as Int) > 0)
                val bytes = java.io.ByteArrayOutputStream()
                val outputType = loader.loadClass("net.sf.sevenzipjbinding.ISequentialOutStream")
                val output = java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(outputType)) { _, method, args ->
                    check(method.name == "write")
                    val chunk = args!![0] as ByteArray
                    bytes.write(chunk)
                    chunk.size
                }
                val result = requireNotNull(api.getMethod("extractSlow", Int::class.javaPrimitiveType, outputType)
                    .invoke(archive, 0, output))
                assertEquals("OK", result.toString())
                assertArrayEquals(expected, bytes.toByteArray())
            } finally {
                api.getMethod("close").invoke(archive)
            }
        }
    }

    @Test fun libraryLaunchesInTheMinifiedApp() {
        val launch = requireNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName))
        target.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Leaf did not display a window", device.wait(Until.hasObject(By.pkg(target.packageName)), 15_000))
        assertFalse("Backup recovery blocked library startup", device.hasObject(By.text("Finish restoring Leaf")))
        assertTrue("Library did not finish composing", device.wait(Until.hasObject(By.desc("Menu")), 15_000))
    }
}
