package io.github.andrewmalitchuk.uncial.dist.source.model

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A finished extraction, reduced to what a UI shows.
 *
 * Computed in Kotlin rather than in Swift for one reason: `Confidence` is a Kotlin value
 * class over a `Float`, and value classes do not survive Obj-C export in any form a Swift
 * caller can do arithmetic on. Summing them is therefore Kotlin's job, and this type is the
 * result — which is exactly the kind of edge `:sdk:swift` exists to smooth.
 *
 * @property words `0` when `OcrOptions.includeWords` is off or the engine cannot do it.
 * @property confidence the mean over every known line confidence, or `null` when none were.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UncialDocumentSummary", exact = true)
public data class IosDocumentSummary(
    public val source: String,
    public val pageCount: Int,
    public val lines: Int,
    public val words: Int,
    public val confidence: Float?,
    public val pages: List<IosPageSummary>,
    public val blocks: List<DocBlock>,
) {
    public val isEmpty: Boolean get() = lines == 0
}
