package io.github.andrewmalitchuk.uncial.core.source.raster

import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions

/**
 * An open PDF, ready to have individual pages rasterized.
 *
 * Holds native resources (a file descriptor on Android, a `PDDocument` on the JVM) and
 * must be closed. Prefer `use { }`.
 */
public interface RasterizedDocument : AutoCloseable {

    /** Number of pages, known without rasterizing any of them. */
    public val pageCount: Int

    /**
     * Rasterizes one page at the resolution [options] asks for, capped by
     * `OcrOptions.maxPageSide`.
     *
     * The caller owns the returned [Raster] and must [Raster.release] it.
     *
     * @param pageIndex zero-based.
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.RenderFailed if this page
     *   cannot be rendered.
     */
    public suspend fun rasterize(pageIndex: Int, options: OcrOptions): Raster
}
