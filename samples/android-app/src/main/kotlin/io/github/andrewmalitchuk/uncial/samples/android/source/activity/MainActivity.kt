package io.github.andrewmalitchuk.uncial.samples.android.source.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.andrewmalitchuk.uncial.samples.android.source.app.SampleApp
import io.github.andrewmalitchuk.uncial.samples.android.source.viewmodel.OcrViewModel
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.theme.AppTheme

/**
 * Runs Uncial against a bundled Ukrainian scan, any PDF the user picks, or a photo taken
 * with the camera or chosen from the gallery.
 *
 * Note what this class does NOT contain: no Uncial initialization, no engine selection, no
 * tessdata copying. The engine registers itself through `androidx.startup`, which is the
 * whole point of PLAN.md §5.5.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: OcrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Explicit rather than implicit: an app targeting SDK 35+ is laid out edge to edge
        // whether it asks or not, so it may as well say so and handle the insets properly.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A VIEW intent means another app sent us a PDF. onCreate runs again after every
        // configuration change, while the ViewModel and its finished result survive one, so
        // re-handling the intent naively would restart the extraction and throw away the
        // rendered document on every rotation. The ViewModel is what prevents that: it
        // refuses a URI it has already extracted.
        //
        // Deliberately NOT guarded on `savedInstanceState == null`. That guard also fires
        // after a low-memory process death, where the activity IS restored with saved state
        // but the ViewModel is brand new and empty -- and suppressing the intent there
        // leaves the user staring at a blank screen instead of their document.
        //
        // This activity is `standard` launch mode, so a second VIEW intent arrives as a new
        // instance with a new ViewModel. Give it `singleTop` and you need onNewIntent too,
        // or the second document is silently ignored.
        intent?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.let(viewModel::extractIntentUri)

        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SampleApp(
                    state = state,
                    onExtractScan = viewModel::extractBundledScan,
                    onExtractDigital = viewModel::extractBundledDigital,
                    onExtractPdf = viewModel::extract,
                    onExtractImage = viewModel::extractImage,
                    onCancel = viewModel::cancel,
                )
            }
        }
    }
}
