package io.github.andrewmalitchuk.uncial.engine.vision.source.engine

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory

/**
 * Apple Vision as an Uncial engine.
 *
 * Vision and PDFKit are system frameworks, so this module has **zero third-party
 * dependencies** and iOS needs no language data at all: the models ship with the OS.
 * (PLAN.md §6.2, §6.4)
 *
 * ### Ukrainian needs iOS 16
 *
 * Vision gained Ukrainian in text-recognition revision 3, which is iOS 16. On iOS 15 the
 * request simply does not offer `uk-UA`, so a Ukrainian document recognizes as garbage
 * Latin rather than failing. This engine therefore asks Vision at runtime which languages
 * it actually supports, drops the rest, and reports what is left through
 * `OcrCapabilities.languages` — read it rather than assuming your requested languages
 * took effect.
 */
public expect fun visionEngine(): OcrEngineFactory
