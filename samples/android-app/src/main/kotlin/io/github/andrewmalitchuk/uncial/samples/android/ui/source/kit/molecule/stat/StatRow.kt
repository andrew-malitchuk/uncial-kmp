package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.stat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * The three numbers a recognized document is judged by.
 *
 * Figures use the display family: they are the result, not a caption about it. A stat
 * whose value is unknown is rendered as `—` by the caller rather than dropped, because a
 * missing tile would read as "this engine has no confidence" instead of "this engine does
 * not report one".
 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        stats.forEach { stat ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Theme.corner.card)
                    .background(Theme.color.surface, Theme.corner.card)
                    .border(Theme.size.hairline, Theme.color.outline, Theme.corner.card)
                    .padding(vertical = Theme.spacing.card),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
            ) {
                Text(text = stat.value, style = Theme.typography.figure, color = Theme.color.ink)
                Text(
                    text = stat.label,
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                )
            }
        }
    }
}
