package io.github.andrewmalitchuk.uncial.engine.tesseract.core.language

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

internal fun OcrLanguage.fileName(): String = "$tesseractCode.traineddata"
