package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * A small piece of state with a box around it.
 *
 * [ChipTone.Unavailable] exists because the whole point of the SDK's capability reporting
 * is that platforms differ; a UI that hides what it cannot do is the UI this sample is
 * arguing against.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
) {
    val color = Theme.color
    val shape = Theme.corner.control

    val background = when (tone) {
        ChipTone.Neutral -> color.surface
        ChipTone.Selected -> color.selected
        ChipTone.Accent -> color.accentMuted
        ChipTone.Unavailable -> color.surfaceVariant
    }
    val foreground = when (tone) {
        ChipTone.Neutral, ChipTone.Selected -> color.ink
        ChipTone.Accent -> color.accent
        ChipTone.Unavailable -> color.inkSubtle
    }
    val outline = when (tone) {
        ChipTone.Neutral -> color.outline
        else -> Color.Transparent
    }

    Text(
        text = text,
        style = Theme.typography.caption,
        color = foreground,
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(Theme.size.hairline, outline, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
