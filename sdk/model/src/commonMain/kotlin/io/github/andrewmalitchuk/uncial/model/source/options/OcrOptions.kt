package io.github.andrewmalitchuk.uncial.model.source.options

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Everything tunable about an extraction, in one place.
 *
 * The POC this SDK grew out of had three platforms hardcoding three different render
 * resolutions — 200 dpi on Android, 300 on desktop, 2× on iOS — for no reason anybody
 * could reconstruct. Those constants live here now, so a caller can set one number and
 * get the same behaviour everywhere. (PLAN.md §5.8)
 *
 * @property languages languages to recognize, in preference order. Engines that cannot
 *   handle one skip it rather than failing.
 * @property renderDpi resolution pages are rasterized at. 200 is the sweet spot for
 *   printed text; below ~150 Tesseract's accuracy falls off a cliff, above ~300 you pay
 *   memory and time for nothing.
 * @property maxPageSide hard cap on the longer side of a rasterized page, in pixels.
 *   Applied **after** [renderDpi], scaling down if needed. This is the OOM guard: an A0
 *   poster at 200 dpi is 9000 px across.
 * @property preferDigitalLayer try the PDF's embedded text layer before OCR. Exact and
 *   ~1000× faster when the PDF has one. Requires the `uncial-pdf-text` module on the
 *   classpath; without it this flag is inert.
 * @property recognitionQuality speed/accuracy trade-off.
 * @property rasterColor pixel format for rasterization.
 * @property includeWords also produce per-word boxes and confidences. Costs extra work in
 *   every engine, so it is off by default.
 * @property pageRange zero-based, inclusive range of pages to process, or `null` for all.
 *   Must not be reversed: `5..2` is rejected rather than quietly producing an empty
 *   document. A range that runs past the end is fine — it is clamped to the real page
 *   count — but one that starts past the end selects nothing and fails the extraction.
 */
public data class OcrOptions(
    public val languages: List<OcrLanguage> = OcrLanguage.Default,
    public val renderDpi: Int = DEFAULT_RENDER_DPI,
    public val maxPageSide: Int = DEFAULT_MAX_PAGE_SIDE,
    public val preferDigitalLayer: Boolean = true,
    public val recognitionQuality: RecognitionQuality = RecognitionQuality.Accurate,
    public val rasterColor: RasterColor = RasterColor.Grayscale,
    public val includeWords: Boolean = false,
    public val pageRange: IntRange? = null,
) {
    init {
        require(languages.isNotEmpty()) { "at least one language is required" }
        require(renderDpi in MIN_RENDER_DPI..MAX_RENDER_DPI) {
            "renderDpi must be in $MIN_RENDER_DPI..$MAX_RENDER_DPI, was $renderDpi"
        }
        require(maxPageSide >= MIN_PAGE_SIDE) {
            "maxPageSide must be at least $MIN_PAGE_SIDE px, was $maxPageSide"
        }
        require(pageRange == null || pageRange.first >= 0) {
            "pageRange must start at 0 or later, was $pageRange"
        }
        // A reversed range selects nothing, and a silently empty document is a far worse
        // answer to "give me pages 5 to 2" than a rejected argument.
        require(pageRange == null || pageRange.last >= pageRange.first) {
            "pageRange must not be reversed, was $pageRange"
        }
    }

    /**
     * The scale factor [renderDpi] asks for, given that PDF user space is 72 dpi.
     *
     * This is the *requested* scale and ignores [maxPageSide]. Anything that has a page in
     * hand should use [scaleForPage] instead, which applies the same clamp the rasterizers
     * do — otherwise a large page ends up rasterized at one scale and its text layer
     * measured at another, and the two stop being comparable.
     */
    public val renderScale: Float get() = renderDpi / PDF_POINTS_PER_INCH

    /**
     * The scale actually used for a page of [widthPoints] x [heightPoints].
     *
     * [renderScale] clamped so the long side does not exceed [maxPageSide]. Every consumer
     * of a page's geometry has to apply this identically, or a document whose pages come
     * partly from the digital text layer and partly from OCR reports two different
     * coordinate scales in one result.
     *
     * @return [renderScale] for a page small enough to need no clamping, and a smaller
     *   factor otherwise. Degenerate (non-positive) dimensions fall back to [renderScale].
     */
    public fun scaleForPage(widthPoints: Float, heightPoints: Float): Float {
        val longSidePoints = maxOf(widthPoints, heightPoints)
        if (longSidePoints <= 0f) return renderScale
        val longSidePixels = longSidePoints * renderScale
        if (longSidePixels <= maxPageSide) return renderScale
        return renderScale * (maxPageSide / longSidePixels)
    }

    public companion object {
        /** PDF user space is defined in points: 72 per inch. */
        public const val PDF_POINTS_PER_INCH: Float = 72f

        public const val DEFAULT_RENDER_DPI: Int = 200
        public const val DEFAULT_MAX_PAGE_SIDE: Int = 2600

        public const val MIN_RENDER_DPI: Int = 72
        public const val MAX_RENDER_DPI: Int = 1200
        public const val MIN_PAGE_SIDE: Int = 64

        /** Options tuned for speed over accuracy: half the resolution, fast recognition. */
        public val Fast: OcrOptions = OcrOptions(
            renderDpi = 150,
            recognitionQuality = RecognitionQuality.Fast,
        )
    }
}
