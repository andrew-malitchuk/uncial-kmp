package io.github.andrewmalitchuk.uncial.samples.android.ui.core

import androidx.compose.ui.unit.Dp

/**
 * The 4 pt grid, named by role rather than by number.
 *
 * @param screen the horizontal inset every screen shares.
 * @param section the gap between two sections of a screen.
 * @param card a card's internal padding.
 * @param row the gap between rows inside a card.
 * @param tight the gap between a label and the thing it labels.
 */
data class ThemeSpacing(
    val screen: Dp,
    val section: Dp,
    val card: Dp,
    val row: Dp,
    val tight: Dp,
)
