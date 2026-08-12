package io.github.andrewmalitchuk.uncial.model.source.text

import kotlin.jvm.JvmInline

/**
 * How sure the engine is about a piece of recognized text, normalized to `0.0..1.0`.
 *
 * Engines report confidence on different scales — Tesseract uses `0..100`, Vision uses
 * `0..1` — and some paths report none at all (a PDF's digital text layer is not a guess,
 * and a custom engine may simply not expose it). Rather than making every field nullable
 * and forcing boxing on a value class, an unavailable confidence is represented by
 * [Unknown], and [isKnown] tells the two apart.
 *
 * ```
 * val c = line.confidence
 * if (c.isKnown && c.value < 0.6f) flagForReview(line)
 * ```
 */
@JvmInline
public value class Confidence private constructor(
    /**
     * The confidence in `0.0..1.0`, or `NaN` when unknown.
     *
     * Reading this without checking [isKnown] is legal but propagates `NaN` through any
     * arithmetic, which is usually the wrong kind of silent.
     */
    public val value: Float,
) : Comparable<Confidence> {

    /** `false` when the engine reported no confidence for this element. */
    public val isKnown: Boolean get() = !value.isNaN()

    /** The confidence, or [fallback] when the engine reported none. */
    public fun orElse(fallback: Float): Float = if (isKnown) value else fallback

    /**
     * Orders known confidences numerically. [Unknown] sorts below every known value, so
     * `minOf(...)` over a line's words does not silently return `NaN`.
     */
    override fun compareTo(other: Confidence): Int = when {
        isKnown && other.isKnown -> value.compareTo(other.value)
        isKnown -> 1
        other.isKnown -> -1
        else -> 0
    }

    override fun toString(): String = if (isKnown) "Confidence($value)" else "Confidence(unknown)"

    public companion object {
        /** The engine reported no confidence for this element. */
        public val Unknown: Confidence = Confidence(Float.NaN)

        /** Full confidence, used by paths that are not guessing — e.g. a digital text layer. */
        public val Certain: Confidence = Confidence(1f)

        /**
         * Wraps an already-normalized confidence, clamping to `0.0..1.0`.
         *
         * `NaN` in means [Unknown] out, so a missing engine value cannot masquerade as a
         * real one.
         */
        public fun of(normalized: Float): Confidence = when {
            normalized.isNaN() -> Unknown
            // `+ 0f` folds negative zero onto positive zero. coerceIn passes `-0.0f`
            // through untouched, and equality on a value class over Float is total order
            // -- the same rule that makes Unknown == Unknown despite being NaN -- so
            // without this, `of(-0.0f) != of(0.0f)` and their hash codes differ too. An
            // engine reporting a rounded-down negative confidence is enough to hit it.
            // Every other value is unaffected: adding zero is exact.
            else -> Confidence(normalized.coerceIn(0f, 1f) + 0f)
        }

        /** Converts a Tesseract-style `0..100` confidence. */
        public fun ofPercent(percent: Float): Confidence =
            if (percent.isNaN() || percent < 0f) Unknown else of(percent / 100f)
    }
}
