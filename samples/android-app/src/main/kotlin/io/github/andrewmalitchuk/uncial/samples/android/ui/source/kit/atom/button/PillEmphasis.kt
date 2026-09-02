package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button

import androidx.compose.foundation.border

/** How much weight a [PillButton] carries on its screen. */
enum class PillEmphasis {
    /** The screen's single action: ink fill, white label. */
    Primary,

    /** A real alternative to [Primary]: paper fill, hairline border. */
    Secondary,

    /** An action the user is not expected to take: no fill at all. */
    Ghost,
}
