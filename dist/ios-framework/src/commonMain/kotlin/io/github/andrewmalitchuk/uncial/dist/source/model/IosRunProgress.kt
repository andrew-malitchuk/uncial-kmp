package io.github.andrewmalitchuk.uncial.dist.source.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * How far an extraction has got, in the units the SDK actually reports.
 *
 * Kotlin's `Flow` does not survive export to Obj-C, so progress reaches Swift as a
 * callback instead. This is the shape that callback carries. (PLAN.md §6.3)
 *
 * @property completed pages finished so far.
 * @property total pages the document turned out to have; `0` before `OcrProgress.Started`
 *   has said, which for a PDF is not immediately.
 * @property source which path produced these pages — `Ocr`, `DigitalTextLayer` or `Mixed`.
 *   `null` until the same point: whether the engine runs at all is decided after the
 *   document has been opened.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UncialRunProgress", exact = true)
public data class IosRunProgress(
    public val completed: Int,
    public val total: Int,
    public val source: String?,
)
