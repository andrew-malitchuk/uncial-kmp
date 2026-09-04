package io.github.andrewmalitchuk.uncial.samples.android.core.document

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A host test, so no `Bitmap` and no Compose: `summarize` is a pure function precisely so
 * the one piece of arithmetic in the sample can be checked without a device.
 *
 * The screen draws an empty confidence bar for `null` -- "not reported" is not "reported as
 * zero", and a digital text layer does not guess.
 */
class DocumentSummarizingTest {

    @Test
    fun `an unknown confidence never reaches the average`() {
        val summary = document(
            line("known", Confidence.of(0.9f)),
            line("unknown", Confidence.Unknown),
        ).summarize(label = "fixture", elapsedMillis = 10, blocks = emptyList())

        assertEquals(0.9f, assertNotNull(summary.confidence), absoluteTolerance = 1e-5f)
    }

    @Test
    fun `a page nobody scored reports no confidence rather than zero`() {
        val summary = document(line("a", Confidence.Unknown))
            .summarize(label = "fixture", elapsedMillis = 10, blocks = emptyList())

        assertNull(summary.confidence)
        assertNull(summary.pages.single().confidence)
    }

    @Test
    fun `the label and elapsed time are carried through untouched`() {
        val summary = document(line("a", Confidence.Certain))
            .summarize(label = "Scanned fixture", elapsedMillis = 2_345, blocks = emptyList())

        assertEquals("Scanned fixture", summary.label)
        assertEquals(2_345, summary.elapsedMillis)
        assertEquals(ExtractionSource.Ocr, summary.source)
        assertEquals(1, summary.pages.single().number)
    }

    private fun document(vararg lines: OcrLine) = OcrDocument(
        pages = listOf(
            OcrPage(
                index = 0,
                size = Size(600f, 800f),
                lines = lines.toList(),
                source = ExtractionSource.Ocr,
            ),
        ),
        source = ExtractionSource.Ocr,
    )

    private fun line(text: String, confidence: Confidence) = OcrLine(
        text = text,
        box = BoundingBox(left = 0f, top = 0f, width = 100f, height = 20f),
        fontSize = 12f,
        confidence = confidence,
    )
}
