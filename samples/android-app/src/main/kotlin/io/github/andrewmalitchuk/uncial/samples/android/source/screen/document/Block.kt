package io.github.andrewmalitchuk.uncial.samples.android.source.screen.document

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.BlockRow

// The pieces [DocumentScreen] renders into: one reconstructed block, and the two things
// the screen says when there is nothing to show. Split out because they are about
// rendering a DocBlock, not about laying out the screen.

@Suppress("REDUNDANT_ELSE_IN_WHEN")
@Composable
internal fun Block(block: DocBlock) {
    when (block) {
        is DocBlock.Heading -> BlockRow(
            glyph = "H${block.level}",
            text = block.text,
            meta = stringResource(R.string.block_heading_meta, block.level),
            isHeading = true,
        )
        is DocBlock.Paragraph -> BlockRow(
            glyph = "¶",
            text = block.text,
            meta = stringResource(R.string.block_paragraph_meta),
        )
        // The hierarchy grows in minor releases; always keep a fallback.
        else -> BlockRow(glyph = "·", text = block.text, meta = block::class.simpleName.orEmpty())
    }
}
