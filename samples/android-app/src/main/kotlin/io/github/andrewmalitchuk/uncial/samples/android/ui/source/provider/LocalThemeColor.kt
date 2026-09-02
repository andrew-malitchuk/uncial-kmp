package io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeColor

// `staticCompositionLocalOf`, not `compositionLocalOf`: these never change after AppTheme
// provides them, and a static local skips the per-read invalidation bookkeeping. The
// failing default is deliberate -- reading a token outside AppTheme is a bug, and a loud
// one beats a component silently rendering in some default palette.

internal val LocalThemeColor =
    staticCompositionLocalOf<ThemeColor> { error("No ThemeColor provided: wrap this in AppTheme") }
