package io.github.andrewmalitchuk.uncial.dist.core.summary

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.model.source.text.OcrWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * This exists because Swift cannot do arithmetic on a `Confidence` value class -- so the
 * averaging happens here, where `Unknown` is `NaN`. Averaging a `NaN` in poisons the mean
 * silently: every comparison against it is false, so the number would neither look wrong
 * nor be right.
 */
class OcrDocumentSummarizingTest {

    @Test
    fun `an unknown confidence is left out of the average`() {
        val document = document(
            page(0, line("known", Confidence.of(0.8f)), line("unknown", Confidence.Unknown)),
        )

        val confidence = document.toIosSummary().confidence

        assertEquals(0.8f, assertNotNull(confidence), absoluteTolerance = TOLERANCE)
    }

    @Test
    fun `a document with no known confidence reports none rather than zero`() {
        val document = document(page(0, line("a", Confidence.Unknown)))

        // `null` is "not reported"; 0f would claim the engine was certain it was wrong.
        assertNull(document.toIosSummary().confidence)
    }

    @Test
    fun `counts cover every page`() {
        val document = document(
            page(0, line("один", Confidence.Certain, words = 1)),
            page(1, line("два слова", Confidence.Certain, words = 2), line("три", Confidence.Certain)),
        )

        val summary = document.toIosSummary()

        assertEquals(2, summary.pageCount)
        assertEquals(3, summary.lines)
        assertEquals(3, summary.words)
        assertEquals(ExtractionSource.Ocr.name, summary.source)
    }

    @Test
    fun `pages are numbered from one for a human reader`() {
        val summary = document(page(0, line("a", Confidence.Certain))).toIosSummary()

        assertEquals(listOf(1), summary.pages.map { it.number })
    }

    @Test
    fun `per-page confidence is averaged per page`() {
        val document = document(
            page(0, line("a", Confidence.of(0.6f)), line("b", Confidence.of(0.8f))),
            page(1, line("c", Confidence.Unknown)),
        )

        val pages = document.toIosSummary().pages

        // Averaged through a Double, so compare with a tolerance rather than bit-for-bit.
        assertEquals(0.7f, assertNotNull(pages[0].confidence), absoluteTolerance = TOLERANCE)
        assertNull(pages[1].confidence)
    }

    @Test
    fun `the structure is reconstructed into the summary`() {
        val summary = document(page(0, line("Заголовок", Confidence.Certain))).toIosSummary()

        assertTrue(summary.blocks.isNotEmpty(), "Swift reads blocks, not document.text")
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }

    private fun document(vararg pages: OcrPage) =
        OcrDocument(pages = pages.toList(), source = ExtractionSource.Ocr)

    private fun page(index: Int, vararg lines: OcrLine) = OcrPage(
        index = index,
        size = Size(600f, 800f),
        lines = lines.toList(),
        source = ExtractionSource.Ocr,
    )

    private fun line(text: String, confidence: Confidence, words: Int = 0) = OcrLine(
        text = text,
        box = BoundingBox(left = 0f, top = 0f, width = 100f, height = 20f),
        fontSize = 12f,
        confidence = confidence,
        words = List(words) { index ->
            OcrWord(
                text = "w$index",
                box = BoundingBox(left = index * 30f, top = 0f, width = 25f, height = 20f),
            )
        },
    )
}
