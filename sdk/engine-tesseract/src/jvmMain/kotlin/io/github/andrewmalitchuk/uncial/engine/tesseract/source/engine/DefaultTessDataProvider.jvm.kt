package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.language.ClasspathTessDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.language.plus
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.language.SystemTessDataProvider

/**
 * The system installation first, then whatever `uncial-lang-*` artifacts are on the
 * classpath. A developer machine with `brew install tesseract-lang` keeps using its own
 * models; a server with only the JAR falls back to the shipped ones.
 */
public actual fun defaultTessDataProvider(): LanguageDataProvider =
    SystemTessDataProvider() + ClasspathTessDataProvider()
