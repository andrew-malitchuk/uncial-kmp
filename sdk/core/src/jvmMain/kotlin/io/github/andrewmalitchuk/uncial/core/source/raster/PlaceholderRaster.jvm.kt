package io.github.andrewmalitchuk.uncial.core.source.raster

public actual fun placeholderRaster(width: Int, height: Int): Raster =
    Raster(backing = null, width = width, height = height)
