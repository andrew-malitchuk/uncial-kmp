package io.github.andrewmalitchuk.uncial.engine.tesseract.source.install

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine.tesseractEngine
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.options.TesseractPageSegmentation

/**
 * Registers the Tesseract engine so `UncialClient` can find it without being told.
 *
 * Call once, early — from `Application.onCreate`, a `main` function, or a DI module.
 * Idempotent.
 *
 * **On Android you do not need this:** the engine registers itself at process start via
 * `androidx.startup`. It is here for the JVM, and for Android apps that strip
 * `InitializationProvider` from their merged manifest.
 *
 * @param pageSegmentation page segmentation for the registered engine.
 */
public fun installTesseractEngine(
    pageSegmentation: TesseractPageSegmentation = TesseractPageSegmentation.Auto,
) {
    OcrEngineRegistry.register(tesseractEngine(pageSegmentation))
}
