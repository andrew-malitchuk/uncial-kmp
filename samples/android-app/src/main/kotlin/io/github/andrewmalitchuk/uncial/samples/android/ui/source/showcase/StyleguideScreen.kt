package io.github.andrewmalitchuk.uncial.samples.android.ui.source.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillButton
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillEmphasis
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.Chip
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.ChipTone
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface.AppCard
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.text.SectionHeader
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.chart.PageConfidence
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.chart.PageConfidenceChart
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.BlockRow
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.Capability
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.CapabilityList
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.SourceCard
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.stat.Stat
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.stat.StatRow
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.theme.AppTheme

/**
 * Every component in the kit on one page, for the preview pane.
 *
 * Not reachable from the app. It exists so a change to a token can be judged against the
 * whole inventory rather than against whichever screen happened to be open — the same job
 * the style guide this design came from does on the web.
 */
@Composable
fun StyleguideScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.color.canvas)
            .verticalScroll(rememberScrollState())
            .padding(Theme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        Text("Type", style = Theme.typography.display, color = Theme.color.ink)
        Text("Display / Fraunces", style = Theme.typography.displaySmall, color = Theme.color.ink)
        Text("Body / Inter", style = Theme.typography.body, color = Theme.color.inkMuted)
        Text("Recognized / serif — Розділ 1. Підсумки", style = Theme.typography.recognized)
        Text("mono · box = (72.0, 318.5)", style = Theme.typography.mono, color = Theme.color.inkSubtle)

        SectionHeader(text = "Buttons", trailing = "one component, three emphases")
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.row)) {
            PillButton("Primary", {}, Modifier.weight(1f))
            PillButton("Secondary", {}, Modifier.weight(1f), PillEmphasis.Secondary)
            PillButton("Ghost", {}, Modifier.weight(1f), PillEmphasis.Ghost)
        }

        SectionHeader(text = "Chips")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.tight)) {
            Chip("ukr", tone = ChipTone.Selected)
            Chip("eng", tone = ChipTone.Selected)
            Chip("OCR", tone = ChipTone.Accent)
            Chip("fast path")
            Chip("deu · unavailable", tone = ChipTone.Unavailable)
        }

        SectionHeader(text = "Stats")
        StatRow(
            stats = listOf(
                Stat("31", "LINES"),
                Stat("268", "WORDS"),
                Stat("94%", "CONF"),
            ),
        )

        SectionHeader(text = "Source card")
        SourceCard(
            glyph = "▦",
            title = "Scanned fixture",
            subtitle = "Image-only Ukrainian PDF · bundled",
            badge = "OCR",
            badgeTone = ChipTone.Accent,
            onClick = {},
        )

        SectionHeader(text = "Confidence")
        AppCard {
            PageConfidenceChart(
                pages = listOf(
                    PageConfidence(1, 0.94f),
                    PageConfidence(2, 0.88f),
                    PageConfidence(3, null),
                    PageConfidence(4, 0.71f),
                ),
            )
        }

        SectionHeader(text = "Blocks")
        BlockRow(
            glyph = "H1",
            text = "Розділ 1. Підсумки",
            meta = "DocBlock.Heading · level 1",
            isHeading = true,
        )
        BlockRow(
            glyph = "¶",
            text = "За звітний період обсяг виконаних робіт зріс на 12 %…",
            meta = "DocBlock.Paragraph",
        )

        SectionHeader(text = "Capabilities")
        AppCard {
            CapabilityList(
                capabilities = listOf(
                    Capability("Word boxes", supported = true),
                    Capability("Per-line language", supported = false, note = "Use OcrLine.script."),
                ),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Theme.spacing.section)) {}
    }
}

@Preview(showBackground = true, heightDp = 1700)
@Composable
private fun StyleguidePreview() {
    AppTheme { StyleguideScreen() }
}
