package io.github.andrewmalitchuk.uncial.samples.android.source.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.Chip
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.ChipTone
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface.AppCard
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.text.SectionHeader
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.Capability
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.CapabilityList

/**
 * What this device can actually do — the screen the whole design exists to justify.
 *
 * Uncial runs Tesseract here and Apple Vision on iOS, and the two disagree about more than
 * speed. `OcrCapabilities` is the SDK refusing to paper over that, so the sample gives it
 * a screen rather than a footnote.
 */
@Composable
fun DeviceScreen(
    capabilities: OcrCapabilities?,
    hasDigitalTextLayer: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        Text(
            text = stringResource(R.string.device_title),
            style = Theme.typography.display,
            color = Theme.color.ink,
            modifier = Modifier.padding(top = Theme.spacing.card),
        )
        Text(
            text = stringResource(R.string.device_body),
            style = Theme.typography.body,
            color = Theme.color.inkMuted,
        )

        if (capabilities == null) {
            AppCard {
                Text(
                    text = stringResource(R.string.no_engine),
                    style = Theme.typography.bodyStrong,
                    color = Theme.color.accent,
                )
                Text(
                    text = stringResource(R.string.no_engine_body),
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                    modifier = Modifier.padding(top = Theme.spacing.tight),
                )
            }
            return@Column
        }

        SectionHeader(
            text = stringResource(R.string.section_engine),
            modifier = Modifier.padding(top = Theme.spacing.card),
        )
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight)) {
                Text(
                    text = capabilities.engineId,
                    style = Theme.typography.displaySmall,
                    color = Theme.color.ink,
                )
                Text(
                    text = stringResource(
                        R.string.engine_meta,
                        capabilities.engineVersion ?: stringResource(R.string.unknown_version),
                    ),
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
                modifier = Modifier.padding(top = Theme.spacing.row),
            ) {
                capabilities.languages.forEach { language ->
                    Chip(text = language.tesseractCode, tone = ChipTone.Selected)
                }
                Chip(
                    text = stringResource(
                        if (hasDigitalTextLayer) {
                            R.string.chip_digital_layer
                        } else {
                            R.string.chip_no_digital_layer
                        },
                    ),
                    tone = if (hasDigitalTextLayer) ChipTone.Accent else ChipTone.Unavailable,
                )
            }
            Text(
                text = stringResource(R.string.engine_languages_note),
                style = Theme.typography.caption,
                color = Theme.color.inkSubtle,
                modifier = Modifier.padding(top = Theme.spacing.row),
            )
        }

        SectionHeader(
            text = stringResource(R.string.section_matrix),
            modifier = Modifier.padding(top = Theme.spacing.card),
        )
        AppCard {
            CapabilityList(
                capabilities = listOf(
                    Capability(
                        name = stringResource(R.string.capability_words),
                        supported = capabilities.wordLevel,
                        note = stringResource(R.string.capability_words_note),
                    ),
                    Capability(
                        name = stringResource(R.string.capability_confidence),
                        supported = capabilities.confidence,
                    ),
                    Capability(
                        name = stringResource(R.string.capability_language),
                        supported = capabilities.perLineLanguage,
                        note = stringResource(R.string.capability_language_note),
                    ),
                    Capability(
                        name = stringResource(R.string.capability_orientation),
                        supported = capabilities.orientationDetection,
                    ),
                    Capability(
                        name = stringResource(R.string.capability_skew),
                        supported = capabilities.skewDetection,
                    ),
                ),
            )
        }
        Text(
            text = stringResource(R.string.matrix_note),
            style = Theme.typography.caption,
            color = Theme.color.inkSubtle,
            modifier = Modifier.padding(bottom = Theme.spacing.section),
        )
    }
}
