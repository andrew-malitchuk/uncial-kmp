package io.github.andrewmalitchuk.uncial.samples.android.source.screen.document

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface.AppCard

/**
 * The empty result, stated as a result.
 *
 * A recognized document with no lines is a valid answer — an image-only PDF the engine
 * could make nothing of — and it is not the same thing as a failure. Showing a spinner
 * forever, or an error, would both be lies.
 */
@Composable
internal fun EmptyResultCard() {
    AppCard {
        Text(
            text = stringResource(R.string.document_empty_title),
            style = Theme.typography.bodyStrong,
            color = Theme.color.ink,
        )
        Text(
            text = stringResource(R.string.document_empty_body),
            style = Theme.typography.caption,
            color = Theme.color.inkSubtle,
            modifier = Modifier.padding(top = Theme.spacing.tight),
        )
    }
}
