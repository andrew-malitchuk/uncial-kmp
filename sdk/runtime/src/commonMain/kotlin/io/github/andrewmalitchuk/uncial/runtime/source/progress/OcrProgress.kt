package io.github.andrewmalitchuk.uncial.runtime.source.progress

import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage

/**
 * What `UncialClient.extractAsFlow` emits as it works through a document.
 *
 * The POC's `ocr(bytes)` blocked for minutes on a 300-page scan with no way to show
 * progress and no way to stop it. Per-page emission is what makes both possible.
 * (PLAN.md §5.2)
 *
 * Failures are **not** an event here: the flow throws
 * `io.github.andrewmalitchuk.uncial.model.OcrError`, which is what a `Flow` collector
 * already knows how to handle. Cancellation likewise propagates as
 * `CancellationException`.
 *
 * New subtypes may be added in minor releases; give a `when` over this an `else`.
 */
public sealed interface OcrProgress {

    /**
     * Emitted once, before any page is processed.
     *
     * Carries the page count, which a progress indicator needs before the first page
     * arrives — this is the reason the type exists rather than starting straight at
     * [Page].
     *
     * @property pageCount how many pages will be processed, after `OcrOptions.pageRange`
     *   is applied. Not necessarily the document's page count.
     * @property source how the text is being obtained. [ExtractionSource.Mixed] means the
     *   digital layer covered some pages and the rest are being OCR'd.
     */
    public data class Started(
        public val pageCount: Int,
        public val source: ExtractionSource,
    ) : OcrProgress

    /**
     * Emitted after each page finishes.
     *
     * @property index the page's zero-based index **in the source document**.
     * @property completed how many pages are done, counting this one.
     * @property of how many pages will be processed in total; the same value as
     *   [Started.pageCount].
     * @property page the recognized page, so a caller can render results incrementally
     *   rather than waiting for the whole document.
     */
    public data class Page(
        public val index: Int,
        public val completed: Int,
        public val of: Int,
        public val page: OcrPage,
    ) : OcrProgress {
        /** Completion as `0.0..1.0`, for a progress bar. */
        public val fraction: Float get() = if (of <= 0) 1f else completed.toFloat() / of
    }

    /**
     * Emitted once, last, with the assembled document.
     *
     * A collector that only wants the end result can `filterIsInstance<Done>().first()` —
     * or just call `UncialClient.extract`, which does exactly that.
     */
    public data class Done(
        public val document: OcrDocument,
    ) : OcrProgress
}
