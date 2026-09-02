package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * A surface that lies on the canvas rather than floating above it.
 *
 * No elevation anywhere in this design: the separation is a hairline and a lighter fill.
 * Shadows would fight the warm background, and on a paper palette they read as dirt.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Theme.corner.card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.color.surface, shape)
            .border(Theme.size.hairline, Theme.color.outline, shape)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(Theme.spacing.card),
        content = content,
    )
}
