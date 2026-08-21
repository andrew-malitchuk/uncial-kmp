package io.github.andrewmalitchuk.uncial.engine.tesseract.source.options

/**
 * How Tesseract should segment the page before recognizing it.
 *
 * Exposed because it is the single most effective knob on Tesseract's accuracy and the
 * POC hardcoded it to [Auto]: a receipt or a single-line label recognizes far better with
 * [SingleBlock] or [SingleLine], and forcing [Auto] on them produces confident nonsense.
 *
 * The values mirror Tesseract's own `PageSegMode`, which is why the names are its names.
 */
public enum class TesseractPageSegmentation {
    /** Full automatic page segmentation without orientation detection. The default. */
    Auto,

    /** Automatic segmentation with orientation and script detection. Slower. */
    AutoWithOrientation,

    /** Assume a single uniform block of text. Good for cropped columns. */
    SingleBlock,

    /** Assume a single line of text. Good for labels and receipts. */
    SingleLine,

    /** Assume a single word. */
    SingleWord,

    /** Sparse text: find as much text as possible in no particular order. */
    SparseText,
}
