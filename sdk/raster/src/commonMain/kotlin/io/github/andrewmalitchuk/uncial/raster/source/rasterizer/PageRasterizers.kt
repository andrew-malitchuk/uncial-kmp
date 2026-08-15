package io.github.andrewmalitchuk.uncial.raster.source.rasterizer

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer

/**
 * Creates the platform's [PageRasterizer].
 *
 * Every platform already ships a PDF renderer, so this costs no third-party dependency
 * except on the JVM: `PdfRenderer` on Android, PDFKit on iOS, PDFBox on the JVM.
 * (PLAN.md §6.2)
 */
public expect fun createPageRasterizer(logger: OcrLogger = OcrLogger.None): PageRasterizer
