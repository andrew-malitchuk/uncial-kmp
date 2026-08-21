package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.options.TesseractPageSegmentation

/**
 * The Tesseract engine for this platform.
 *
 * Available on Android (Tesseract4Android, which bundles `libtesseract.so`) and on the
 * JVM (Tess4J, which binds to a **system** `libtesseract`). Running the same engine on
 * both is deliberate: it means an Android result and a JVM result of the same scan agree,
 * which is what makes JVM the practical place to run the golden corpus. (PLAN.md §4)
 *
 * Not available on iOS — there, `uncial-engine-vision` is the engine, and this module is
 * not even built for the platform.
 *
 * @param pageSegmentation how to segment pages. Engine-specific knobs live on the
 *   engine's own factory rather than in `OcrOptions`, which stays generic across all
 *   three engines.
 */
public expect fun tesseractEngine(
    pageSegmentation: TesseractPageSegmentation = TesseractPageSegmentation.Auto,
): OcrEngineFactory
