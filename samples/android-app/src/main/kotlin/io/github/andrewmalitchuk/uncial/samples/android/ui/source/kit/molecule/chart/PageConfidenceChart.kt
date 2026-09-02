package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * Per-page confidence, as bars.
 *
 * A page the engine gave no confidence for draws an empty track rather than a zero-height
 * bar — "not reported" and "reported as nothing" are different claims, and only one of
 * them is the engine's fault. Digital text-layer pages are the usual case: they are not a
 * guess, so there is nothing to be confident about.
 */
@Composable
fun PageConfidenceChart(pages: List<PageConfidence>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        pages.forEach { page ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // The track is always drawn, so a low bar reads as low rather than as
                    // a rendering accident.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(Theme.corner.small)
                            .background(Theme.color.surfaceVariant),
                    )
                    val fraction = page.confidence
                    if (fraction != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceIn(0.05f, 1f))
                                .clip(Theme.corner.small)
                                .background(Theme.color.accentMuted),
                        )
                    }
                }
                Text(
                    text = "p${page.pageNumber}",
                    style = Theme.typography.mono,
                    color = Theme.color.inkSubtle,
                )
            }
        }
    }
}
