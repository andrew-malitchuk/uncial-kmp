package io.github.andrewmalitchuk.uncial.samples.cli.core.report

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.structure.source.reconstruction.DocumentStructure

/**
 * Prints the reconstructed headings and paragraphs.
 *
 * The compiler calls the `else` redundant, and today it is. Keep it anyway: `DocBlock` is
 * documented as growing in minor releases, so an exhaustive `when` without a fallback is a
 * build break waiting for an upgrade. This is the advice the SDK gives its consumers; a
 * sample should follow it.
 */
@Suppress("REDUNDANT_ELSE_IN_WHEN")
internal fun printStructure(document: OcrDocument) {
    println()
    println("── structure ──")
    DocumentStructure.reconstruct(document).forEach { block ->
        when (block) {
            is DocBlock.Heading -> println("${"#".repeat(block.level)} ${block.text}")
            is DocBlock.Paragraph -> println("${block.text}\n")
            else -> println(block.text)
        }
    }
}
