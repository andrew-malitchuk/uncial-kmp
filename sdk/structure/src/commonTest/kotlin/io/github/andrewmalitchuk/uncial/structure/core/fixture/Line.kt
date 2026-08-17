package io.github.andrewmalitchuk.uncial.structure.core.fixture

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine

/** One line at a specific vertical offset. */
internal fun line(
    text: String,
    top: Float,
    fontSize: Float = BODY_SIZE,
    height: Float = fontSize,
    left: Float = LINE_LEFT,
): OcrLine = OcrLine(
    text = text,
    box = BoundingBox(left = left, top = top, width = text.length * 7f, height = height),
    fontSize = fontSize,
)
