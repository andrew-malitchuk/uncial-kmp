package io.github.andrewmalitchuk.uncial.structure.core.fixture

import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine

/**
 * Stacks lines down the page.
 *
 * @param gap vertical space between lines, as a multiple of line height. The default sits
 *   below `StructureOptions.paragraphGapRatio`, so consecutive lines stay in one paragraph
 *   unless a test asks otherwise.
 */
internal fun stack(
    vararg entries: Pair<String, Float>,
    gap: Float = 0.4f,
    startTop: Float = 100f,
): List<OcrLine> {
    var top = startTop
    return entries.map { (text, fontSize) ->
        val current = line(text, top, fontSize)
        top += fontSize * (1f + gap)
        current
    }
}
