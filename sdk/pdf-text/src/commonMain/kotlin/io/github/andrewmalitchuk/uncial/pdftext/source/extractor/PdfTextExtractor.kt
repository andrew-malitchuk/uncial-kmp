package io.github.andrewmalitchuk.uncial.pdftext.source.extractor

import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger

/**
 * Reads a PDF's embedded text layer.
 *
 * Pass the result to `UncialClient`'s builder to enable the fast path:
 *
 * ```
 * val client = UncialClient {
 *     digitalTextExtractor = pdfTextExtractor()
 *     preferDigitalLayer = true
 * }
 * ```
 *
 * ### Coordinates
 *
 * PDF text lives in 72-dpi points, while OCR produces raster pixels. Rather than handing
 * back a second coordinate system for callers to reconcile, this extractor scales points
 * by `OcrOptions.renderScale` — so a digital page and an OCR'd page of the same document
 * carry directly comparable geometry, and `DocumentStructure` can compare type sizes
 * across a document where some pages came from each path. That single coordinate model is
 * the point of the SDK. (PLAN.md §2)
 */
public expect fun pdfTextExtractor(logger: OcrLogger = OcrLogger.None): DigitalTextExtractor
