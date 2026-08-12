package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.geometry.Orientation
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size

/**
 * One page of a recognized document.
 *
 * @property index zero-based page index in the source document.
 * @property size the page's size **in the same pixel units as every [OcrLine.box] on it**.
 *   This is the rasterized size, which depends on `OcrOptions.renderDpi` — it is not the
 *   PDF's point size. Divide by it to get normalized coordinates.
 * @property lines recognized lines, in the order the engine produced them, which is
 *   usually but not guaranteed to be reading order.
 */
public data class OcrPage(
    public val index: Int,
    public val size: Size,
    public val lines: List<OcrLine>,
    public val orientation: Orientation = Orientation.Up,
    public val source: ExtractionSource = ExtractionSource.Ocr,
) {
    public val isEmpty: Boolean get() = lines.isEmpty()

    /** The page's text, one line per line, in engine order. */
    public val text: String get() = lines.joinToString("\n") { it.text }
}
