package io.github.andrewmalitchuk.uncial.lang.download.core.provider

import io.github.andrewmalitchuk.uncial.core.source.log.LogLevel
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.lang.download.core.http.StubResponse
import io.github.andrewmalitchuk.uncial.lang.download.core.http.stubConnections
import io.github.andrewmalitchuk.uncial.lang.download.source.tessdata.TessDataSource
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import java.io.File
import java.net.HttpURLConnection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

private const val MODEL = "not a real traineddata, but neither is anything else in a test"

/**
 * The download provider's failure paths.
 *
 * Everything here runs against [stubConnections], so no test touches the network. The
 * cases worth having are the ones a happy-path download never reaches: one language of
 * several failing, cancellation arriving mid-download, a response that is too large to
 * buffer, and an install that dies after the destination has been touched.
 */
class JvmDownloadingLanguageDataProviderTest {

    private val cacheDirectory: File =
        File.createTempFile("uncial-download-test", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    private val tessData = File(cacheDirectory, "tessdata")

    @AfterTest
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    private class RecordingLogger : OcrLogger {
        val messages = mutableListOf<Pair<LogLevel, String>>()
        override fun log(level: LogLevel, message: String, throwable: Throwable?) {
            messages += level to message
        }
    }

    private val logger = RecordingLogger()

    /**
     * A provider whose downloads are canned.
     *
     * No checksums are pinned, so any bytes verify — these tests are about what happens
     * around verification, and `DownloadVerificationTest` already covers verification
     * itself.
     */
    private fun provider(
        maxBytes: Long = 64L * 1024,
        route: (String) -> StubResponse,
    ) = JvmDownloadingLanguageDataProvider(
        source = TessDataSource(checksums = emptyMap()),
        cacheDirectory = cacheDirectory,
        logger = logger,
        maxBytes = maxBytes,
        openConnection = stubConnections(route),
    )

    private fun ok(text: String = MODEL) = StubResponse(body = text.encodeToByteArray())

    private fun notFound() = StubResponse(status = HttpURLConnection.HTTP_NOT_FOUND)

    @Test
    fun `one language failing does not cost the caller the ones that worked`() = runTest {
        // The contract is to throw only when NONE of the languages can be materialized.
        // English is asked for first and 404s; Ukrainian must still be fetched.
        val provider = provider { url -> if ("eng" in url) notFound() else ok() }

        val path = provider.materialize(listOf(OcrLanguage.English, OcrLanguage.Ukrainian))

        assertEquals(cacheDirectory.absolutePath, path)
        assertTrue(File(tessData, "ukr.traineddata").isFile, "ukr should have been downloaded")
        assertFalse(File(tessData, "eng.traineddata").exists(), "eng 404'd")
        assertTrue(
            logger.messages.any { it.first == LogLevel.Warning && "eng" in it.second },
            "the failure that was survived should still be reported: ${logger.messages}",
        )
    }

    @Test
    fun `materialize fails only when every language failed and says why`() = runTest {
        val provider = provider { notFound() }

        val error = assertFailsWith<OcrError.NoLanguageData> {
            provider.materialize(listOf(OcrLanguage.English, OcrLanguage.Ukrainian))
        }

        assertEquals(listOf(OcrLanguage.English, OcrLanguage.Ukrainian), error.languages)
        // The last per-language failure is kept as the cause; without it the thrown error
        // says "no language data" and nothing about the 404 that caused it.
        assertTrue("HTTP 404" in (error.cause?.cause?.message ?: ""), "cause: ${error.cause}")
    }

    @Test
    fun `cancellation propagates instead of being counted as a failed language`() = runTest {
        // A caller that gives up must not be told its languages are missing -- and must
        // not be left believing the cancellation took effect while the download rumbles on.
        //
        // The cancellation has to come from inside the per-language try/catch to exercise
        // the rethrow at all, so it is the connection opener that throws. One language
        // only, so that a swallowed cancellation cannot hide: it would leave the filter
        // empty and surface as NoLanguageData instead.
        val provider = provider { throw CancellationException("the caller gave up") }

        assertFailsWith<CancellationException> {
            provider.materialize(listOf(OcrLanguage.Ukrainian))
        }
    }

    @Test
    fun `prefetch propagates cancellation rather than logging it as a failure`() = runTest {
        // prefetch swallows everything -- it is best-effort -- which is exactly why the
        // one exception it must not swallow needs pinning.
        val provider = provider { throw CancellationException("the caller gave up") }

        assertFailsWith<CancellationException> {
            provider.prefetch(listOf(OcrLanguage.Ukrainian))
        }
        assertTrue(
            logger.messages.none { it.first == LogLevel.Warning },
            "cancellation was reported as a prefetch failure: ${logger.messages}",
        )
    }

    @Test
    fun `a response that admits to being too large is refused`() = runTest {
        val provider = provider(maxBytes = 1024) {
            StubResponse(body = ByteArray(4096))
        }

        val error = assertFailsWith<OcrError.NoLanguageData> {
            provider.materialize(listOf(OcrLanguage.Ukrainian))
        }
        assertTrue("Content-Length" in (error.cause?.cause?.message ?: ""), "cause: ${error.cause}")
    }

    @Test
    fun `a lying Content-Length does not get past the cap either`() = runTest {
        // Content-Length is whatever the far end chose to say. The bytes are buffered in
        // memory before the checksum can look at them, so the limit has to hold while
        // reading as well.
        val provider = provider(maxBytes = 1024) {
            StubResponse(body = ByteArray(64 * 1024), advertised = 12L)
        }

        assertFailsWith<OcrError.NoLanguageData> {
            provider.materialize(listOf(OcrLanguage.Ukrainian))
        }
        assertFalse(File(tessData, "ukr.traineddata").exists(), "nothing should reach the cache")
    }

    @Test
    fun `a body of exactly the cap is kept and one byte more is refused`() = runTest {
        // The cap is a limit, not an approximation: the other size tests overshoot it by
        // a factor of four or more, which would pass just as happily against an
        // off-by-one comparison. Content-Length says nothing here (-1, as a chunked
        // response does), so it is readAtMost's boundary being pinned and not fetch's.
        val cap = 1024L

        val atTheCap = provider(maxBytes = cap) {
            StubResponse(body = ByteArray(cap.toInt()), advertised = -1L)
        }
        atTheCap.materialize(listOf(OcrLanguage.Ukrainian))
        assertEquals(cap, File(tessData, "ukr.traineddata").length())

        File(tessData, "ukr.traineddata").delete()
        val overTheCap = provider(maxBytes = cap) {
            StubResponse(body = ByteArray(cap.toInt() + 1), advertised = -1L)
        }
        assertFailsWith<OcrError.NoLanguageData> {
            overTheCap.materialize(listOf(OcrLanguage.Ukrainian))
        }
        assertFalse(File(tessData, "ukr.traineddata").exists(), "nothing should reach the cache")
    }

    @Test
    fun `a failed install cleans up its partial file`() = runTest {
        // A directory where the model should go: the rename fails, and so does the copy
        // that stands in for it.
        tessData.mkdirs()
        File(tessData, "ukr.traineddata").mkdirs()
        File(tessData, "ukr.traineddata/occupied").writeText("in the way")
        val provider = provider { ok() }

        assertFailsWith<OcrError.NoLanguageData> {
            provider.materialize(listOf(OcrLanguage.Ukrainian))
        }
        assertFalse(File(tessData, "ukr.traineddata.part").exists(), "the .part file leaked")
    }

    @Test
    fun `a failed install does not leave the destination behind`() = runTest {
        // The copy fallback is not atomic: a failure part-way through it leaves a
        // truncated model that isUsable() -- "a file, and not empty" -- would accept on
        // the next run. Whatever the destination holds when an install fails, it goes.
        // Modelled by making the .part path unwritable, which is the only way to fail the
        // install deterministically on every filesystem.
        tessData.mkdirs()
        val target = File(tessData, "ukr.traineddata")
        target.writeText("")
        File(tessData, "ukr.traineddata.part").mkdirs()
        val provider = provider { ok() }

        assertFailsWith<OcrError.NoLanguageData> {
            provider.materialize(listOf(OcrLanguage.Ukrainian))
        }
        assertFalse(target.exists(), "the destination survived a failed install")
    }

    @Test
    fun `clearCache removes half-downloaded models and leaves everything else alone`() =
        runTest {
            tessData.mkdirs()
            File(tessData, "ukr.traineddata").writeText(MODEL)
            File(tessData, "eng.traineddata.part").writeText("half a model")
            File(tessData, "readme.txt").writeText("not ours to delete")

            assertEquals(2, provider { ok() }.clearCache())

            assertFalse(File(tessData, "ukr.traineddata").exists())
            assertFalse(File(tessData, "eng.traineddata.part").exists())
            assertTrue(File(tessData, "readme.txt").exists(), "unrelated files must survive")
        }
}
