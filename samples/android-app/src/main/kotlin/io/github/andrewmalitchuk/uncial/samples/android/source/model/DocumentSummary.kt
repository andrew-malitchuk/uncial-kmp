package io.github.andrewmalitchuk.uncial.samples.android.source.model

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource

/**
 * A finished extraction, reduced to what the document screen shows.
 *
 * @property words `0` when `OcrOptions.includeWords` is off or the engine cannot do it;
 *   the screen renders that as `—` rather than as a zero.
 * @property confidence the mean of the known line confidences, or `null` when none were.
 */
data class DocumentSummary(
    val label: String,
    val source: ExtractionSource,
    val pages: List<PageSummary>,
    val lines: Int,
    val words: Int,
    val confidence: Float?,
    val elapsedMillis: Long,
    val blocks: List<DocBlock>,
) {
    val pageCount: Int get() = pages.size
    val isEmpty: Boolean get() = lines == 0
}
