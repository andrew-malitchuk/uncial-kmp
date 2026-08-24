package io.github.andrewmalitchuk.uncial.runtime.core.fake

import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage

/**
 * A digital text layer with configurable coverage.
 *
 * @param pageText index to text, one line per `\n`. A page missing from the map comes back
 *   with no lines, which is how a partially-OCR'd scan looks to the runtime.
 * @param pageCount how many pages the imaginary PDF has. `0` models the third-party
 *   extractor that returns a document with no pages at all rather than admitting it found
 *   nothing — the runtime must not read that as full coverage.
 * @param present `false` makes this behave like a PDF with no text layer at all.
 */
internal class FakeDigitalTextExtractor(
    private val pageText: Map<Int, String>,
    private val pageCount: Int,
    private val present: Boolean = true,
) : DigitalTextExtractor {

    var extractCalls: Int = 0
        private set

    override val id: String = "fake-digital"

    override suspend fun extract(bytes: ByteArray, options: OcrOptions): OcrDocument? {
        extractCalls++
        if (!present) return null
        val pages = (0 until pageCount).map { index ->
            OcrPage(
                index = index,
                size = Size(1000f, 1400f),
                lines = pageText[index]?.lines()?.filter { it.isNotBlank() }
                    ?.mapIndexed { lineIndex, text ->
                        OcrLine(
                            text = text,
                            box = BoundingBox(40f, 60f + lineIndex * 20f, 400f, 16f),
                            fontSize = 16f,
                            confidence = Confidence.Certain,
                        )
                    }
                    .orEmpty(),
                source = ExtractionSource.DigitalTextLayer,
            )
        }
        return if (pages.any { it.lines.isNotEmpty() } || pageCount == 0) {
            OcrDocument(pages, ExtractionSource.DigitalTextLayer)
        } else {
            null
        }
    }
}
