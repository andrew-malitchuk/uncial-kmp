package io.github.andrewmalitchuk.uncial.samples.android.ui.source.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.andrewmalitchuk.uncial.samples.android.R

/**
 * Inter, for the interface.
 *
 * Downloaded with the Cyrillic subset, which is not the default: the sample's own copy is
 * English, but a file name, an error message or a recognized string can be Ukrainian, and
 * the Latin subset renders that as tofu.
 */
val Interface: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
