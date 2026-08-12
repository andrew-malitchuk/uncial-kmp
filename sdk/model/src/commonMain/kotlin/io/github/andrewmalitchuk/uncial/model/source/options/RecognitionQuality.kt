package io.github.andrewmalitchuk.uncial.model.source.options

/**
 * How thoroughly the engine should look at the page.
 *
 * Honoured by the Vision engine, which has a real
 * `VNRequestTextRecognitionLevel` switch. The Tesseract engines ignore it: their only
 * equivalent is `OEM_TESSERACT_LSTM_COMBINED`, which needs legacy data that neither
 * `tessdata_fast` nor `tessdata_best` ships, so asking for it fails at init instead of
 * running faster. Check `OcrCapabilities` rather than assuming this took effect.
 */
public enum class RecognitionQuality {
    /** Roughly an order of magnitude faster, noticeably worse on small or noisy type. */
    Fast,

    /** The default. What you want for scanned documents. */
    Accurate,
}
