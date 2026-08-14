package io.github.andrewmalitchuk.uncial.core.source.recognizer

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage

/**
 * Recognizes text in a raster.
 *
 * The other half of the split described on [Raster]: a recognizer knows nothing about
 * PDFs. Give it pixels, get back lines with geometry.
 *
 * Implementations hold engine state — a `TessBaseAPI` handle, a loaded language model —
 * which the runtime creates once per `UncialClient` and closes when that client closes.
 * **Not once per extraction:** every `extract` call on one client is handed the same
 * instance, so an implementation must tolerate concurrent, re-entrant [recognize] calls
 * from several coroutines. Loading the model per document would dominate the cost of
 * everything else, which is why the runtime shares it rather than serializing on the
 * caller's behalf. Every bundled recognizer — both Tesseract bindings and Vision — holds
 * a single native handle and therefore serializes [recognize] internally with a mutex; an
 * engine wrapping anything similarly non-reentrant has to do the same.
 *
 * This is also why the SDK has no `expect object`: a singleton takes no configuration,
 * cannot be mocked, and has no lifecycle. (PLAN.md §5.1)
 */
public interface TextRecognizer : AutoCloseable {

    /**
     * What this recognizer can actually do, given the options it was created with.
     *
     * Android and iOS run different engines with different accuracy and different
     * metadata, and pretending otherwise is a lie the caller eventually pays for. This is
     * the self-description that replaces the pretence. (PLAN.md §4)
     */
    public val capabilities: OcrCapabilities

    /**
     * Recognizes [raster] and returns it as a page.
     *
     * Does not release [raster]; the caller owns it. Must cooperate with cancellation —
     * a long page is expected to check its coroutine's state as it goes.
     *
     * @param pageIndex the index to stamp on the resulting [OcrPage]; recognition itself
     *   does not care which page this is.
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.RecognitionFailed if the
     *   engine fails on this page.
     */
    public suspend fun recognize(
        raster: Raster,
        pageIndex: Int,
        options: OcrOptions,
    ): OcrPage
}
