package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * The capability matrix, rendered as rows rather than as a grid.
 *
 * `no` is written out, not left blank. An empty cell reads as "the designer ran out of
 * room"; the word reads as the answer, which is the entire reason `OcrCapabilities`
 * exists.
 */
@Composable
fun CapabilityList(capabilities: List<Capability>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        capabilities.forEachIndexed { index, capability ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = Theme.size.hairline,
                    color = Theme.color.outline,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Theme.spacing.row),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
            ) {
                // Name and answer on one line, note underneath across the full width.
                // Putting the answer beside a two-line note instead centres it between
                // those lines, where it reads as a word inside the sentence.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = capability.name,
                        style = Theme.typography.bodyStrong,
                        color = Theme.color.ink,
                    )
                    Text(
                        text = if (capability.supported) "yes" else "no",
                        style = Theme.typography.label,
                        color = if (capability.supported) {
                            Theme.color.ink
                        } else {
                            Theme.color.inkSubtle
                        },
                    )
                }
                capability.note?.let {
                    Text(
                        text = it,
                        style = Theme.typography.caption,
                        color = Theme.color.inkSubtle,
                    )
                }
            }
        }
    }
}
