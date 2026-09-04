package io.github.andrewmalitchuk.uncial.samples.android.source.screen.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

@Composable
internal fun EmptyDocument(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Theme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        Text(
            text = stringResource(R.string.document_none_title),
            style = Theme.typography.display,
            color = Theme.color.ink,
        )
        Text(
            text = stringResource(R.string.document_none_body),
            style = Theme.typography.body,
            color = Theme.color.inkMuted,
        )
    }
}
