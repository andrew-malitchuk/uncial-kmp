package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface.AppCard

/**
 * One reconstructed block, labelled with what `DocumentStructure` decided it was.
 *
 * The `H1` / `¶` glyph is the point of the screen: the SDK returns structure, and a sample
 * that rendered the blocks as undifferentiated paragraphs would be hiding its own result.
 * The text itself is rendered in the serif — it is the document speaking, not the app.
 */
@Composable
fun BlockRow(
    glyph: String,
    text: String,
    meta: String,
    modifier: Modifier = Modifier,
    isHeading: Boolean = false,
) {
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(Theme.corner.small)
                    .background(if (isHeading) Theme.color.selected else Theme.color.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = glyph, style = Theme.typography.mono, color = Theme.color.inkMuted)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Theme.spacing.row),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
            ) {
                Text(
                    text = text,
                    style = Theme.typography.recognized,
                    fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                    color = Theme.color.ink,
                )
                Text(text = meta, style = Theme.typography.mono, color = Theme.color.inkSubtle)
            }
        }
    }
}
