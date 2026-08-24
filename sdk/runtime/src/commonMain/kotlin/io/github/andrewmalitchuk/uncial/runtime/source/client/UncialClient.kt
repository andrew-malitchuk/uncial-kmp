package io.github.andrewmalitchuk.uncial.runtime.source.client

import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.error
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.runtime.core.error.runCatchingOcr
import io.github.andrewmalitchuk.uncial.runtime.core.pipeline.ExtractionPipeline
import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single entry point a consumer of Uncial calls.
 *
 * Build one with the [UncialClient] function, keep it for as long as you are extracting
 * documents, and [close] it when you are done — it holds a loaded recognition engine,
 * which on Tesseract means several MB of language model that you do not want to reload per
 * document.
 *
 * ```
 * val ocr = UncialClient {
 *     languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
 *     renderDpi = 200
 *     preferDigitalLayer = true
 * }
 *
 * when (val result = ocr.extract(pdfBytes)) {
 *     // OcrDocument: pages -> lines -> words, boxes, confidence
 *     else -> result.getOrNull()?.let(::render)
 * }
 *
 * ocr.close()
 * ```
 *
 * ### Errors
 *
 * The `extract` functions return [Result], and a failure always carries an
 * [OcrError] — never a raw platform exception. Cancellation is never captured into a
 * `Result`: cancelling the calling coroutine throws `CancellationException` as it would
 * from any other suspend function.
 *
 * For Swift, the `...OrThrow` variants are annotated so they arrive as `try await`.
 *
 * ### Thread safety
 *
 * Safe to share, and concurrent extractions on one client are safe — but the
 * serialization that makes them safe belongs to the *engines*, not to this class. A
 * client creates one recognizer and hands that same instance to every concurrent
 * `extract`; each bundled recognizer holds a single native handle and takes its own lock
 * around it, so the calls queue rather than interleave. See `TextRecognizer` for what a
 * third-party engine has to guarantee.
 *
 * [close] may be called at any time, including while an extraction is running, and the
 * recognizer is released exactly once however the two interleave. What it does *not* do is
 * return instantly: the bundled engines take a blocking native lock in `close()` so a
 * handle is never freed underneath work in flight — on Android that spans a whole page, on
 * the JVM the native call currently executing. **`close()` therefore blocks the calling
 * thread**, which is worth knowing before calling it from `onDestroy` or a `use { }` block
 * on the main thread.
 */
