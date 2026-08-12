package io.github.andrewmalitchuk.uncial.model.source.structure

/**
 * A semantic block of a reconstructed document.
 *
 * This is the output of `DocumentStructure.reconstruct` — the part of Uncial that is
 * actually hard to buy elsewhere. A wrapper over Vision gives you lines; turning lines
 * back into headings and paragraphs is the point.
 *
 * ### Compatibility
 *
 * This hierarchy is expected to **grow**: lists, tables and multi-column handling are
 * planned, and each will add a subtype in a minor release. A `when` over `DocBlock`
 * should therefore always carry an `else` branch, even where the compiler currently
 * considers it exhaustive — otherwise a minor upgrade will stop your build.
 */
public sealed interface DocBlock {

    /** The block's text content, with lines already joined and hyphenation resolved. */
    public val text: String

    /**
     * A heading.
     *
     * @property level 1 for a top-level heading, 2 for a subheading. Derived from how far
     *   the line's type size exceeds the document's median, not from any real document
     *   outline — a scanned page has no outline to read.
     */
    public data class Heading(
        public val level: Int,
        override val text: String,
    ) : DocBlock {
        init {
            require(level >= 1) { "heading level must be 1 or greater, was $level" }
        }
    }

    /** A body paragraph, reflowed from one or more recognized lines. */
    public data class Paragraph(
        override val text: String,
    ) : DocBlock
}
