package io.github.andrewmalitchuk.uncial.samples.android.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeColor
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeCorner
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeSize
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeSpacing
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeTypography

/**
 * The one way to read a design token.
 *
 * ```
 * Text(text = title, style = Theme.typography.display, color = Theme.color.ink)
 * ```
 *
 * Components never name a colour, a size or a family directly. That is what makes the
 * palette a decision rather than a hundred decisions, and it is why this sample can carry
 * a design system at all without it becoming the thing being maintained.
 */
object Theme {

    val color: ThemeColor
        @Composable @ReadOnlyComposable get() = LocalThemeColor.current

    val typography: ThemeTypography
        @Composable @ReadOnlyComposable get() = LocalThemeTypography.current

    val spacing: ThemeSpacing
        @Composable @ReadOnlyComposable get() = LocalThemeSpacing.current

    val corner: ThemeCorner
        @Composable @ReadOnlyComposable get() = LocalThemeCorner.current

    val size: ThemeSize
        @Composable @ReadOnlyComposable get() = LocalThemeSize.current
}
