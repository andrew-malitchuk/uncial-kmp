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
import androidx.compose.ui.text.style.TextOverflow
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.Chip
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.ChipTone
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface.AppCard

/**
 * One thing the user can hand to the SDK.
 *
 * The badge is the card's whole argument: `OCR` and `fast path` are not decoration, they
 * are which code path the tap will take, stated before the tap rather than explained
 * after it.
 */
@Composable
fun SourceCard(
    glyph: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeTone: ChipTone = ChipTone.Neutral,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AppCard(modifier = modifier, onClick = onClick.takeIf { enabled }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Theme.size.icon)
                    .clip(Theme.corner.small)
                    .background(Theme.color.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = glyph, style = Theme.typography.title, color = Theme.color.inkMuted)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Theme.spacing.row),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
            ) {
                Text(
                    text = title,
                    style = Theme.typography.bodyStrong,
                    color = if (enabled) Theme.color.ink else Theme.color.inkSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) Chip(text = badge, tone = badgeTone)
        }
    }
}
