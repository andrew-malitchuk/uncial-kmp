package io.github.andrewmalitchuk.uncial.samples.android.source.screen.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillButton
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillEmphasis

/**
 * The only screen that spends the gradient.
 *
 * It says one thing — everything happens on the device — and then gets out of the way.
 * Every screen after it is paper, because from there on the content is someone's document
 * and the chrome should not compete with it.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.color.heroGradient)
            .systemBarsPadding()
            .padding(horizontal = Theme.spacing.screen, vertical = Theme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.welcome_title),
            style = Theme.typography.display,
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.welcome_body),
            style = Theme.typography.body,
            color = Color.White.copy(alpha = 0.86f),
        )
        Spacer(Modifier.padding(top = Theme.spacing.row))
        PillButton(
            text = stringResource(R.string.action_get_started),
            onClick = onStart,
            emphasis = PillEmphasis.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
