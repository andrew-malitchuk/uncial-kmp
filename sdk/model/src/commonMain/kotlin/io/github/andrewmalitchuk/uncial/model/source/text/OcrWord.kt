package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox

/**
 * A single recognized word.
 *
 * Words are optional: they cost extra work in every engine, and a caller who only wants
 * reflowable text never looks at them. See `OcrOptions.includeWords`.
 */
public data class OcrWord(
    public val text: String,
    public val box: BoundingBox,
    public val confidence: Confidence = Confidence.Unknown,
)
