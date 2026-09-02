package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.bar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * The bar every screen below the welcome wears.
 *
 * It carries the navigation: a back affordance on the left when there is somewhere to go
 * back to, and at most one action on the right. There is no tab bar anywhere in this app —
 * the screens form a stack, and this is the only control that moves through it.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The bar owns its own inset. Material's TopAppBar does this for you; a
            // hand-rolled one draws under the clock and the battery unless it says so.
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = Theme.spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        if (onBack != null) {
            Text(
                text = "←",
                style = Theme.typography.displaySmall,
                color = Theme.color.ink,
                modifier = Modifier
                    .clip(Theme.corner.control)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 6.dp),
            )
        }
        Text(
            text = title,
            style = Theme.typography.title,
            color = Theme.color.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = Theme.typography.label,
                color = Theme.color.inkMuted,
                modifier = Modifier
                    .clip(Theme.corner.control)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
