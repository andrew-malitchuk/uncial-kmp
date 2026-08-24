package io.github.andrewmalitchuk.uncial.runtime.source.client

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.engine.fake.source.engine.FakeOcrEngine
import io.github.andrewmalitchuk.uncial.engine.fake.source.raster.FakePageRasterizer
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

private val NO_BYTES = ByteArray(0)

class UncialClientTest {

    @Test
    fun `extract returns the recognized document`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine(text = mapOf(0 to "Розділ перший", 1 to "Тіло тексту"))
            rasterizer = FakePageRasterizer(pageCount = 2)
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(2, document.pageCount)
        assertEquals("Розділ перший", document.pages[0].text)
        assertEquals("Тіло тексту", document.pages[1].text)
        assertEquals(ExtractionSource.Ocr, document.source)
        client.close()
    }

    @Test
    fun `capabilities come from the engine and report null when none is available`() = runTest {
        val withEngine = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer()
        }
        assertEquals(FakeOcrEngine.FAKE_ENGINE_ID, assertNotNull(withEngine.capabilities).engineId)
        assertTrue(withEngine.isAvailable)

        val unavailable = UncialClient {
            engine = FakeOcrEngine(available = false)
            rasterizer = FakePageRasterizer()
        }
        assertNull(unavailable.capabilities)
        assertTrue(!unavailable.isAvailable)
    }

    @Test
    fun `flow reports every page in order and finishes with the document`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 3)
        }
        val events = client.extractAsFlow(NO_BYTES).toList()

        val started = assertIs<OcrProgress.Started>(events.first())
        assertEquals(3, started.pageCount)
        assertEquals(ExtractionSource.Ocr, started.source)

        val pages = events.filterIsInstance<OcrProgress.Page>()
        assertEquals(listOf(0, 1, 2), pages.map { it.index })
        assertEquals(listOf(1, 2, 3), pages.map { it.completed })
        assertTrue(pages.all { it.of == 3 })
        assertEquals(1f, pages.last().fraction)

        assertEquals(3, assertIs<OcrProgress.Done>(events.last()).document.pageCount)
        client.close()
    }

    @Test
    fun `cancellation propagates instead of becoming a failed Result`() = runTest {
        // The bug this guards: kotlin.runCatching catches CancellationException, which would
        // turn "my caller cancelled me" into "the extraction failed normally" and leave the
        // caller's scope believing the work completed.
        val client = UncialClient {
            engine = FakeOcrEngine(delayPerPageMillis = 1_000)
            rasterizer = FakePageRasterizer(pageCount = 100)
        }
        assertFailsWith<CancellationException> {
            withTimeout(2_500) { client.extract(NO_BYTES) }
        }
        client.close()
    }

    @Test
    fun `an engine failure arrives as a typed OcrError in the Result`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine(failOnPage = 1)
            rasterizer = FakePageRasterizer(pageCount = 3)
        }
        val error = client.extract(NO_BYTES).exceptionOrNull()
        assertEquals(1, assertIs<OcrError.RecognitionFailed>(error).pageIndex)
        client.close()
    }

    @Test
    fun `a rasterizer failure arrives as RenderFailed`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 3, failOnPage = 2)
        }
        val error = client.extract(NO_BYTES).exceptionOrNull()
        assertEquals(2, assertIs<OcrError.RenderFailed>(error).pageIndex)
        client.close()
    }

    @Test
    fun `no available engine fails with Unsupported rather than throwing at build time`() =
        runTest {
            // Construction must never throw: clients get built in DI graphs and initializers.
            val client = UncialClient {
                engine = FakeOcrEngine(available = false)
                rasterizer = FakePageRasterizer()
            }
            assertIs<OcrError.Unsupported>(client.extract(NO_BYTES).exceptionOrNull())
        }

    @Test
    fun `a closed client refuses further work`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer()
        }
        client.extract(NO_BYTES).getOrThrow()
        client.close()
        client.close() // idempotent

        assertIs<OcrError.Unsupported>(client.extract(NO_BYTES).exceptionOrNull())
    }

    @Test
    fun `pageRange limits which pages are processed`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 10)
            pageRange = 2..4
        }
        val document = client.extract(NO_BYTES).getOrThrow()
        assertEquals(listOf(2, 3, 4), document.pages.map { it.index })
        client.close()
    }

    @Test
    fun `an out-of-bounds pageRange is clamped to the real page count`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 3)
            pageRange = 1..99
        }
        val document = client.extract(NO_BYTES).getOrThrow()
        assertEquals(listOf(1, 2), document.pages.map { it.index })
        client.close()
    }

    @Test
    fun `a pageRange running to Int MAX_VALUE is intersected and not walked`() = runTest {
        // `0..Int.MAX_VALUE` is the legal way to say "to the end of the document".
        // Filtering the caller's range instead of intersecting it arithmetically walked
        // two billion indices before touching a single page, so the regression signal
        // here is the test timing out rather than a wrong assertion.
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 3)
            pageRange = 0..Int.MAX_VALUE
        }
        val document = client.extract(NO_BYTES).getOrThrow()
        assertEquals(listOf(0, 1, 2), document.pages.map { it.index })
        client.close()
    }

    @Test
    fun `a document with no pages fails with InvalidInput`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 0)
        }
        assertIs<OcrError.InvalidInput>(client.extract(NO_BYTES).exceptionOrNull())

        // The intersection has to survive an empty document too: clamping against
        // `pageCount - 1` makes the upper bound -1, which must read as "no pages" rather
        // than as a range to iterate.
        val ranged = UncialClient {
            engine = FakeOcrEngine()
            rasterizer = FakePageRasterizer(pageCount = 0)
            pageRange = 0..Int.MAX_VALUE
        }
        assertIs<OcrError.InvalidInput>(ranged.extract(NO_BYTES).exceptionOrNull())
        client.close()
        ranged.close()
    }

    @Test
    fun `close during engine creation closes the recognizer instead of leaking it`() = runTest {
        // The race the store-then-recheck in obtainRecognizer exists for: close() cannot
        // take the suspending engineLock, so it can land at any point while create() is
        // still loading the language model -- ~8 MB plus a native handle for ukr+eng. The
        // deferreds make that interleaving deterministic rather than lucky.
        val engine = GatedEngine()
        val client = UncialClient {
            this.engine = engine
            rasterizer = FakePageRasterizer()
        }
        val extraction = async { client.extract(NO_BYTES) }

        engine.creating.await()
        client.close()
        engine.release.complete(Unit)
        val error = extraction.await().exceptionOrNull()

        // Nobody else can ever reach this recognizer, so if the client did not close it
        // the handle is gone for good.
        assertTrue(assertNotNull(engine.created).isClosed, "the recognizer was leaked")
        assertIs<OcrError.Unsupported>(error)
    }

    @Test
    fun `Unsupported carries the probe failure that made the registry come back empty`() =
        runTest {
            // The registry is process-global, hence the finally: this is the one test here
            // that resolves an engine through it rather than being handed one.
            val failure = ProbeBlewUp()
            OcrEngineRegistry.register(ThrowingProbeEngine(failure))
            try {
                val client = UncialClient { rasterizer = FakePageRasterizer() }
                val error = assertIs<OcrError.Unsupported>(
                    client.extract(NO_BYTES).exceptionOrNull(),
                )
                // Without the cause the caller is told "no OCR engine available" for an
                // engine that is installed and did report -- by throwing.
                assertSame(failure, error.cause)
                client.close()
            } finally {
                OcrEngineRegistry.unregister(ThrowingProbeEngine.ID)
            }
        }

    @Test
    fun `extractOrThrow throws the typed error for Swift callers`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine(failOnPage = 0)
            rasterizer = FakePageRasterizer(pageCount = 1)
        }
        assertFailsWith<OcrError.RecognitionFailed> { client.extractOrThrow(NO_BYTES) }
        client.close()
    }
}

