package uncial.convention.core.language

/**
 * Where a language module's `.traineddata` is downloaded from, by language code.
 *
 * `tessdata_fast` and not `tessdata`: the fast models are 3.8 MB (ukr) and 4.1 MB (eng)
 * against 12 MB and 23 MB for the full ones, and there is nothing smaller to fall back to.
 */
internal fun tessdataUrl(language: String): String =
    "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$language.traineddata"
