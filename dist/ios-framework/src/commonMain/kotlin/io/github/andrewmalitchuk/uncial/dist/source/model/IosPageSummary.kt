package io.github.andrewmalitchuk.uncial.dist.source.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * One page's contribution to a finished result.
 *
 * @property confidence the mean of the page's known line confidences, or `null` when the
 *   engine reported none. A digital text layer is not a guess, so it reports nothing —
 *   which is not the same claim as "zero".
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UncialPageSummary", exact = true)
public data class IosPageSummary(
    public val number: Int,
    public val lines: Int,
    public val confidence: Float?,
)
