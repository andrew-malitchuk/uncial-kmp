package io.github.andrewmalitchuk.uncial.model.source.options

/** The pixel format pages are rasterized into before recognition. */
public enum class RasterColor {
    /**
     * One channel. The default: OCR engines binarize anyway, and it costs a quarter of the
     * memory of ARGB, which is what keeps a 300-page scan from an OOM on a cheap phone.
     */
    Grayscale,

    /** Four channels. Only useful for a custom engine that reads colour. */
    Color,
}
