package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme

/**
 * The only button in this app.
 *
 * Three emphases rather than three components, because they differ in colour and nothing
 * else: same height, same stadium, same label style. A screen that needs a fourth kind of
 * button usually needs one fewer action instead.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: PillEmphasis = PillEmphasis.Primary,
    enabled: Boolean = true,
) {
    val color = Theme.color
    val shape = Theme.corner.control

    val background = when (emphasis) {
        PillEmphasis.Primary -> color.ink
        PillEmphasis.Secondary -> color.surface
        PillEmphasis.Ghost -> Color.Transparent
    }
    val content = when (emphasis) {
        PillEmphasis.Primary -> color.onInk
        PillEmphasis.Secondary -> color.ink
        PillEmphasis.Ghost -> color.inkMuted
    }
    val border = when (emphasis) {
        PillEmphasis.Primary -> null
        else -> BorderStroke(Theme.size.hairline, color.outline)
    }

    Box(
        modifier = modifier
            .height(Theme.size.control)
            .clip(shape)
            .background(if (enabled) background else color.surfaceVariant, shape)
            .then(if (border == null) Modifier else Modifier.border(border, shape))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // The ripple is the one Material behaviour worth keeping here: a custom
                // press state would have to re-teach users what a tap feels like.
                indication = ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = Theme.typography.label,
                color = if (enabled) content else color.inkSubtle,
            )
        }
    }
}
