package io.github.andrewmalitchuk.uncial.model.source.geometry

/** A width/height pair in page space, in the same top-down pixel units as [Point]. */
public data class Size(
    public val width: Float,
    public val height: Float,
) {
    /** `true` when either dimension is zero or negative, i.e. nothing can be laid out in it. */
    public val isEmpty: Boolean get() = width <= 0f || height <= 0f
}
