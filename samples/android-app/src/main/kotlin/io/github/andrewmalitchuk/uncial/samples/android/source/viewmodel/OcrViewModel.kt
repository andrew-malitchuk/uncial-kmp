package io.github.andrewmalitchuk.uncial.samples.android.source.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.log.LogLevel
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor
import io.github.andrewmalitchuk.uncial.samples.android.core.asset.BUNDLED_DIGITAL
import io.github.andrewmalitchuk.uncial.samples.android.core.asset.BUNDLED_SCAN
import io.github.andrewmalitchuk.uncial.samples.android.core.content.displayName
import io.github.andrewmalitchuk.uncial.samples.android.core.document.summarize
import io.github.andrewmalitchuk.uncial.samples.android.core.image.decodeForOcr
import io.github.andrewmalitchuk.uncial.samples.android.source.model.OcrUiState
import io.github.andrewmalitchuk.uncial.samples.android.source.model.RunState
import io.github.andrewmalitchuk.uncial.structure.source.reconstruction.DocumentStructure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "UncialSample"

/** How many "page N" lines the progress screen keeps. See [OcrViewModel.onProgress]. */
private const val PROGRESS_TAIL = 3

/**
 * Owns the Uncial client and turns its progress flow into UI state.
 *
 * A ViewModel rather than state in the composable, because the client holds a loaded
 * Tesseract model: recreating it on every configuration change would reload ~8 MB of
 * language data.
 */
