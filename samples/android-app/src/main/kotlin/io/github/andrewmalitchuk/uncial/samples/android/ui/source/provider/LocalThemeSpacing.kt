package io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeSpacing

internal val LocalThemeSpacing =
    staticCompositionLocalOf<ThemeSpacing> { error("No ThemeSpacing provided: wrap this in AppTheme") }
