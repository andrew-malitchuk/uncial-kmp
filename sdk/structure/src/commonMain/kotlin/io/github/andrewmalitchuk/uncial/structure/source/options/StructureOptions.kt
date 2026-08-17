package io.github.andrewmalitchuk.uncial.structure.source.options

/**
 * Thresholds the structure heuristics compare against.
 *
 * These were hardcoded constants in the POC. They are options here because they are
 * genuinely document-dependent: a technical manual with 11 pt body text and 12 pt
 * headings needs a lower [headingRatio] than a novel, and a document with no running
 * headers should not have [chromeFrequency] guessing at them.
 *
 * The defaults are the POC's values, which were tuned on Ukrainian scanned books.
 *
 * @property headingRatio a line whose type size is at least this multiple of the
 *   document's median is treated as a heading.
 * @property h1Ratio a heading at least this large is level 1 rather than level 2.
 * @property paragraphGapRatio a vertical gap larger than this multiple of the line's own
 *   height starts a new paragraph.
 * @property chromeFrequency a first/last line of a page whose normalized text repeats on
 *   at least this fraction of pages is treated as a running header or footer and dropped.
 * @property maxHeadingWords a long line is prose in large type, not a heading, no matter
 *   its size.
 * @property minPagesForChromeDetection running-header detection needs a few pages before
 *   repetition means anything.
 * @property dropChrome whether page furniture is removed at all — running headers and
 *   footers *and* lone page numbers, which are the same decision even though they are
 *   found by different rules. `false` keeps everything, for documents whose chrome is
 *   content: a dictionary's guide words, a numbered index.
 */
public data class StructureOptions(
    public val headingRatio: Float = 1.25f,
    public val h1Ratio: Float = 1.6f,
    public val paragraphGapRatio: Float = 1.2f,
    public val chromeFrequency: Float = 0.4f,
    public val maxHeadingWords: Int = 20,
    public val minPagesForChromeDetection: Int = 4,
    public val dropChrome: Boolean = true,
) {
    init {
        require(headingRatio > 1f) { "headingRatio must exceed 1, was $headingRatio" }
        require(h1Ratio >= headingRatio) {
            "h1Ratio ($h1Ratio) must be at least headingRatio ($headingRatio)"
        }
        require(paragraphGapRatio > 0f) { "paragraphGapRatio must be positive" }
        require(chromeFrequency in 0f..1f) { "chromeFrequency must be a fraction 0..1" }
        require(maxHeadingWords > 0) { "maxHeadingWords must be positive" }
    }

    public companion object {
        /** The POC's tuning, kept as the default. */
        public val Default: StructureOptions = StructureOptions()

        /**
         * Keeps page furniture instead of dropping it: running headers, running footers
         * and page numbers all survive into the blocks.
         *
         * A flag rather than an unreachable threshold, because the two chrome rules are
         * reached differently -- repetition needs a page count, a lone page number does
         * not -- and only a flag can switch off both.
         */
        public val KeepChrome: StructureOptions = StructureOptions(dropChrome = false)
    }
}
