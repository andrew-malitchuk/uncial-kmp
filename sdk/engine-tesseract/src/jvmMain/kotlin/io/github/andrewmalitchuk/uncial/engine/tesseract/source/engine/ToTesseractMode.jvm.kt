package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import io.github.andrewmalitchuk.uncial.engine.tesseract.source.options.TesseractPageSegmentation
import net.sourceforge.tess4j.ITessAPI

internal fun TesseractPageSegmentation.toTesseractMode(): Int = when (this) {
    TesseractPageSegmentation.Auto -> ITessAPI.TessPageSegMode.PSM_AUTO
    TesseractPageSegmentation.AutoWithOrientation -> ITessAPI.TessPageSegMode.PSM_AUTO_OSD
    TesseractPageSegmentation.SingleBlock -> ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK
    TesseractPageSegmentation.SingleLine -> ITessAPI.TessPageSegMode.PSM_SINGLE_LINE
    TesseractPageSegmentation.SingleWord -> ITessAPI.TessPageSegMode.PSM_SINGLE_WORD
    TesseractPageSegmentation.SparseText -> ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT
}
