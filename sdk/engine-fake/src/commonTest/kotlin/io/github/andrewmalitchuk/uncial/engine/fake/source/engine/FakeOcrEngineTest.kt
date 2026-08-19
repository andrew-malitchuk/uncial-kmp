package io.github.andrewmalitchuk.uncial.engine.fake.source.engine

import io.github.andrewmalitchuk.uncial.core.source.raster.placeholderRaster
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The fake is published, so these are the guarantees somebody else's test suite is built
 * on -- including the awkward one: an engine that *claims* word support has to produce
 * words, or the fake hides the very bug it exists to expose.
 */
class FakeOcrEngineTest {

    @Test
    fun `a page recognizes as the text it was given`() = runTest {
        val page = FakeOcrEngine(text = mapOf(0 to "Розділ 1\nТекст сторінки"))
            .recognize(pageIndex = 0)

        assertContentEquals(listOf("Розділ 1", "Текст сторінки"), page.lines.map { it.text })
        assertEquals(ExtractionSource.Ocr, page.source)
    }

    @Test
    fun `a page with no entry falls back to the default text`() = runTest {
        val page = FakeOcrEngine(defaultText = "fallback").recognize(pageIndex = 7)

        assertContentEquals(listOf("fallback"), page.lines.map { it.text })
    }

    @Test
    fun `blank text means an image-only page that OCR could not read`() = runTest {
        val page = FakeOcrEngine(defaultText = "").recognize(pageIndex = 0)

        assertTrue(page.lines.isEmpty())
    }

    @Test
    fun `failOnPage throws for that page only`() = runTest {
        val engine = FakeOcrEngine(failOnPage = 1)

        engine.recognize(pageIndex = 0)
        val failure = assertFailsWith<OcrError.RecognitionFailed> { engine.recognize(pageIndex = 1) }
        assertEquals(1, failure.pageIndex)
    }

    @Test
    fun `an unavailable engine reports it and refuses to create a recognizer`() = runTest {
        val engine = FakeOcrEngine(available = false)

        assertFalse(engine.isAvailable())
        assertFailsWith<OcrError.EngineInit> { engine.create(OcrOptions(), logger = NoLogger) }
    }

    @Test
    fun `capabilities echo the requested languages`() = runTest {
        val requested = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)

        val capabilities = FakeOcrEngine().capabilities(OcrOptions(languages = requested))

        assertContentEquals(requested, capabilities.languages)
        assertEquals(FakeOcrEngine.FAKE_ENGINE_ID, capabilities.engineId)
    }

    @Test
    fun `words appear only when the caller asks and the engine claims them`() = runTest {
        val withWords = FakeOcrEngine(text = mapOf(0 to "два слова"))
            .recognize(pageIndex = 0, options = OcrOptions(includeWords = true))
        assertContentEquals(listOf("два", "слова"), withWords.lines.single().words.map { it.text })

        val withoutWords = FakeOcrEngine(text = mapOf(0 to "два слова"))
            .recognize(pageIndex = 0, options = OcrOptions(includeWords = false))
        assertTrue(withoutWords.lines.single().words.isEmpty())

        val engineWithoutWordSupport = FakeOcrEngine(
            text = mapOf(0 to "два слова"),
            capabilities = FakeOcrEngine.DefaultCapabilities.copy(wordLevel = false),
        ).recognize(pageIndex = 0, options = OcrOptions(includeWords = true))
        assertTrue(engineWithoutWordSupport.lines.single().words.isEmpty())
    }

    @Test
    fun `lines carry plausible geometry rather than zero boxes`() = runTest {
        val page = FakeOcrEngine(text = mapOf(0 to "перший\nдругий")).recognize(pageIndex = 0)

        val boxes = page.lines.map { it.box }
        assertTrue(boxes.all { it.width > 0f && it.height > 0f }, "structure tests read these boxes")
        assertTrue(boxes[1].top > boxes[0].top, "lines must descend the page")
        assertTrue(page.lines.all { it.fontSize > 0f }, "a 0f size must never reach the median")
    }

    @Test
    fun `the page reports the size of the raster it was handed`() = runTest {
        val page = FakeOcrEngine().recognize(pageIndex = 0, width = 800, height = 1200)

        assertEquals(800f, page.size.width)
        assertEquals(1200f, page.size.height)
    }

    private suspend fun FakeOcrEngine.recognize(
        pageIndex: Int,
        options: OcrOptions = OcrOptions(),
        width: Int = 100,
        height: Int = 200,
    ) = create(options, logger = NoLogger).use { recognizer ->
        recognizer.recognize(placeholderRaster(width, height), pageIndex, options)
    }
}
