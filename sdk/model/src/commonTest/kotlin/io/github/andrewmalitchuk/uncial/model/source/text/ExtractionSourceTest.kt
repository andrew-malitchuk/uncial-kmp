package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtractionSourceTest {

    @Test
    fun `a document's source is Mixed only when its pages disagree`() {
        assertEquals(
            ExtractionSource.Ocr,
            ExtractionSource.of(listOf(ExtractionSource.Ocr, ExtractionSource.Ocr)),
        )
        assertEquals(
            ExtractionSource.Mixed,
            ExtractionSource.of(
                listOf(ExtractionSource.DigitalTextLayer, ExtractionSource.Ocr),
            ),
        )
        // An empty document has to claim something; OCR is the safe assumption.
        assertEquals(ExtractionSource.Ocr, ExtractionSource.of(emptyList()))
    }
}
