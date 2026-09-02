package io.github.andrewmalitchuk.uncial.samples.android.ui.core

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// The design system's token groups, one data class each.
//
// Same shape as the kit this sample borrows from: values live in `AppTheme`, are carried
// down by CompositionLocals, and are read through the `Theme` accessor. Nothing here has
// values -- a token group is a vocabulary, and the palette that fills it is a decision
// made in exactly one place.

/**
 * The palette.
 *
 * Warm and paper-like on purpose: recognized text is the content, so the chrome stays
 * quiet. The single gradient ([heroGradient]) is the only loud surface in the app, and it
 * appears on exactly one screen.
 *
 * @param ink primary text and the CTA fill.
 * @param inkMuted secondary text: captions, supporting copy.
 * @param inkSubtle tertiary text: metadata, monospace figures.
 * @param canvas the screen background.
 * @param surface cards and panels sitting on [canvas].
 * @param surfaceVariant a recessed surface — segmented tracks, unselected fills.
 * @param selected the selected state of a chip or segment.
 * @param accent the one saturated colour: active badges, the OCR path.
 * @param accentMuted a softer accent for fills behind [accent] text.
 * @param outline hairlines. One weight, one colour, everywhere.
 * @param onAccent text drawn on [accent].
 * @param onInk text drawn on [ink] — the dark pill button's label.
 * @param heroGradient the welcome screen's ember wash.
 * @param warmGradient the top-of-screen warmth used where a screen needs a little of it.
 */
data class ThemeColor(
    val ink: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    val canvas: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val selected: Color,
    val accent: Color,
    val accentMuted: Color,
    val outline: Color,
    val onAccent: Color,
    val onInk: Color,
    val heroGradient: Brush,
    val warmGradient: Brush,
)
