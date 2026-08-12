package io.github.andrewmalitchuk.uncial.model.source.geometry

/**
 * An axis-aligned rectangle in top-down page space.
 *
 * @property left distance from the page's left edge.
 * @property top distance from the page's **top** edge (not the bottom — see [Point]).
 */
public data class BoundingBox(
    public val left: Float,
    public val top: Float,
    public val width: Float,
    public val height: Float,
) {
    /** X coordinate of the right edge. */
    public val right: Float get() = left + width

    /** Y coordinate of the bottom edge; larger than [top] because `y` grows downwards. */
    public val bottom: Float get() = top + height

    /** Geometric centre, useful for reading-order and column heuristics. */
    public val center: Point get() = Point(left + width / 2f, top + height / 2f)

    public val area: Float get() = width * height

    /** The smallest box containing both this box and [other]. */
    public fun union(other: BoundingBox): BoundingBox {
        val l = minOf(left, other.left)
        val t = minOf(top, other.top)
        return BoundingBox(
            left = l,
            top = t,
            width = maxOf(right, other.right) - l,
            height = maxOf(bottom, other.bottom) - t,
        )
    }

    public companion object {
        /** A degenerate box at the origin, used where an engine reports no geometry. */
        public val Zero: BoundingBox = BoundingBox(0f, 0f, 0f, 0f)
    }
}
