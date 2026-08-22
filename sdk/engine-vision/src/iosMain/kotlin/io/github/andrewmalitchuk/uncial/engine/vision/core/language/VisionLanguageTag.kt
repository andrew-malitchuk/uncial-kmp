package io.github.andrewmalitchuk.uncial.engine.vision.core.language

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/** Vision's `uk-UA`-style tags, for the languages Uncial ships constants for. */
internal fun OcrLanguage.visionTag(): String? = bcp47
