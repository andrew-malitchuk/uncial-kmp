package uncial.convention.core.naming

import org.gradle.api.Project
import uncial.convention.core.identity.Uncial

/**
 * `io.github.andrewmalitchuk.uncial.engine.tesseract` -- unique per module, with no wiring.
 *
 * The SDK ships no Android resources, so a namespace only has to be unique; deriving it
 * from the module name means a new module needs none of this spelled out.
 */
internal val Project.androidNamespace: String
    get() = "${Uncial.ANDROID_NAMESPACE_PREFIX}.$moduleName"

/**
 * The module name in dotted form: `engine-tesseract` -> `engine.tesseract`.
 *
 * Taken from the project name rather than its path, so a module can move between `:sdk` and
 * `:dist` without changing the namespace its consumers have already seen.
 */
private val Project.moduleName: String
    get() = name.replace('-', '.')
