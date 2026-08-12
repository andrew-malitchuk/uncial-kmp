package io.github.andrewmalitchuk.uncial.model.source.geometry

/**
 * How the page content is rotated relative to the raster.
 *
 * This is coarse page rotation in 90° steps, not the fine skew of a crooked scan —
 * for that see `OcrLine.skewDegrees`.
 */
public enum class Orientation {
    /** Text reads left-to-right, no rotation. */
    Up,

    /** Content is rotated 90° clockwise. */
    Right,

    /** Content is upside down. */
    Down,

    /** Content is rotated 90° counter-clockwise. */
    Left,
    ;

    public val degrees: Int
        get() = when (this) {
            Up -> 0
            Right -> 90
            Down -> 180
            Left -> 270
        }
}
