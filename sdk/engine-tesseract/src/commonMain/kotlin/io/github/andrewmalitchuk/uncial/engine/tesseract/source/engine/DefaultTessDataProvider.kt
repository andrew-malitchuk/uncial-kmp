package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider

/**
 * The platform's default source of `.traineddata`.
 *
 * On Android this reads the language artifacts' assets and copies them where Tesseract can
 * see them; on the JVM it looks in the usual system locations (`TESSDATA_PREFIX`, the
 * Homebrew and Linux package paths). Supply your own provider to
 * [OcrEngineFactory.create] to override.
 */
public expect fun defaultTessDataProvider(): LanguageDataProvider
