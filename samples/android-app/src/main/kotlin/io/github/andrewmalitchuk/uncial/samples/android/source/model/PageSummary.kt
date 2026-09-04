package io.github.andrewmalitchuk.uncial.samples.android.source.model

/** One page's contribution to the document screen. */
data class PageSummary(
    val number: Int,
    val lines: Int,
    /** `null` when the engine reported no confidence — a digital text layer never guesses. */
    val confidence: Float?,
)
