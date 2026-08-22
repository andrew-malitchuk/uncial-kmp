package io.github.andrewmalitchuk.uncial.engine.vision.source.install

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.engine.vision.source.engine.visionEngine

/**
 * Registers the Vision engine so `UncialClient` can find it without being told.
 *
 * Call once from your app's startup — iOS has no equivalent of `androidx.startup`, so
 * unlike Android this step is required. The generated Swift API exposes it too.
 * Idempotent.
 */
public fun installVisionEngine() {
    OcrEngineRegistry.register(visionEngine())
}
