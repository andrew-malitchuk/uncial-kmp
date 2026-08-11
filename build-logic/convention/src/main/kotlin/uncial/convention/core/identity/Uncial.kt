package uncial.convention.core.identity

/**
 * The facts about the SDK that more than one convention plugin needs to agree on.
 *
 * Everything here is deliberately a constant rather than a Gradle property: these are
 * identity, not configuration. Coordinates (`group`, `version`) are the exception and live
 * in `gradle.properties`, which is the single source of truth for them. (PLAN.md §9.1)
 */
internal object Uncial {

    /** Maven artifact ids are `uncial-<module>`; Gradle paths stay readable as `:sdk:model`. */
    const val ARTIFACT_PREFIX: String = "uncial-"

    /** Android namespaces are `<prefix>.<module>`; the SDK ships no resources, so uniqueness is all that matters. */
    const val ANDROID_NAMESPACE_PREFIX: String = "io.github.andrewmalitchuk.uncial"

    const val REPOSITORY_URL: String = "https://github.com/andrew-malitchuk/uncial-kmp"
    const val INCEPTION_YEAR: String = "2026"

    const val LICENSE_NAME: String = "The Apache License, Version 2.0"
    const val LICENSE_URL: String = "https://www.apache.org/licenses/LICENSE-2.0.txt"

    const val DEVELOPER_ID: String = "andrew-malitchuk"
    const val DEVELOPER_NAME: String = "Andrew Malitchuk"
    const val DEVELOPER_EMAIL: String = "andrew.malitchuk@gmail.com"
}
