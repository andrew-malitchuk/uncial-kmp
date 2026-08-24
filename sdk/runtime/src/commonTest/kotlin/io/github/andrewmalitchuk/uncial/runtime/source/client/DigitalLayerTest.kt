package io.github.andrewmalitchuk.uncial.runtime.source.client

import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.RasterizedDocument
import io.github.andrewmalitchuk.uncial.engine.fake.source.engine.FakeOcrEngine
import io.github.andrewmalitchuk.uncial.engine.fake.source.raster.FakePageRasterizer
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.runtime.core.fake.FakeDigitalTextExtractor
import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

private val NO_BYTES = ByteArray(0)

/**
 * The digital text layer is the fast path: exact, and roughly a thousand times cheaper
 * than OCR. These pin down when it is used, when it is skipped, and what happens when it
 * only covers part of a document.
 */
class DigitalLayerTest {

    @Test
    fun `a full text layer is used and OCR never runs`() = runTest {
        val engine = FakeOcrEngine(text = mapOf(0 to "OCR SHOULD NOT RUN"))
        val client = UncialClient {
            this.engine = engine
            rasterizer = FakePageRasterizer(pageCount = 2)
            digitalTextExtractor = FakeDigitalTextExtractor(
                pageText = mapOf(0 to "цифровий текст сторінки один", 1 to "і сторінки два"),
                pageCount = 2,
            )
            preferDigitalLayer = true
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(ExtractionSource.DigitalTextLayer, document.source)
        assertEquals("цифровий текст сторінки один", document.pages[0].text)
        assertTrue("OCR SHOULD NOT RUN" !in document.text)
        client.close()
    }

    @Test
    fun `a partial text layer is completed by OCR and the result is Mixed`() = runTest {
        // The real case this models: a scan whose cover page the scanner already OCR'd.
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 3)
            digitalTextExtractor = FakeDigitalTextExtractor(
                pageText = mapOf(0 to "цифрова обкладинка"),
                pageCount = 3,
            )
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(ExtractionSource.Mixed, document.source)
        assertEquals("цифрова обкладинка", document.pages[0].text)
        assertEquals(ExtractionSource.DigitalTextLayer, document.pages[0].source)
        assertEquals("розпізнано рушієм", document.pages[1].text)
        assertEquals(ExtractionSource.Ocr, document.pages[1].source)
        client.close()
    }

