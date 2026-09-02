package io.github.andrewmalitchuk.uncial.samples.android.ui.core

import androidx.compose.ui.graphics.Shape

/**
 * Corner shapes.
 *
 * @param panel the largest radius: sheets and full-width panels.
 * @param card cards and list items.
 * @param small previews and inline figures.
 * @param control buttons, chips and segments — a stadium, not a rounded rectangle.
 */
data class ThemeCorner(
    val panel: Shape,
    val card: Shape,
    val small: Shape,
    val control: Shape,
)
