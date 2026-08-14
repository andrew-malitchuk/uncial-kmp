package io.github.andrewmalitchuk.uncial.core.source.engine

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * What an engine can do on this platform, right now, with these options.
 *
 * Uncial runs Tesseract on Android and the JVM but Apple Vision on iOS, and the
 * differences are not cosmetic: Vision reports no per-word language, Tesseract reports no
 * skew, their confidences are not comparable, and the language sets differ. An SDK that
 * hid this would force every caller to rediscover it in production.
 *
 * So the API states it instead. Read this before relying on a field of `OcrLine` being
 * populated. (PLAN.md §4, §7)
 *
 * @property engineId stable identifier, e.g. `tesseract` or `vision`.
 * @property engineVersion the engine's own version when it reports one.
 * @property languages the languages actually available — the intersection of what was
 *   requested with what this engine and its data can do, which may be **smaller** than
 *   `OcrOptions.languages`.
 * @property wordLevel per-word boxes are available when `OcrOptions.includeWords` is set.
 * @property confidence lines and words carry a real `Confidence`, not `Unknown`.
 * @property perLineLanguage `OcrLine.language` is populated. Only the JVM engine can do
 *   this: libtesseract exposes the recognition language per element through its C API,
 *   which the Tesseract4Android Java wrapper does not surface and Vision does not have at
 *   all. Where this is `false`, use `OcrLine.script` — derived from the text, so always
 *   available.
 * @property orientationDetection `OcrPage.orientation` is measured rather than assumed to
 *   be [io.github.andrewmalitchuk.uncial.model.geometry.Orientation.Up]. Same asymmetry as
 *   [perLineLanguage]: available through libtesseract's C API only.
 * @property skewDetection `OcrLine.skewDegrees` is measured rather than left at zero.
 */
public data class OcrCapabilities(
    public val engineId: String,
    public val engineVersion: String? = null,
    public val languages: List<OcrLanguage> = emptyList(),
    public val wordLevel: Boolean = false,
    public val confidence: Boolean = false,
    public val perLineLanguage: Boolean = false,
    public val orientationDetection: Boolean = false,
    public val skewDetection: Boolean = false,
) {
    /** `true` when one or more requested languages had to be dropped. */
    public fun droppedAnyOf(requested: List<OcrLanguage>): Boolean =
        requested.any { it !in languages }
}
