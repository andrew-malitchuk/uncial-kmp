package io.github.andrewmalitchuk.uncial.samples.android.core.document

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.samples.android.source.model.DocumentSummary
import io.github.andrewmalitchuk.uncial.samples.android.source.model.PageSummary

/**
 * Reduces a recognized document to [DocumentSummary].
 *
 * `Confidence.isKnown` is checked rather than reading `value` directly: an unknown
 * confidence is `NaN`, and averaging it in would poison the mean silently — every
 * comparison against `NaN` is false, so the result would neither look wrong nor be right.
 *
 * Walks every line of the document twice, so it is a pure function on purpose: the caller
 * moves it off the main thread rather than this deciding a dispatcher for it.
 */
internal fun OcrDocument.summarize(
    label: String,
    elapsedMillis: Long,
    blocks: List<DocBlock>,
): DocumentSummary {
    val known = pages.flatMap { page -> page.lines.map { it.confidence } }.filter { it.isKnown }
    return DocumentSummary(
        label = label,
        source = source,
        pages = pages.map { page ->
            val pageKnown = page.lines.map { it.confidence }.filter { it.isKnown }
            PageSummary(
                number = page.index + 1,
                lines = page.lines.size,
                confidence = pageKnown.takeIf { it.isNotEmpty() }
                    ?.map { it.value }
                    ?.average()
                    ?.toFloat(),
            )
        },
        lines = pages.sumOf { it.lines.size },
        words = pages.sumOf { page -> page.lines.sumOf { it.words.size } },
        confidence = known.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.toFloat(),
        elapsedMillis = elapsedMillis,
        blocks = blocks,
    )
}
