package io.github.andrewmalitchuk.uncial.samples.android.source.model

import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource

/** Where the current extraction is. */
sealed interface RunState {

    /** Nothing in flight. */
    data object Idle : RunState

    /**
     * An extraction is running.
     *
     * @property label what is being recognized, for the progress screen's subtitle.
     * @property completed pages finished so far.
     * @property total pages the document turned out to have, or `0` before `OcrProgress`
     *   has said — an image has one page and says so immediately, a PDF does not.
     * @property source `null` until `OcrProgress.Started` says which path was taken —
     *   which is the point of the digital fast path: whether the engine runs at all is
     *   decided after the document has been opened, not before.
     * @property lastPageLines a short tail of "page N · M lines", newest last.
     */
    data class Running(
        val label: String,
        val completed: Int = 0,
        val total: Int = 0,
        val source: ExtractionSource? = null,
        val lastPageLines: List<String> = emptyList(),
    ) : RunState {
        /** `null` while the page count is unknown, so the UI can show an indeterminate bar. */
        val fraction: Float? get() = if (total > 0) completed.toFloat() / total else null
    }
}
