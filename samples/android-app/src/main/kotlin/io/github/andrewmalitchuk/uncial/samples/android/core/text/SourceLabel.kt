package io.github.andrewmalitchuk.uncial.samples.android.core.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.samples.android.R

/**
 * Which path produced a result, in words rather than as an enum constant.
 *
 * `Ocr` and `DigitalTextLayer` are names for a compiler; what a reader needs to know is
 * that one of them is a guess and the other is not.
 */
@Suppress("REDUNDANT_ELSE_IN_WHEN")
@Composable
fun ExtractionSource.label(): String = stringResource(
    when (this) {
        ExtractionSource.Ocr -> R.string.source_ocr
        ExtractionSource.DigitalTextLayer -> R.string.source_digital_layer
        ExtractionSource.Mixed -> R.string.source_mixed
        // The enum is documented as growing in minor releases.
        else -> R.string.source_unknown
    },
)
