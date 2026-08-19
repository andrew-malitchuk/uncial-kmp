package io.github.andrewmalitchuk.uncial.engine.fake.source.engine

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger

/** The fake's tests say nothing; a logger that recorded would only prove the logger works. */
internal val NoLogger: OcrLogger = OcrLogger.None
