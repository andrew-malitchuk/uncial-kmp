package io.github.andrewmalitchuk.uncial.runtime.source.client

import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.options.RasterColor
import io.github.andrewmalitchuk.uncial.model.source.options.RecognitionQuality
import io.github.andrewmalitchuk.uncial.raster.source.rasterizer.createPageRasterizer

/**
 * Configuration for [UncialClient].
 *
 * The recognition settings mirror [OcrOptions] one for one, so the common case needs no
 * knowledge of that type. Set [options] directly to supply a whole [OcrOptions] instead —
 * useful when the options come from somewhere else — and note that doing so ignores the
 * individual properties.
 *
 * The remaining properties are the composition root: engine, rasterizer, digital
 * extractor, language data, logger. Uncial has **no DI framework** on purpose — inside a
 * library that is a dependency forced on the consumer and a source of version conflicts,
 * so this is a hand-written builder instead. (PLAN.md §6.3)
 */
public class UncialClientBuilder internal constructor() {

    // ── Recognition settings (mirrors OcrOptions) ──

    /** Languages to recognize, in preference order. */
    public var languages: List<OcrLanguage> = OcrLanguage.Default

    /** Resolution pages are rasterized at. See [OcrOptions.renderDpi]. */
    public var renderDpi: Int = OcrOptions.DEFAULT_RENDER_DPI

    /** Cap on the longer side of a rasterized page. See [OcrOptions.maxPageSide]. */
    public var maxPageSide: Int = OcrOptions.DEFAULT_MAX_PAGE_SIDE

    /** Try the PDF's text layer before OCR. Requires [digitalTextExtractor]. */
    public var preferDigitalLayer: Boolean = true

    /** Speed/accuracy trade-off. Honoured by Vision; ignored by Tesseract. */
    public var recognitionQuality: RecognitionQuality = RecognitionQuality.Accurate

    /** Pixel format for rasterization. */
    public var rasterColor: RasterColor = RasterColor.Grayscale

    /** Also produce per-word boxes and confidences. Off by default: it costs extra work. */
    public var includeWords: Boolean = false

    /** Zero-based, inclusive range of pages to process, or `null` for all. */
    public var pageRange: IntRange? = null

    /**
     * A complete [OcrOptions], overriding every individual property above.
     *
     * `null` (the default) means the properties are used.
     */
    public var options: OcrOptions? = null

    // ── Composition root ──

    /**
     * The recognition engine.
     *
     * `null` (the default) resolves through [OcrEngineRegistry] at first use — which on
     * Android means the Tesseract engine registered itself and there is nothing to set.
     * Set this to force a specific engine, or to inject
     * `FakeOcrEngine` in tests.
     */
    public var engine: OcrEngineFactory? = null

    /**
     * How PDF pages become rasters.
     *
     * `null` (the default) uses the platform's own renderer: `PdfRenderer` on Android,
     * PDFKit on iOS, PDFBox on the JVM. Set it to `FakePageRasterizer` in tests, or to
     * your own implementation to feed Uncial pages from somewhere other than a PDF.
     */
    public var rasterizer: PageRasterizer? = null

    /**
     * The digital text-layer reader — the fast path.
     *
     * `null` (the default) means OCR always runs, even for a PDF that already contains its
     * text. Add the `uncial-pdf-text` dependency and set this to `pdfTextExtractor()` to
     * enable it. It is not wired automatically because the module is optional: on Android
     * it pulls in several MB of PDFBox-Android, which whoever OCRs photographs should not
     * pay for. (PLAN.md §8.2)
     */
    public var digitalTextExtractor: DigitalTextExtractor? = null

    /**
     * Where Tesseract's `.traineddata` comes from.
     *
     * `null` (the default) uses the engine's own default: the app's assets on Android, the
     * system installation on the JVM. Vision ignores it.
     */
    public var languageData: LanguageDataProvider? = null

    /** Where diagnostics go. Discards everything by default — a library logs nothing uninvited. */
    public var logger: OcrLogger = OcrLogger.None

    internal fun build(): UncialClient {
        val resolvedOptions = options ?: OcrOptions(
            languages = languages,
            renderDpi = renderDpi,
            maxPageSide = maxPageSide,
            preferDigitalLayer = preferDigitalLayer,
            recognitionQuality = recognitionQuality,
            rasterColor = rasterColor,
            includeWords = includeWords,
            pageRange = pageRange,
        )
        val explicitEngine = engine
        return UncialClient(
            // Resolved lazily so that construction cannot fail, and so that an engine
            // registered after the client was built is still found.
            engineProvider = { explicitEngine ?: OcrEngineRegistry.firstAvailable() },
            rasterizer = rasterizer ?: createPageRasterizer(logger),
            digitalTextExtractor = digitalTextExtractor,
            languageData = languageData,
            options = resolvedOptions,
            logger = logger,
        )
    }
}