@OptIn(ExperimentalAtomicApi::class)
public class UncialClient internal constructor(
    private val engineProvider: () -> OcrEngineFactory?,
    private val rasterizer: PageRasterizer,
    private val digitalTextExtractor: DigitalTextExtractor?,
    private val languageData: LanguageDataProvider?,
    /** The options every extraction on this client uses. */
    public val options: OcrOptions,
    private val logger: OcrLogger,
) : AutoCloseable {

    private val engineLock = Mutex()

    // Atomic, not a plain `var`: close() cannot take the suspending engineLock, so there is
    // no happens-before edge between it and obtainRecognizer. Whoever wins the exchange
    // owns closing the recognizer, which is what makes "closed exactly once" true no matter
    // how the two interleave.
    private val activeRecognizer = AtomicReference<TextRecognizer?>(null)

    // Volatile because close() runs outside engineLock -- it has to, since AutoCloseable
    // is not suspending -- so without it a thread already inside ensureOpen() may never
    // observe the flag, and on Kotlin/Native this is an outright data race.
    @Volatile
    private var closed: Boolean = false

    private val pipeline = ExtractionPipeline(
        rasterizer = rasterizer,
        digitalTextExtractor = digitalTextExtractor,
        recognizer = ::obtainRecognizer,
        options = options,
        logger = logger,
    )

    /**
     * What this client can actually do here, or `null` when no engine is available.
     *
     * Read it instead of assuming: Android and iOS run different engines with different
     * metadata, and the languages reported here can be a subset of the ones you asked for
     * — Vision has no Ukrainian before iOS 16, and Tesseract has none without the matching
     * `uncial-lang-*` artifact. (PLAN.md §4)
     *
     * `null` means either no engine module is on the classpath, or the one that is cannot
     * run — a JVM with no system `libtesseract`, for instance. Every `extract` call will
     * fail with [OcrError.Unsupported] in that state.
     */
    public val capabilities: OcrCapabilities?
        get() = engineProvider()?.takeIf { it.isAvailable() }?.capabilities(options)

    /** `true` when an engine is available and extraction can be expected to work. */
    public val isAvailable: Boolean get() = capabilities != null

    /** Whether the digital text-layer fast path is wired up on this client. */
    public val hasDigitalTextLayerSupport: Boolean get() = digitalTextExtractor != null

    /**
     * Extracts a PDF in one call.
     *
     * Equivalent to collecting [extractAsFlow] and keeping only the final document. Use
     * the flow instead when the document is long enough that a user needs to see progress.
     *
     * @param bytes the PDF's bytes.
     */
    public suspend fun extract(bytes: ByteArray): Result<OcrDocument> = runCatchingOcr {
        ensureOpen()
        pipeline.extract(bytes).lastDocument()
    }

    /**
     * Recognizes an image the caller already has, with no PDF involved.
     *
     * Construct the [Raster] from your platform's image type — `Raster(bitmap)`,
     * `Raster(bufferedImage)`, `Raster(cgImage)`. The caller keeps ownership: this does not
     * release it.
     */
    public suspend fun extract(raster: Raster): Result<OcrDocument> = runCatchingOcr {
        ensureOpen()
        pipeline.extract(raster).lastDocument()
    }

    /**
     * Extracts a PDF, reporting progress per page and honouring cancellation.
     *
     * ```
     * ocr.extractAsFlow(pdfBytes).collect { progress ->
     *     when (progress) {
     *         is OcrProgress.Started -> ui.showTotal(progress.pageCount)
     *         is OcrProgress.Page -> ui.update(progress.fraction)
     *         is OcrProgress.Done -> render(progress.document)
     *         else -> Unit
     *     }
     * }
     * ```
     *
     * The flow is cold: nothing happens until it is collected. It throws [OcrError] on
     * failure rather than emitting it, and cancelling the collecting coroutine stops the
     * work at the next page boundary — or sooner, since the Tesseract engine can interrupt
     * a page in flight.
     */
    public fun extractAsFlow(bytes: ByteArray): Flow<OcrProgress> =
        progressFlow { pipeline.extract(bytes) }

    /** [extractAsFlow] for an image the caller already has. */
    public fun extractAsFlow(raster: Raster): Flow<OcrProgress> =
        progressFlow { pipeline.extract(raster) }

    /**
     * [extract] as a throwing function, for Swift.
     *
     * Kotlin's `Result` is an inline value class and does not survive the trip through
     * Objective-C in any usable form, so the iOS-facing surface throws instead. In Swift
     * this arrives as `try await ocr.extract(bytes:)`.
     */
    @Throws(OcrError::class, CancellationException::class)
    public suspend fun extractOrThrow(bytes: ByteArray): OcrDocument =
        extract(bytes).getOrThrow()

    /** [extract] for an image, as a throwing function. See [extractOrThrow]. */
    @Throws(OcrError::class, CancellationException::class)
    public suspend fun extractOrThrow(raster: Raster): OcrDocument =
        extract(raster).getOrThrow()

    /**
     * Releases the recognition engine.
     *
     * After this the client is unusable and every `extract` fails with
     * [OcrError.Unsupported]. Closing twice is safe.
     */
    override fun close() {
        closed = true
        activeRecognizer.exchange(null)?.closeQuietly()
    }

    /**
     * The recognizer, created on first use and reused afterwards.
     *
     * Creation loads the language model, which for `ukr+eng` is ~8 MB and takes real time.
     * Doing it per page — or per document — would dominate the cost of everything else.
     */
    private suspend fun obtainRecognizer(): TextRecognizer = engineLock.withLock {
        ensureOpen()
        activeRecognizer.load()?.let { return@withLock it }
        val engine = engineProvider() ?: throw OcrError.Unsupported(
            "no OCR engine available. Add uncial-engine-tesseract (Android/JVM) or " +
                "uncial-engine-vision (iOS), or pass one to the UncialClient builder. " +
                "Outside Android, call installTesseractEngine() / installVisionEngine() once.",
            // An engine that threw while probing is invisible to firstAvailable(), and
            // "no engine available" is a misleading way to describe "the engine you
            // installed blew up when asked". The registry keeps that throwable for us.
            cause = OcrEngineRegistry.lastProbeFailure(),
        )
        if (!engine.isAvailable()) {
            throw OcrError.Unsupported(
                "engine '${engine.id}' is present but not usable here. On the JVM this " +
                    "usually means no system libtesseract or no .traineddata; see " +
                    "OcrEngineFactory.isAvailable().",
            )
        }
        val created = engine.create(options, logger, languageData)
        // Store first, THEN re-check. close() can land at any point while create() is
        // loading the language model -- ~8 MB for ukr+eng -- and it does not take
        // engineLock. Checking before storing would leave a window in which close() sees
        // no recognizer, this line then publishes one, and those 8 MB plus a native handle
        // belong to a client nobody will ever close again. Publishing first means the
        // exchange below and the one in close() race for the same reference, and exactly
        // one of them gets it.
        activeRecognizer.store(created)
        if (closed) {
            activeRecognizer.exchange(null)?.closeQuietly()
            throw OcrError.Unsupported("this UncialClient has been closed")
        }
        created
    }

    /** Closes without letting a failing teardown mask the error that caused it. */
    private fun TextRecognizer.closeQuietly() {
        try {
            close()
        } catch (cause: Throwable) {
            // A recognizer that could not release its native handle is a leak, and a
            // silent one if this is dropped on the floor.
            logger.error("failed to close the recognizer", cause)
        }
    }

    private fun ensureOpen() {
        if (closed) throw OcrError.Unsupported("this UncialClient has been closed")
    }

    private inline fun progressFlow(
        crossinline source: () -> Flow<OcrProgress>,
    ): Flow<OcrProgress> =
        kotlinx.coroutines.flow.flow {
            ensureOpen()
            source().collect { emit(it) }
        }

    private suspend fun Flow<OcrProgress>.lastDocument(): OcrDocument =
        filterIsInstance<OcrProgress.Done>().map { it.document }.first()
}
