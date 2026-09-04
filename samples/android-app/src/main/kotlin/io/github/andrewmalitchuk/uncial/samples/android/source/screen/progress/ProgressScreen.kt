package io.github.andrewmalitchuk.uncial.samples.android.source.screen.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.core.text.label
import io.github.andrewmalitchuk.uncial.samples.android.source.model.RunState
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillButton
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillEmphasis

/**
 * What the SDK is doing right now, page by page.
 *
 * The count is the honest unit of progress: `extractAsFlow` emits once per page, and a
 * page is also the granularity at which cancellation lands. The ring is indeterminate
 * until `OcrProgress.Started` has said how many pages there are — an image knows
 * immediately, a PDF does not.
 */
@Composable
fun ProgressScreen(
    run: RunState.Running,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Theme.spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val fraction = run.fraction
            if (fraction == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(132.dp),
                    color = Theme.color.accent,
                    trackColor = Theme.color.surfaceVariant,
                    strokeWidth = 6.dp,
                )
            } else {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(132.dp),
                    color = Theme.color.accent,
                    trackColor = Theme.color.surfaceVariant,
                    strokeWidth = 6.dp,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (run.total > 0) "${run.completed}" else "…",
                    style = Theme.typography.display,
                    color = Theme.color.ink,
                )
                Text(
                    text = if (run.total > 0) {
                        stringResource(R.string.progress_of_pages, run.total)
                    } else {
                        stringResource(R.string.progress_opening)
                    },
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                )
            }
        }

        Text(
            text = run.label,
            style = Theme.typography.bodyStrong,
            color = Theme.color.ink,
            modifier = Modifier.padding(top = Theme.spacing.section),
        )
        // Only once `Started` has said. Before that the path is genuinely not known --
        // a PDF with a text layer never reaches the engine, and that is decided after the
        // document has been opened.
        run.source?.let { source ->
            Text(
                text = source.label(),
                style = Theme.typography.mono,
                color = Theme.color.inkSubtle,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Theme.spacing.row),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            run.lastPageLines.forEach { line ->
                Text(
                    text = line,
                    style = Theme.typography.caption,
                    color = Theme.color.inkSubtle,
                    textAlign = TextAlign.Center,
                )
            }
        }

        PillButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            emphasis = PillEmphasis.Ghost,
            modifier = Modifier.padding(top = Theme.spacing.section),
        )
        Text(
            text = stringResource(R.string.progress_cancel_note),
            style = Theme.typography.caption,
            color = Theme.color.inkSubtle,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Theme.spacing.tight),
        )
    }
}
