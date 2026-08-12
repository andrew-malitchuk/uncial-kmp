package io.github.andrewmalitchuk.uncial.model.source.geometry

/**
 * A point in page space.
 *
 * Uncial uses a single coordinate convention everywhere: **top-down pixels**.
 * The origin `(0, 0)` is the top-left corner of the rasterized page and `y` grows
 * downwards. Every engine normalizes into this convention, so a caller never has to
 * know whether the numbers came from Tesseract (already top-down) or Vision
 * (normalized, bottom-up).
 */
public data class Point(
    public val x: Float,
    public val y: Float,
)
