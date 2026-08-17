package io.github.andrewmalitchuk.uncial.structure.core.builder

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.structure.source.options.StructureOptions

/**
 * Accumulates lines into paragraphs and emits finished blocks.
 *
 * Kept separate from [DocumentStructure] because it holds all the mutable state of the
 * reconstruction — the paragraph being built, where the previous line was — which makes
 * the traversal in `reconstruct` readable as a sequence of decisions rather than a
 * bookkeeping exercise.
 */
internal class BlockBuilder {

    private val finished = mutableListOf<DocBlock>()
    private val paragraph = StringBuilder()
    private var previousLine: OcrLine? = null
    private var previousPageIndex = -1

    fun blocks(): List<DocBlock> = finished.toList()

    /** Emits a finished block, e.g. a heading, after flushing any pending paragraph. */
    fun add(block: DocBlock) {
        finished.add(block)
        previousLine = null
        previousPageIndex = -1
    }

    /**
     * Adds a body line, starting a new paragraph first if the vertical gap says so.
     *
     * A gap is only meaningful within one page: across a page break the previous line's
     * position says nothing, so a paragraph is assumed to continue. That is the right
     * default for books, where paragraphs routinely straddle the page turn.
     */
    fun append(line: OcrLine, pageIndex: Int, options: StructureOptions) {
        val previous = previousLine
        val samePage = previous != null && previousPageIndex == pageIndex
        if (samePage && paragraph.isNotEmpty()) {
            val gap = line.box.top - previous.box.bottom
            if (gap > line.box.height * options.paragraphGapRatio) flush()
        }
        appendText(line.text)
        previousLine = line
        previousPageIndex = pageIndex
    }

    /** Closes the pending paragraph, if any. */
    fun flush() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) finished.add(DocBlock.Paragraph(text))
        paragraph.clear()
    }

    /**
     * Joins a line onto the paragraph, resolving end-of-line hyphenation.
     *
     * A trailing hyphen is treated as a line-break hyphen and dropped, so `сло-` + `во`
     * becomes `слово`. This is wrong for a genuine compound that happens to break at its
     * hyphen — `Кam'янець-` + `Подільський` loses it — but the alternative needs a
     * dictionary per language, and dropping is wrong less often than keeping.
     */
    private fun appendText(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        when {
            paragraph.isEmpty() -> paragraph.append(trimmed)
            paragraph.last() == '-' -> {
                paragraph.deleteAt(paragraph.length - 1)
                paragraph.append(trimmed)
            }
            else -> paragraph.append(' ').append(trimmed)
        }
    }
}
