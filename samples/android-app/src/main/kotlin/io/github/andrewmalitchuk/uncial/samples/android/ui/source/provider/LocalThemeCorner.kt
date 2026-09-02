package io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeCorner

internal val LocalThemeCorner =
    staticCompositionLocalOf<ThemeCorner> { error("No ThemeCorner provided: wrap this in AppTheme") }
