package io.github.andrewmalitchuk.uncial.engine.fake.source.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster

public actual fun blankRaster(width: Int, height: Int): Raster {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.WHITE)
    return Raster(bitmap)
}
