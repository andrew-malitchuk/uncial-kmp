package io.github.andrewmalitchuk.uncial.dist.core.summary

import io.github.andrewmalitchuk.uncial.dist.source.model.IosDocumentSummary
import io.github.andrewmalitchuk.uncial.dist.source.model.IosPageSummary
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.structure.source.reconstruction.DocumentStructure

/**
 * Reduces a recognized document to [IosDocumentSummary], structure included.
 *
 * `Confidence.isKnown` is checked rather than reading `value` directly: an unknown
 * confidence is `NaN`, and averaging it in would poison the mean silently — every
 * comparison against `NaN` is false, so the result would neither look wrong nor be right.
 */
internal fun OcrDocument.toIosSummary(): IosDocumentSummary {
    val known = pages.flatMap { page -> page.lines.map { it.confidence } }.filter { it.isKnown }
    return IosDocumentSummary(
        source = source.name,
        pageCount = pageCount,
        lines = pages.sumOf { it.lines.size },
        words = pages.sumOf { page -> page.lines.sumOf { it.words.size } },
        confidence = known.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.toFloat(),
        pages = pages.map { page ->
            val pageKnown = page.lines.map { it.confidence }.filter { it.isKnown }
            IosPageSummary(
                number = page.index + 1,
                lines = page.lines.size,
                confidence = pageKnown.takeIf { it.isNotEmpty() }
                    ?.map { it.value }
                    ?.average()
                    ?.toFloat(),
            )
        },
        blocks = DocumentStructure.reconstruct(this),
    )
}
