package io.github.andrewmalitchuk.uncial.samples.android.ui.source.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeColor
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeCorner
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeSize
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeSpacing
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.ThemeTypography
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeColor
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeCorner
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeSize
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeSpacing
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.provider.LocalThemeTypography

// The palette, written once. Everything else in the app refers to these by role.
private val Ink = Color(0xFF141210)
private val InkMuted = Color(0xFF4A433C)
private val InkSubtle = Color(0xFF8B8177)
private val Cream = Color(0xFFF7F4ED)
private val CreamDeep = Color(0xFFEFE9DE)
private val Paper = Color(0xFFFFFDF9)
private val Line = Color(0xFFE4DDD0)
private val Tan = Color(0xFFDED2B8)
private val Ember = Color(0xFFE2561F)
private val Peach = Color(0xFFF6D7A9)

private val AppColor = ThemeColor(
    ink = Ink,
    inkMuted = InkMuted,
    inkSubtle = InkSubtle,
    canvas = Cream,
    surface = Paper,
    surfaceVariant = CreamDeep,
    selected = Tan,
    accent = Ember,
    accentMuted = Peach,
    outline = Line,
    onAccent = Color.White,
    onInk = Color.White,
    // The one loud surface in the app. Five stops rather than two, because the reference
    // is a photograph of an ember and a two-stop gradient reads as a colour swatch.
    heroGradient = Brush.verticalGradient(
        0.00f to Color(0xFFF7D5CD),
        0.26f to Color(0xFFF2B49F),
        0.56f to Color(0xFFDD6A2C),
        0.82f to Color(0xFF5A2A1B),
        1.00f to Color(0xFF20110C),
    ),
    // Warmth at the top of a screen that then settles into canvas, so it never competes
    // with recognized text further down.
    warmGradient = Brush.verticalGradient(
        0.00f to Color(0xFFF8DCAE),
        0.26f to Color(0xFFFAEED6),
        0.52f to Color(0xFFF8F4EC),
        1.00f to Cream,
    ),
)

private val AppTypography = ThemeTypography(
    display = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    figure = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 28.sp,
    ),
    title = TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    body = TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodyStrong = TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    label = TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    caption = TextStyle(
        fontFamily = Interface,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5f.sp,
        lineHeight = 15.sp,
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    recognized = TextStyle(
        fontFamily = Recognized,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
)

private val AppSpacing = ThemeSpacing(
    screen = 20.dp,
    section = 24.dp,
    card = 16.dp,
    row = 12.dp,
    tight = 4.dp,
)

private val AppCorner = ThemeCorner(
    panel = RoundedCornerShape(26.dp),
    card = RoundedCornerShape(18.dp),
    small = RoundedCornerShape(12.dp),
    // A stadium: the radius is deliberately larger than any control is tall.
    control = RoundedCornerShape(percent = 50),
)

private val AppSize = ThemeSize(
    control = 46.dp,
    icon = 38.dp,
    hairline = 1.dp,
)

/**
 * Provides every design token, plus the Material scheme built out of the same values.
 *
 * Material3 is still underneath — `Scaffold`, `NavigationBar` and `Snackbar` all read
 * `MaterialTheme.colorScheme`, and leaving it at its defaults would put purple chrome
 * around a warm paper palette. So the scheme is **derived** from the tokens rather than
 * living beside them: one palette, two consumers.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = AppColor.ink,
        onPrimary = AppColor.onInk,
        secondary = AppColor.accent,
        onSecondary = AppColor.onAccent,
        secondaryContainer = AppColor.selected,
        onSecondaryContainer = AppColor.ink,
        background = AppColor.canvas,
        onBackground = AppColor.ink,
        surface = AppColor.surface,
        onSurface = AppColor.ink,
        surfaceVariant = AppColor.surfaceVariant,
        onSurfaceVariant = AppColor.inkMuted,
        outline = AppColor.outline,
        outlineVariant = AppColor.outline,
        error = AppColor.accent,
        onError = AppColor.onAccent,
    )
    CompositionLocalProvider(
        LocalThemeColor provides AppColor,
        LocalThemeTypography provides AppTypography,
        LocalThemeSpacing provides AppSpacing,
        LocalThemeCorner provides AppCorner,
        LocalThemeSize provides AppSize,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
