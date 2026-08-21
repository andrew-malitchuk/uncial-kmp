package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import com.googlecode.tesseract.android.TessBaseAPI
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.options.TesseractPageSegmentation

internal fun TesseractPageSegmentation.toTesseractMode(): Int = when (this) {
    TesseractPageSegmentation.Auto -> TessBaseAPI.PageSegMode.PSM_AUTO
    TesseractPageSegmentation.AutoWithOrientation -> TessBaseAPI.PageSegMode.PSM_AUTO_OSD
    TesseractPageSegmentation.SingleBlock -> TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
    TesseractPageSegmentation.SingleLine -> TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
    TesseractPageSegmentation.SingleWord -> TessBaseAPI.PageSegMode.PSM_SINGLE_WORD
    TesseractPageSegmentation.SparseText -> TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
}
