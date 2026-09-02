package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * The label that names a group of cards, with an optional fact on the right.
 *
 * The trailing slot is usually where a screen admits something — how many pages, which
 * engine, what was dropped.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = text, style = Theme.typography.title, color = Theme.color.ink)
        if (trailing != null) {
            Text(text = trailing, style = Theme.typography.mono, color = Theme.color.inkSubtle)
        }
    }
}