    @Test
    fun `a PDF with no text layer falls back to OCR entirely`() = runTest {
        val digital = FakeDigitalTextExtractor(pageText = emptyMap(), pageCount = 2, present = false)
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 2)
            digitalTextExtractor = digital
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(1, digital.extractCalls, "the digital layer should be tried exactly once")
        assertEquals(ExtractionSource.Ocr, document.source)
        assertEquals("розпізнано рушієм", document.pages[0].text)
        client.close()
    }

    @Test
    fun `preferDigitalLayer false skips the text layer without even asking`() = runTest {
        val digital = FakeDigitalTextExtractor(
            pageText = mapOf(0 to "цифровий текст"),
            pageCount = 1,
        )
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 1)
            digitalTextExtractor = digital
            preferDigitalLayer = false
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(0, digital.extractCalls)
        assertEquals("розпізнано рушієм", document.pages[0].text)
        client.close()
    }

    @Test
    fun `without the pdf-text module the client says so and OCRs`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 1)
        }
        assertTrue(!client.hasDigitalTextLayerSupport)
        assertEquals(ExtractionSource.Ocr, client.extract(NO_BYTES).getOrThrow().source)
        client.close()
    }

    @Test
    fun `coverage is judged over the requested pages and not the whole document`() = runTest {
        // The bug: a scan whose first pages carry no text made the fast path look
        // incomplete even when every page the caller asked for had text. The PDF was then
        // opened for a rasterization it never used, and the run announced Started(Mixed)
        // before a Done that reported DigitalTextLayer.
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "OCR SHOULD NOT RUN")
            // Opening at all is the failure: the fast path must not touch the rasterizer.
            rasterizer = UnopenablePageRasterizer
            digitalTextExtractor = FakeDigitalTextExtractor(
                pageText = mapOf(2 to "сторінка три", 3 to "сторінка чотири", 4 to "п'ять"),
                pageCount = 5,
            )
            pageRange = 2..4
        }
        val events = client.extractAsFlow(NO_BYTES).toList()

        val started = assertIs<OcrProgress.Started>(events.first())
        assertEquals(3, started.pageCount)
        assertEquals(ExtractionSource.DigitalTextLayer, started.source)

        val document = assertIs<OcrProgress.Done>(events.last()).document
        // The two events used to contradict each other; they must agree.
        assertEquals(started.source, document.source)
        assertEquals(listOf(2, 3, 4), document.pages.map { it.index })
        client.close()
    }

    @Test
    fun `Started announces the source the run will really produce`() = runTest {
        // The text layer covers page 0 only and page 0 was not asked for, so nothing is
        // reused: announcing Mixed here would be contradicted by the Done that follows.
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 3)
            digitalTextExtractor = FakeDigitalTextExtractor(
                pageText = mapOf(0 to "цифрова обкладинка"),
                pageCount = 3,
            )
            pageRange = 1..2
        }
        val events = client.extractAsFlow(NO_BYTES).toList()

        val started = assertIs<OcrProgress.Started>(events.first())
        val document = assertIs<OcrProgress.Done>(events.last()).document
        assertEquals(ExtractionSource.Ocr, started.source)
        assertEquals(started.source, document.source)
        client.close()
    }

    @Test
    fun `an extractor that returns no pages does not count as full coverage`() = runTest {
        // `all {}` is true of an empty list, so a document with no pages used to satisfy
        // the fast path and the caller got nothing back instead of OCR.
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 2)
            digitalTextExtractor = FakeDigitalTextExtractor(pageText = emptyMap(), pageCount = 0)
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(2, document.pageCount)
        assertEquals(ExtractionSource.Ocr, document.source)
        assertEquals("розпізнано рушієм", document.pages[0].text)
        client.close()
    }

    @Test
    fun `a repeated page index keeps the first copy the way the scan it replaced did`() = runTest {
        // Only a malformed extractor reports one index twice, but indexing the text layer
        // instead of scanning it per page was meant to change the cost and not the answer:
        // associateBy keeps the LAST entry for a repeated key where the scan kept the first.
        val client = UncialClient {
            engine = FakeOcrEngine(defaultText = "розпізнано рушієм")
            rasterizer = FakePageRasterizer(pageCount = 2)
            digitalTextExtractor = RepeatedIndexExtractor
        }
        val document = client.extract(NO_BYTES).getOrThrow()

        assertEquals(ExtractionSource.Mixed, document.source)
        assertEquals("перша копія", document.pages[0].text)
        client.close()
    }

    @Test
    fun `a pageRange selecting nothing fails the same way with and without a text layer`() =
        runTest {
            // Same input must not mean "error" or "empty document" depending on whether the
            // PDF happens to carry text.
            val withLayer = UncialClient {
                engine = FakeOcrEngine()
                rasterizer = FakePageRasterizer(pageCount = 3)
                digitalTextExtractor = FakeDigitalTextExtractor(
                    pageText = mapOf(0 to "перша", 1 to "друга", 2 to "третя"),
                    pageCount = 3,
                )
                pageRange = 10..20
            }
            val withoutLayer = UncialClient {
                engine = FakeOcrEngine()
                rasterizer = FakePageRasterizer(pageCount = 3)
                pageRange = 10..20
            }

            assertIs<OcrError.InvalidInput>(withLayer.extract(NO_BYTES).exceptionOrNull())
            assertIs<OcrError.InvalidInput>(withoutLayer.extract(NO_BYTES).exceptionOrNull())
            withLayer.close()
            withoutLayer.close()
        }
}

/** A rasterizer that fails the test if the digital fast path ever reaches for it. */
private object UnopenablePageRasterizer : PageRasterizer {
    override suspend fun open(bytes: ByteArray): RasterizedDocument =
        throw AssertionError("the digital fast path must not open the document")
}

/**
 * A text layer that reports page 0 twice, which nothing well-behaved does.
 *
 * `FakeDigitalTextExtractor` builds its pages from `0 until pageCount` and so cannot
 * produce a duplicate at all; this exists purely to pin which copy wins. Page 1 carries no
 * text so that the fast path falls through to OCR, where the indexing happens.
 */
private object RepeatedIndexExtractor : DigitalTextExtractor {

    override val id: String = "repeated-index"

    override suspend fun extract(bytes: ByteArray, options: OcrOptions): OcrDocument =
        OcrDocument(
            listOf(page(0, "перша копія"), page(0, "друга копія"), page(1, text = null)),
            ExtractionSource.DigitalTextLayer,
        )

    private fun page(index: Int, text: String?) = OcrPage(
        index = index,
        size = Size(1000f, 1400f),
        lines = text?.let {
            listOf(
                OcrLine(
                    text = it,
                    box = BoundingBox(40f, 60f, 400f, 16f),
                    fontSize = 16f,
                    confidence = Confidence.Certain,
                ),
            )
        }.orEmpty(),
        source = ExtractionSource.DigitalTextLayer,
    )
}
