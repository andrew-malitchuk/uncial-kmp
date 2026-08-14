package io.github.andrewmalitchuk.uncial.core.source.raster

import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
public actual fun placeholderRaster(width: Int, height: Int): Raster =
    Raster(backing = null, width = width, height = height)
