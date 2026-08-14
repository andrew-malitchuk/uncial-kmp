package io.github.andrewmalitchuk.uncial.core.source.engine

import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument

/**
 * Reads a PDF's embedded text layer — the fast path that skips OCR entirely.
 *
 * A PDF produced by a word processor already contains its text with coordinates; running
 * OCR over a raster of it is roughly a thousand times slower and strictly less accurate.
 * When `OcrOptions.preferDigitalLayer` is set, the runtime tries this first and falls back
 * to OCR per page.
 *
 * Declared in `core` rather than in `uncial-pdf-text` so the runtime can use the digital
 * path without depending on the module that implements it — the same indirection that
 * keeps engines out of the runtime's compile classpath. Absent that module, the runtime
 * simply has no extractor and goes straight to OCR.
 */
public interface DigitalTextExtractor {

    /** Stable identifier for logging and capability reporting. */
    public val id: String

    /**
     * Extracts the text layer of [bytes].
     *
     * ### The page list must be complete
     *
     * An implementation returns **one [io.github.andrewmalitchuk.uncial.model.text.OcrPage]
     * per page of the input**, in order, with `lines` left empty for pages that carry no
     * usable text. It must *not* return only the pages it found text on: the runtime reads
     * the list's length as the document's length, so a short list makes the missing pages
     * disappear from the result instead of being sent to OCR. Emptiness per page is the
     * signal — never absence.
     *
     * @return every page of the document, or `null` when there is no text layer at all —
     *   the signal to fall back to OCR entirely. A returned document is routinely partial
     *   in *content*: a scan with an OCR'd cover page has text on page 1 and empty `lines`
     *   on the rest, and the runtime OCRs those.
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.InvalidInput if the bytes
     *   are not a readable PDF.
     */
    public suspend fun extract(bytes: ByteArray, options: OcrOptions): OcrDocument?
}
