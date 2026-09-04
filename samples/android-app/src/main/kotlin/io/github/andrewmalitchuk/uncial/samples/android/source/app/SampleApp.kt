package io.github.andrewmalitchuk.uncial.samples.android.source.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.source.model.OcrUiState
import io.github.andrewmalitchuk.uncial.samples.android.source.model.RunState
import io.github.andrewmalitchuk.uncial.samples.android.source.screen.device.DeviceScreen
import io.github.andrewmalitchuk.uncial.samples.android.source.screen.document.DocumentScreen
import io.github.andrewmalitchuk.uncial.samples.android.source.screen.home.HomeScreen
import io.github.andrewmalitchuk.uncial.samples.android.source.screen.progress.ProgressScreen
import io.github.andrewmalitchuk.uncial.samples.android.source.screen.welcome.WelcomeScreen
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.bar.AppTopBar

/**
 * The whole app: five destinations and the rules for moving between them.
 *
 * A stack, not tabs. `Home` is the root and everything else is entered from it and backed
 * out of — which is also what the system back gesture does, through [BackHandler]. The
 * depth is never more than one, so the "stack" is a single destination plus the rule that
 * back means Home; a navigation library would be more machinery than the graph has.
 *
 * `rememberSaveable`, so a rotation does not send the user back to the welcome screen; the
 * ViewModel holds the document across the same event.
 */
@Composable
fun SampleApp(
    state: OcrUiState,
    onExtractScan: () -> Unit,
    onExtractDigital: () -> Unit,
    onExtractPdf: (Uri) -> Unit,
    onExtractImage: (Uri) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(SampleDestination.Welcome) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Starting an extraction is what navigates: the user tapped a source, so the progress
    // screen is where they now are. Doing it here rather than in each callback keeps the
    // rule in one place -- including for a PDF another app sent us, which starts a run
    // without anyone tapping anything.
    LaunchedEffect(state.run) {
        when (state.run) {
            is RunState.Running -> destination = SampleDestination.Progress
            RunState.Idle -> if (destination == SampleDestination.Progress) {
                // A finished run has a document; a cancelled one does not, and sending the
                // user to an empty document screen would read as a failure.
                destination = if (state.document != null) {
                    SampleDestination.Document
                } else {
                    SampleDestination.Home
                }
            }
        }
    }

    LaunchedEffect(state.error) {
        // A snackbar rather than an inline error: the failure belongs to the run that just
        // ended, not to the screen the user is now looking at.
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    // Back leaves whatever was entered from Home. On the progress screen it also cancels,
    // because leaving a run running and unreachable would be the dishonest option.
    BackHandler(enabled = destination != SampleDestination.Home && destination != SampleDestination.Welcome) {
        if (destination == SampleDestination.Progress) onCancel()
        destination = SampleDestination.Home
    }

    if (destination == SampleDestination.Welcome) {
        WelcomeScreen(
            onStart = { destination = SampleDestination.Home },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Theme.color.canvas,
        topBar = {
            when (destination) {
                SampleDestination.Home -> AppTopBar(
                    title = stringResource(R.string.app_name),
                    action = stringResource(R.string.action_device),
                    onAction = { destination = SampleDestination.Device },
                )
                SampleDestination.Progress -> AppTopBar(
                    title = stringResource(R.string.title_recognizing),
                )
                SampleDestination.Document -> AppTopBar(
                    // The document's own name is the screen's display title; repeating it
                    // here would be the same sentence twice, one of them truncated.
                    title = stringResource(R.string.title_result),
                    onBack = { destination = SampleDestination.Home },
                )
                SampleDestination.Device -> AppTopBar(
                    title = stringResource(R.string.action_device),
                    onBack = { destination = SampleDestination.Home },
                )
                SampleDestination.Welcome -> Unit
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        val content = Modifier
            .fillMaxSize()
            .background(Theme.color.canvas)
            .padding(insets)

        when (destination) {
            SampleDestination.Home -> HomeScreen(
                languages = state.capabilities
                    ?.languages
                    ?.joinToString("+") { it.tesseractCode }
                    .orEmpty(),
                lastResult = state.document,
                onOpenDocument = { destination = SampleDestination.Document },
                onExtractScan = onExtractScan,
                onExtractDigital = onExtractDigital,
                onExtractPdf = onExtractPdf,
                onExtractImage = onExtractImage,
                enabled = !state.isRunning,
                modifier = content,
            )
            SampleDestination.Progress -> {
                val run = state.run
                if (run is RunState.Running) {
                    ProgressScreen(run = run, onCancel = onCancel, modifier = content)
                } else {
                    // The run ended between the LaunchedEffect and this frame.
                    Column(modifier = content) {}
                }
            }
            SampleDestination.Document -> DocumentScreen(
                document = state.document,
                modifier = content,
            )
            SampleDestination.Device -> DeviceScreen(
                capabilities = state.capabilities,
                hasDigitalTextLayer = state.hasDigitalTextLayer,
                modifier = content,
            )
            // Handled above, before the Scaffold.
            SampleDestination.Welcome -> Unit
        }
    }
}
