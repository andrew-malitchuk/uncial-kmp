package io.github.andrewmalitchuk.uncial.engine.tesseract.source.install

import android.content.Context
import androidx.startup.Initializer
import io.github.andrewmalitchuk.uncial.core.source.android.UncialContextInitializer
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine.tesseractEngine

/**
 * Puts the Tesseract engine into [OcrEngineRegistry] at process start.
 *
 * Depends on [UncialContextInitializer] so the application context is already captured by
 * the time the engine is registered — the engine needs it to find language data.
 *
 * Registration here is cheap: it stores a factory. Nothing loads `libtesseract` or reads
 * `.traineddata` until an extraction actually starts, so this costs no measurable startup
 * time.
 */
public class TesseractEngineInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        OcrEngineRegistry.register(tesseractEngine())
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(UncialContextInitializer::class.java)
}
