package io.github.andrewmalitchuk.uncial.samples.android.ui.core

import androidx.compose.ui.unit.Dp

/**
 * Fixed sizes that more than one component has to agree on.
 *
 * @param control the height of a button, chip row or segmented control.
 * @param icon a leading icon's box inside a card.
 * @param hairline the one stroke width in the design.
 */
data class ThemeSize(
    val control: Dp,
    val icon: Dp,
    val hairline: Dp,
)
