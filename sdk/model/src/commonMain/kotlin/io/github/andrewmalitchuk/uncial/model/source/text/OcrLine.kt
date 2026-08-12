package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * One line of recognized text — the unit every engine agrees on, and the unit
 * `DocumentStructure` reasons about.
 *
 * @property text the line's text, already trimmed.
 * @property box the line's bounds in top-down page pixels.
 * @property fontSize an estimate of the type size. For OCR this is a proxy derived from
 *   the line's height, not a real font metric; for the digital text layer it is the actual
 *   size. Structure heuristics compare it against the document's median, so the proxy is
 *   good enough — but it is not a number to display to a user.
 * @property words the line's words when the engine produced them and the caller asked
 *   for them; empty otherwise. An empty list means "not requested or not supported",
 *   never "the line has no words".
 * @property language the language the engine attributed to this line, when it reports one.
 *   Vision does not attribute per line; Tesseract can when given multiple languages.
 * @property skewDegrees the line's rotation from horizontal, positive clockwise. `0` for a
 *   straight line and for engines that do not measure it.
 */
public data class OcrLine(
    public val text: String,
    public val box: BoundingBox,
    public val fontSize: Float,
    public val confidence: Confidence = Confidence.Unknown,
    public val words: List<OcrWord> = emptyList(),
    public val language: OcrLanguage? = null,
    public val skewDegrees: Float = 0f,
) {
    /** The writing system of [text], classified on demand. */
    public val script: TextScript get() = TextScript.of(text)
}
