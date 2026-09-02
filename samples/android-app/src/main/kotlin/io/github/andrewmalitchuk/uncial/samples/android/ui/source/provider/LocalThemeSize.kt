package io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeSize

internal val LocalThemeSize =
    staticCompositionLocalOf<ThemeSize> { error("No ThemeSize provided: wrap this in AppTheme") }
