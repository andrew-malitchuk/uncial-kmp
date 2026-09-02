package io.github.andrewmalitchuk.uncial.samples.android.ui.core

import androidx.compose.ui.text.TextStyle

/**
 * The type scale.
 *
 * Two families with one job each: a serif for display and for recognized text, a sans for
 * everything the UI says about it. Mixing them is how a reader tells the document from the
 * app.
 *
 * @param display screen titles, two lines, tight leading.
 * @param displaySmall the same voice at card size.
 * @param figure large numerals — the stat tiles.
 * @param title a card's own heading.
 * @param body running UI copy.
 * @param bodyStrong the emphasised line of a card.
 * @param label buttons, chips, and anything with a box around it.
 * @param caption metadata under a title.
 * @param mono figures that must line up: boxes, confidences, codes.
 * @param recognized text that came out of the SDK, deliberately not the UI's voice.
 */
data class ThemeTypography(
    val display: TextStyle,
    val displaySmall: TextStyle,
    val figure: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
    val recognized: TextStyle,
)
