package io.github.andrewmalitchuk.uncial.samples.android.source.screen.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.core.text.label
import io.github.andrewmalitchuk.uncial.samples.android.source.model.DocumentSummary
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface.AppCard
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.text.SectionHeader
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.chart.PageConfidence
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.chart.PageConfidenceChart
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.BlockRow
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.stat.Stat
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.stat.StatRow

/**
 * The result, argued rather than dumped.
 *
 * A sample that printed `document.text` would prove nothing: the reason this SDK returns
 * pages of lines of words with boxes and confidences is that someone downstream needs
 * them. So the screen leads with the counts, then the per-page confidence, then the
 * reconstructed blocks — three views of the same object, none of which is a string.
 */
@Composable
fun DocumentScreen(
    document: DocumentSummary?,
    modifier: Modifier = Modifier,
) {
    if (document == null) {
        EmptyDocument(modifier)
        return
    }

    // Lazy, because a 300-page document is exactly the case this SDK exists for and
    // rendering every block eagerly would be the wrong demonstration.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Theme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
                modifier = Modifier.padding(top = Theme.spacing.card),
            ) {
                Text(
                    text = document.label,
                    style = Theme.typography.display,
                    color = Theme.color.ink,
                )
                Text(
                    text = stringResource(
                        R.string.document_meta,
                        document.pageCount,
                        document.source.label(),
                        document.elapsedMillis,
                    ),
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                )
            }
        }

        item {
            StatRow(
                stats = listOf(
                    Stat(value = "${document.lines}", label = stringResource(R.string.stat_lines)),
                    Stat(
                        // `—` rather than `0`: no words means the option was off or the
                        // engine cannot do it, which is not the same claim as "none found".
                        value = document.words.takeIf { it > 0 }?.toString() ?: "—",
                        label = stringResource(R.string.stat_words),
                    ),
                    Stat(
                        value = document.confidence?.let { "${(it * 100).toInt()}%" } ?: "—",
                        label = stringResource(R.string.stat_confidence),
                    ),
                ),
            )
        }

        item {
            SectionHeader(
                text = stringResource(R.string.section_confidence),
                trailing = stringResource(R.string.section_confidence_trailing),
                modifier = Modifier.padding(top = Theme.spacing.card),
            )
        }
        item {
            AppCard {
                PageConfidenceChart(
                    pages = document.pages.map { PageConfidence(it.number, it.confidence) },
                )
            }
        }

        item {
            SectionHeader(
                text = stringResource(R.string.section_structure),
                trailing = stringResource(R.string.section_structure_trailing, document.blocks.size),
                modifier = Modifier.padding(top = Theme.spacing.card),
            )
        }

        if (document.isEmpty) {
            item { EmptyResultCard() }
        }

        // No `key` on purpose. DocBlock carries no identity of its own -- it is a value,
        // and the same text can legitimately occur twice -- so any key would have to
        // include the index, which is what the positional default already is.
        items(document.blocks) { block -> Block(block) }

        item { Column(modifier = Modifier.padding(bottom = Theme.spacing.section)) {} }
    }
}
