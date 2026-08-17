package io.github.andrewmalitchuk.uncial.structure.core.fixture

import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument

/** A single-page document from `text to fontSize` pairs. */
internal fun singlePage(vararg entries: Pair<String, Float>, gap: Float = 0.4f): OcrDocument =
    document(page(0, stack(*entries, gap = gap)))
