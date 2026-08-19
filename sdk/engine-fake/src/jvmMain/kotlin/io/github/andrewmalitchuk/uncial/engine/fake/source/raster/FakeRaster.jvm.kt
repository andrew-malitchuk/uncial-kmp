package io.github.andrewmalitchuk.uncial.engine.fake.source.raster

import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import java.awt.Color
import java.awt.image.BufferedImage

public actual fun blankRaster(width: Int, height: Int): Raster {
    val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val graphics = image.createGraphics()
    try {
        graphics.paint = Color.WHITE
        graphics.fillRect(0, 0, width, height)
    } finally {
        graphics.dispose()
    }
    return Raster(image)
}
