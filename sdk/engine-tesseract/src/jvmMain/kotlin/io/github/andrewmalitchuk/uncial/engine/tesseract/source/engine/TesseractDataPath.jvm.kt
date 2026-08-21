package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

/**
 * Adapts a provider's datapath to what Tess4J expects.
 *
 * @return `root/tessdata` when that directory exists, otherwise [root] unchanged — which
 *   covers a provider that already points at the tessdata directory.
 */
internal fun tesseractDataPath(root: String): String {
    val nested = java.io.File(root, "tessdata")
    return if (nested.isDirectory) nested.absolutePath else root
}
