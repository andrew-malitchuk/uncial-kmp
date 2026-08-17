package io.github.andrewmalitchuk.uncial.structure.source.reconstruction

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.structure.core.builder.BlockBuilder
import io.github.andrewmalitchuk.uncial.structure.source.options.StructureOptions

/**
 * Turns recognized geometry back into semantics: [OcrDocument] to [DocBlock]s.
 *
 * This is the part of Uncial that is hard to replace. Any engine gives you lines; deciding
 * that *this* line is a chapter heading, that *those* four lines are one paragraph broken
 * by a page turn, and that the text at the top of every page is a running header nobody
 * wants — that is the differentiator. (PLAN.md §2)
 *
 * It is a pure function with no platform dependencies and no coroutines, which is why it
 * lives in its own module: fully testable, releasable on its own, and skippable by callers
 * who only want raw text.
 *
 * ### What it assumes
 *
 * Single-column, reflowable prose. Multi-column layouts, tables and lists are not handled
 * yet — they are the reason [DocBlock] is expected to grow. Given a two-column scan it
 * will interleave the columns, because it sorts by vertical position.
 *
 * ```
 * val blocks = DocumentStructure.reconstruct(document)
 * ```
 */
public object DocumentStructure {

    /**
     * Reconstructs the document's block structure.
     *
     * @param document a recognized document from either extraction path.
     * @param options thresholds to compare against; see [StructureOptions].
     * @return blocks in reading order. Empty if the document has no non-blank text.
     */
    public fun reconstruct(
        document: OcrDocument,
        options: StructureOptions = StructureOptions.Default,
    ): List<DocBlock> {
        // "No text at all" is the only reason to give up. An unusable median is not:
        // plenty of recognizers report no type size whatsoever, and returning nothing for
        // a document full of words would be silent data loss. See headingLevel.
        if (document.pages.none { page -> page.lines.any { it.text.isNotBlank() } }) {
            return emptyList()
        }
        val bodyFontSize = medianFontSize(document)

        val chrome = detectChrome(document, options)
        val builder = BlockBuilder()

        for (page in document.pages) {
            val ordered = page.readingOrder()
            // Running headers and footers can only be the first or last line of a page,
            // so the edges are identified once per page rather than per line.
            val firstText = ordered.firstOrNull()?.normalizedText()
            val lastText = ordered.lastOrNull()?.normalizedText()

            ordered.forEachIndexed { position, line ->
                val atEdge = position == 0 || position == ordered.lastIndex
                // Heading usually beats chrome. Chrome is found by repetition with digits
                // normalized away, so in a book whose pages open with "Розділ 3",
                // "Розділ 4", ... every chapter title collapses to one key, clears the
                // occurrence threshold, and would be deleted as a running header.
                //
                // The exception is a repeat whose RAW text is identical every time. That
                // is a running head ("ІСТОРІЯ УКРАЇНИ" atop all 300 pages), and setting it
                // in display type does not make it content -- without this it would be
                // emitted as a heading once per page. The distinction is exactly the digit
                // normalization: chapter titles differ before it, running heads do not.
                val verbatimRepeat = atEdge && line.normalizedText() in chrome.verbatimKeys
                val heading = if (verbatimRepeat) null else line.headingLevel(bodyFontSize, options)
                if (heading != null) {
                    builder.flush()
                    builder.add(DocBlock.Heading(heading, line.text.trim()))
                    return@forEachIndexed
                }

                if (isChrome(line, atEdge, firstText, lastText, chrome.keys, options)) {
                    return@forEachIndexed
                }
                builder.append(line, page.index, options)
            }
        }
        builder.flush()
        return builder.blocks()
    }

    /**
     * The heading level for this line, or `null` if it is body text.
     *
     * Size alone is not enough: a whole paragraph set in large type is still a paragraph,
     * so anything longer than [StructureOptions.maxHeadingWords] is rejected.
     *
     * @param bodyFontSize the document's median, or `0f` when it has no usable type sizes
     *   — in which case nothing can be judged large and every line is body text.
     */
    private fun OcrLine.headingLevel(bodyFontSize: Float, options: StructureOptions): Int? {
        if (bodyFontSize <= 0f) return null
        // The same trap as the median, from the other side: `NaN < anything` is false, so
        // an unreported size would sail past the ratio test and make this line an H2. A
        // zero-size line is not evidence of a heading either -- it is evidence of a
        // producer that does not report type sizes.
        if (!fontSize.isFinite() || fontSize <= 0f) return null
        if (fontSize < bodyFontSize * options.headingRatio) return null
        if (wordCount() > options.maxHeadingWords) return null
        return if (fontSize >= bodyFontSize * options.h1Ratio) 1 else 2
    }

