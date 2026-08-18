package io.github.andrewmalitchuk.uncial.pdftext.core.assembly

import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage

/**
 * Decides whether an extracted text layer is worth using.
 *
 * A scanned PDF is not simply "a PDF without text": it often carries a few stray
 * characters — a watermark, a producer string leaked into content, a stamped page number —
 * which would otherwise pass for a text layer and suppress OCR of the whole document. So
 * "has a text layer" is a judgement about volume, not presence.
 */
internal object DigitalPageAssembly {

    /**
     * Minimum characters on a page before its text layer is believed.
     *
     * A page below this is treated as having no text at all, so the runtime OCRs it. Set
     * from the observation that a stamped page number is 1-4 characters and a real page of
     * prose is hundreds.
     */
    private const val MIN_CHARACTERS_PER_PAGE = 24

    /** `true` when this page's text layer carries enough text to trust. */
    fun hasUsableText(page: OcrPage): Boolean =
        page.lines.sumOf { it.text.trim().length } >= MIN_CHARACTERS_PER_PAGE

    /**
     * Assembles the pages that had usable text into a document.
     *
     * @return `null` when no page did, which is the runtime's signal to fall back to OCR
     *   entirely. Pages that individually failed the test are returned empty, so the
     *   runtime can OCR exactly those and leave the rest alone.
     */
    fun assemble(pages: List<OcrPage>): OcrDocument? {
        if (pages.none(::hasUsableText)) return null
        val kept = pages.map { page ->
            if (hasUsableText(page)) page else page.copy(lines = emptyList())
        }
        return OcrDocument(pages = kept, source = ExtractionSource.DigitalTextLayer)
    }
}
