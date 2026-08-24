package io.github.andrewmalitchuk.uncial.runtime.source.client

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Creates a [UncialClient].
 *
 * ```
 * val ocr = UncialClient {
 *     languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
 *     renderDpi = 200
 *     maxPageSide = 2600
 *     preferDigitalLayer = true
 * }
 * ```
 *
 * Never throws: a missing or unusable engine surfaces at the first `extract` call as
 * [io.github.andrewmalitchuk.uncial.model.OcrError.Unsupported], and can be checked up
 * front with [UncialClient.isAvailable]. That keeps client construction safe to do in an
 * initializer or a DI graph.
 */
public fun UncialClient(configure: UncialClientBuilder.() -> Unit = {}): UncialClient =
    UncialClientBuilder().apply(configure).build()