    /**
     * The document's median type size, used as the "this is body text" baseline.
     *
     * A median rather than a mean because headings and page numbers are outliers that
     * would drag a mean upwards and hide the very headings we are looking for.
     *
     * @return the median, or `0f` when there is nothing usable to take one of.
     */
    private fun medianFontSize(document: OcrDocument): Float {
        val sizes = document.pages
            .asSequence()
            .flatMap { it.lines.asSequence() }
            .filter { it.text.isNotBlank() }
            .map { it.fontSize }
            // Only real type sizes may vote. A single NaN poisons everything downstream --
            // it sorts last, so it can BE the median, and then every comparison against it
            // is false, so headingRatio never rejects a line. Zero is just as damaging in a
            // document that MIXES reported and unreported sizes (PDFBox reports 0 pt for
            // Type3 fonts and degenerate text matrices): the zeros drag the median below
            // the real body size, and ordinary text starts clearing the h1 ratio.
            .filter { it.isFinite() && it > 0f }
            .toMutableList()
        if (sizes.isEmpty()) return 0f
        sizes.sort()
        val mid = sizes.size / 2
        return if (sizes.size % 2 == 0) (sizes[mid - 1] + sizes[mid]) / 2f else sizes[mid]
    }

    /**
     * Normalized texts that repeat at the top or bottom of enough pages to be running
     * headers or footers rather than content.
     *
     * Digits are normalized away first, so "Chapter 3 — page 41" and "Chapter 3 — page 42"
     * count as the same header.
     */
    private fun detectChrome(document: OcrDocument, options: StructureOptions): Chrome {
        if (!options.dropChrome) return Chrome.None
        if (document.pages.size < options.minPagesForChromeDetection) return Chrome.None

        val edgeCounts = mutableMapOf<String, Int>()
        // Whether every occurrence of a key had the same raw text, or only the same text
        // once digits were normalized away. See the call site: it is what separates a
        // running head from a chapter title.
        val verbatim = mutableMapOf<String, String?>()
        for (page in document.pages) {
            val ordered = page.readingOrder()
            if (ordered.isEmpty()) continue
            val edges = if (ordered.size == 1) {
                listOf(ordered.first())
            } else {
                listOf(ordered.first(), ordered.last())
            }
            for (line in edges) {
                val key = line.normalizedText()
                edgeCounts[key] = (edgeCounts[key] ?: 0) + 1
                val raw = line.text.trim()
                if (key !in verbatim) {
                    verbatim[key] = raw
                } else if (verbatim[key] != raw) {
                    verbatim[key] = null
                }
            }
        }
        // At least 2: a line seen ONCE is not repeating, whatever the fraction works out
        // to. Without this floor a 4-page document gets a threshold of int(4 * 0.4) = 1,
        // and every page's last line is classified as a running footer and dropped --
        // which is exactly what happened on a real 4-page scan.
        val threshold = (document.pages.size * options.chromeFrequency)
            .toInt()
            .coerceAtLeast(MIN_CHROME_OCCURRENCES)
        val keys = edgeCounts.filterValues { it >= threshold }.keys
        return Chrome(
            keys = keys,
            verbatimKeys = keys.filterTo(mutableSetOf()) { verbatim[it] != null },
        )
    }

    /**
     * The repeating edge lines of a document.
     *
     * @property keys every normalized text that repeats often enough to be chrome.
     * @property verbatimKeys the subset whose raw text was identical on every page — a
     *   running head rather than a chapter title that merely normalizes to one key.
     */
    private class Chrome(val keys: Set<String>, val verbatimKeys: Set<String>) {
        companion object {
            val None = Chrome(emptySet(), emptySet())
        }
    }

    private fun isChrome(
        line: OcrLine,
        atEdge: Boolean,
        firstText: String?,
        lastText: String?,
        chrome: Set<String>,
        options: StructureOptions,
    ): Boolean {
        // The page-number rule answers to the same option as the running-header one. It
        // used to sit above this guard, so StructureOptions.KeepChrome kept the headers
        // and ate the page numbers anyway -- an option called Keep that silently drops
        // half of what it names.
        if (!options.dropChrome) return false
        val trimmed = line.text.trim()
        // A line that is nothing but digits is a page number wherever it sits.
        if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() || it.isWhitespace() }) return true
        if (!atEdge || chrome.isEmpty()) return false
        val normalized = line.normalizedText()
        return (normalized == firstText || normalized == lastText) && normalized in chrome
    }

    /**
     * Page lines in reading order: top to bottom, then left to right.
     *
     * Engines emit lines in their own order, which is usually reading order but is not
     * promised to be — Vision in particular reorders by confidence.
     */
    private fun OcrPage.readingOrder(): List<OcrLine> =
        lines.filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))

    private fun OcrLine.normalizedText(): String =
        text.trim().lowercase().replace(DIGITS, "#")

    private fun OcrLine.wordCount(): Int =
        text.trim().split(WHITESPACE).count { it.isNotEmpty() }

    /** A line has to appear at least this many times before repetition means anything. */
    private const val MIN_CHROME_OCCURRENCES = 2

    private val DIGITS = Regex("\\d+")
    private val WHITESPACE = Regex("\\s+")
}
