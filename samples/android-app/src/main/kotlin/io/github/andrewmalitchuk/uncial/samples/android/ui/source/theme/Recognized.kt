package io.github.andrewmalitchuk.uncial.samples.android.ui.source.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * The family recognized text is rendered in — the platform's serif, not Fraunces.
 *
 * Fraunces has no Cyrillic. The design asks for recognized text to read as a *document*
 * rather than as UI, and on a Ukrainian scan the system serif (Noto Serif) is the family
 * that can actually do both.
 */
val Recognized: FontFamily = FontFamily.Serif