/**
 * An engine whose `create` blocks until the test lets it finish.
 *
 * `FakeOcrEngine` cannot express this: it has no hook in `create` and its recognizer does
 * not record being closed, and widening its published API for one test in this module
 * would put both on every consumer's surface.
 */
private class GatedEngine : OcrEngineFactory {

    /** Completes once `create` has started, so the test knows the model is "loading". */
    val creating: CompletableDeferred<Unit> = CompletableDeferred()

    /** Completed by the test to let `create` return. */
    val release: CompletableDeferred<Unit> = CompletableDeferred()

    var created: ClosingRecognizer? = null
        private set

    override val id: String = "gated"

    override fun isAvailable(): Boolean = true

    override fun capabilities(options: OcrOptions): OcrCapabilities = OcrCapabilities(
        engineId = id,
        engineVersion = "gated",
        languages = options.languages,
    )

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer {
        creating.complete(Unit)
        release.await()
        return ClosingRecognizer(capabilities(options)).also { created = it }
    }
}

/** A recognizer that remembers whether anyone released it. */
private class ClosingRecognizer(
    override val capabilities: OcrCapabilities,
) : TextRecognizer {

    var isClosed: Boolean = false
        private set

    override suspend fun recognize(
        raster: Raster,
        pageIndex: Int,
        options: OcrOptions,
    ): OcrPage = error("the closed client must never get as far as recognition")

    override fun close() {
        isClosed = true
    }
}

/** An engine that blows up when asked whether it can run, the way a missing native lib does. */
private class ThrowingProbeEngine(private val failure: Throwable) : OcrEngineFactory {

    override val id: String = ID

    override fun isAvailable(): Boolean = throw failure

    override fun capabilities(options: OcrOptions): OcrCapabilities =
        error("not reachable: the probe throws first")

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer = error("not reachable: the probe throws first")

    companion object {
        const val ID: String = "throwing-probe"
    }
}

private class ProbeBlewUp : IllegalStateException("no native library here")
