package io.github.andrewmalitchuk.uncial.model.source.text

/**
 * Which path produced a document or page.
 *
 * Uncial has two ways to get text out of a PDF, and they have very different
 * characteristics: a digital text layer is exact and cheap, OCR is a guess and costs
 * seconds per page. Callers routinely need to know which one they got — to decide whether
 * to trust the output, to show a "scanned document" badge, or to explain a slow run — so
 * the answer travels with the result rather than being inferred from timings.
 */
public enum class ExtractionSource {
    /** Read from the PDF's embedded text layer. Exact; [Confidence.Certain] throughout. */
    DigitalTextLayer,

    /** Recognized from a raster by an OCR engine. */
    Ocr,

    /** Some pages came from the text layer, others were OCR'd — a common hybrid scan. */
    Mixed,
    ;

    public companion object {
        /** Reduces per-page sources to the source of the document as a whole. */
        public fun of(pageSources: Iterable<ExtractionSource>): ExtractionSource {
            val distinct = pageSources.toSet()
            return when {
                distinct.isEmpty() -> Ocr
                distinct.size == 1 -> distinct.first()
                else -> Mixed
            }
        }
    }
}
