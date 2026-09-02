package io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeTypography

internal val LocalThemeTypography =
    staticCompositionLocalOf<ThemeTypography> { error("No ThemeTypography provided: wrap this in AppTheme") }
