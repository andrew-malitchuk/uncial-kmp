package io.github.andrewmalitchuk.uncial.structure.core.fixture

import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage

internal fun page(index: Int, lines: List<OcrLine>): OcrPage =
    OcrPage(index = index, size = Size(PAGE_WIDTH, PAGE_HEIGHT), lines = lines)
