package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import kotlin.test.Test
import kotlin.test.assertEquals

class OcrDocumentTest {

    @Test
    fun `copy keeps the source it was given because defaults do not re-run`() {
        val document = OcrDocument(
            listOf(
                page(0, ExtractionSource.DigitalTextLayer),
                page(1, ExtractionSource.Ocr),
            ),
        )
        assertEquals(ExtractionSource.Mixed, document.source)

        val digitalOnly = document.pages.filter { it.source == ExtractionSource.DigitalTextLayer }
        // The documented trap: copy() does not re-run the default, so the narrowed document
        // still claims Mixed. Pinned here so that anyone who makes `source` a computed val
        // has to come back and delete this test on purpose.
        assertEquals(ExtractionSource.Mixed, document.copy(pages = digitalOnly).source)
        // The form the KDoc points callers at.
        assertEquals(ExtractionSource.DigitalTextLayer, OcrDocument(digitalOnly).source)
    }

    private fun page(index: Int, source: ExtractionSource) = OcrPage(
        index = index,
        size = Size(1000f, 1400f),
        lines = emptyList(),
        source = source,
    )
}
