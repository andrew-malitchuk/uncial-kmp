package io.github.andrewmalitchuk.uncial.structure.core.fixture

import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage

internal fun document(vararg pages: OcrPage): OcrDocument = OcrDocument(pages.toList())
