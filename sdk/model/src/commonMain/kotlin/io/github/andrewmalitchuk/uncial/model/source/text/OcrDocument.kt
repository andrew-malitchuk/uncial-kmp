package io.github.andrewmalitchuk.uncial.model.source.text

/**
 * A recognized document: the single format both extraction paths and all three engines
 * reduce to.
 *
 * This is the type the whole SDK exists to produce. `DocumentStructure.reconstruct` turns
 * it into `DocBlock`s; everything else is a way of filling it in.
 */
public data class OcrDocument(
    public val pages: List<OcrPage>,
    /**
     * Which path produced this document; [ExtractionSource.Mixed] if the pages disagree.
     *
     * **Derived from [pages] only when the default is used.** `copy()` does not re-run
     * default arguments, so `document.copy(pages = pages.filter { ... })` keeps the source
     * of the document it came from: a `Mixed` document narrowed to only its digital pages
     * still reports `Mixed`. Narrow with the constructor instead, so the default runs
     * again:
     *
     * ```
     * OcrDocument(document.pages.filter { it.source == ExtractionSource.DigitalTextLayer })
     * ```
     *
     * It stays a constructor property rather than a computed `val` because a producer has
     * to be able to state a source the pages alone cannot imply — a document with no pages
     * still knows which path came back empty-handed, while [ExtractionSource.of] can only
     * guess [ExtractionSource.Ocr].
     */
    public val source: ExtractionSource = ExtractionSource.of(pages.map { it.source }),
) {
    /** `true` when no page produced a single line — an image-only PDF that OCR failed on. */
    public val isEmpty: Boolean get() = pages.all { it.isEmpty }

    public val pageCount: Int get() = pages.size

    /** The document's text, pages separated by a blank line. */
    public val text: String get() = pages.joinToString("\n\n") { it.text }

    /** Every line of every page, flattened. */
    public val lines: List<OcrLine> get() = pages.flatMap { it.lines }
}