class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    /**
     * Extraction runs here rather than in `viewModelScope`.
     *
     * `viewModelScope` is cancelled just *before* `onCleared()`, which leaves nothing to
     * wait on at the moment the client has to be closed — and cancellation is cooperative,
     * so a page can still be inside the native engine. Owning the scope is what makes the
     * shutdown in [onCleared] able to close the client only after the work has stopped.
     */
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var running: Job? = null

    /**
     * The URI already handled from an `ACTION_VIEW` intent.
     *
     * The activity re-delivers its intent whenever it is recreated; this ViewModel outlives
     * that, so it is the right place to remember that the document is already dealt with.
     */
    private var consumedIntentUri: Uri? = null

    // No engine selection, no tessdata copying, no initialization: on Android the engine
    // registers itself through androidx.startup. This is the DX the SDK is meant to give.
    private val uncial: UncialClient = UncialClient {
        languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
        renderDpi = 200
        // On, because the document screen reports a word count. It costs an extra
        // iteration inside the engine and a `List<OcrWord>` per line, which is the trade
        // the capability matrix is describing when it says `words: yes`.
        includeWords = true
        preferDigitalLayer = true
        digitalTextExtractor = pdfTextExtractor()
        logger = OcrLogger { level, message, throwable ->
            Log.println(if (level == LogLevel.Error) Log.ERROR else Log.DEBUG, TAG, message)
            throwable?.let { Log.e(TAG, "cause", it) }
        }
    }

    init {
        _state.update {
            it.copy(
                capabilities = uncial.capabilities,
                hasDigitalTextLayer = uncial.hasDigitalTextLayerSupport,
            )
        }
    }

    /** The scanned fixture: image-only, so this exercises the OCR path. */
    fun extractBundledScan() = extract({ BUNDLED_SCAN }) { context ->
        val bytes = withContext(Dispatchers.IO) {
            context.assets.open(BUNDLED_SCAN).use { it.readBytes() }
        }
        uncial.extractAsFlow(bytes)
    }

    /** The digital fixture: has a text layer, so the engine should never start. */
    fun extractBundledDigital() = extract({ BUNDLED_DIGITAL }) { context ->
        val bytes = withContext(Dispatchers.IO) {
            context.assets.open(BUNDLED_DIGITAL).use { it.readBytes() }
        }
        uncial.extractAsFlow(bytes)
    }

    fun extract(uri: Uri) = extract({ context -> context.displayName(uri) }) { context ->
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("could not open $uri")
        }
        uncial.extractAsFlow(bytes)
    }

    /**
     * Recognizes a photo — from the camera or the gallery — with no PDF involved.
     *
     * The whole difference from the PDF path is the overload being called: `extractAsFlow`
     * takes a [Raster] as readily as it takes bytes, because rasterization and recognition
     * are separate roles in the SDK. Everything downstream — progress, structure,
     * cancellation — is identical.
     *
     * Note what is *not* passed: a `sourceDpi`. Nobody rendered this image at a known
     * resolution, and telling Tesseract a number that was never true is worse than letting
     * it estimate one. `OcrOptions.renderDpi` does not apply either — there is nothing to
     * render.
     */
    fun extractImage(uri: Uri) = extract({ context -> context.displayName(uri) }) { context ->
        val bitmap = withContext(Dispatchers.IO) { decodeForOcr(context, uri) }
        val raster = Raster(bitmap)
        // onCompletion rather than a try/finally around the collection: it runs on success,
        // on failure and on cancellation alike, and this raster is several megabytes of
        // pixels the sample owns. The SDK releases the rasters it creates itself; one the
        // caller built stays the caller's.
        uncial.extractAsFlow(raster).onCompletion { raster.release() }
    }

    /** Extracts a URI handed over by another app, at most once. See [consumedIntentUri]. */
    fun extractIntentUri(uri: Uri) {
        if (uri == consumedIntentUri) return
        consumedIntentUri = uri
        extract(uri)
    }

    /**
     * Runs one extraction, whatever its source.
     *
     * [open] is suspending and returns the flow rather than the input, so each entry point
     * can do its own reading and decoding inside the coroutine — off the main thread, and
     * under the same cancellation as the recognition that follows it. [label] is suspending
     * for the same reason: naming a picked URI is a `ContentResolver` query, which is I/O.
     */
    private fun extract(
        label: suspend (Context) -> String,
        open: suspend (Context) -> Flow<OcrProgress>,
    ) {
        // One extraction at a time: a second would queue behind the first anyway, since the
        // engine holds a single native handle.
        //
        // `cancel()` only *requests* cancellation, so the previous job is joined from inside
        // the new one rather than abandoned here. Cancelling and immediately launching would
        // let the old job resume after this one has already set isRunning, and its `finally`
        // would then clear the progress bar out from under a run that is still going.
        val previous = running
        running = extractionScope.launch {
            // NonCancellable, or the chain snaps: if a third request cancels THIS job while
            // it is parked here, the join throws, this job completes without waiting, and
            // its successor starts while the first extraction is still in the engine.
            withContext(NonCancellable) { previous?.cancelAndJoin() }
            _state.update {
                it.copy(run = RunState.Running(label = ""), document = null, error = null)
            }
            val started = System.currentTimeMillis()
            try {
                val name = label(getApplication())
                _state.update { state ->
                    val running = state.run as? RunState.Running ?: return@update state
                    state.copy(run = running.copy(label = name))
                }
                open(getApplication()).collect { progress ->
                    onProgress(progress, name, started)
                }
            } catch (cancelled: CancellationException) {
                // Rethrow before the general catch. CancellationException IS a Throwable,
                // so without this a superseded run reports "Job was cancelled" as a failure
                // -- and the error field is not covered by the identity guard below, so it
                // would be painted over a run that is still going. This is the same trap
                // the SDK's own runCatchingOcr exists to avoid.
                throw cancelled
            } catch (cause: Throwable) {
                Log.e(TAG, "extraction failed", cause)
                _state.update {
                    it.copy(error = "${cause::class.simpleName}: ${cause.message}")
                }
            } finally {
                // Only the current job may clear the running state. A superseded job reaches
                // this after its replacement has already claimed the UI.
                if (running === coroutineContext.job) {
                    _state.update { it.copy(run = RunState.Idle) }
                }
            }
        }
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private suspend fun onProgress(progress: OcrProgress, label: String, startedAt: Long) {
        when (progress) {
            is OcrProgress.Started -> _state.update { state ->
                state.copy(
                    run = RunState.Running(
                        label = label,
                        total = progress.pageCount,
                        source = progress.source,
                    ),
                )
            }
            is OcrProgress.Page -> _state.update { state ->
                val running = state.run as? RunState.Running ?: return@update state
                state.copy(
                    run = running.copy(
                        completed = progress.completed,
                        total = progress.of,
                        // A tail rather than the whole log: on a 300-page document the
                        // list would be the largest thing in the state, and only the last
                        // few lines are ever on screen.
                        lastPageLines = (
                            running.lastPageLines +
                                "page ${progress.index + 1} · ${progress.page.lines.size} lines"
                            ).takeLast(PROGRESS_TAIL),
                    ),
                )
            }
            is OcrProgress.Done -> {
                val document = progress.document
                // The collector runs on Main.immediate, and both reconstruction and the
                // summary walk every line of the document -- 300 pages of it, in the case
                // this SDK exists for. Every other stage moves itself off the main thread;
                // these are pure functions, so moving them is the caller's job.
                val summary = withContext(Dispatchers.Default) {
                    document.summarize(
                        label = label,
                        elapsedMillis = System.currentTimeMillis() - startedAt,
                        blocks = DocumentStructure.reconstruct(document),
                    )
                }
                _state.update { it.copy(document = summary) }
            }
            // DocBlock and OcrProgress are documented as growing in minor releases, so an
            // exhaustive `when` without a fallback is a build break waiting for an upgrade.
            else -> Unit
        }
    }

    /**
     * Stops the extraction in flight.
     *
     * Cooperative, like every cancellation here: the Tesseract engine interrupts the page
     * it is on, but the call returns before the engine has finished unwinding. The UI says
     * so rather than pretending the button is instant.
     */
    fun cancel() {
        running?.cancel()
    }

    override fun onCleared() {
        // Close the client only once the in-flight extraction has actually stopped: closing
        // frees the engine's native handle, and cancellation is not observed until the engine
        // returns from the page it is on.
        //
        // A cancelled Job completes only after all of its children have, so a completion
        // handler is the wait — without blocking the main thread, which is what a plain
        // `runBlocking { cancelAndJoin() }` here would do for the length of a page.
        extractionScope.coroutineContext.job.invokeOnCompletion { uncial.close() }
        extractionScope.cancel()
        super.onCleared()
    }
}
