package uncial.convention.core.catalog

import org.gradle.api.Project

/** A version from the catalog, e.g. `versionOf("jvmTarget")` -> `"11"`. */
internal fun Project.versionOf(alias: String): String =
    libs.findVersion(alias).get().requiredVersion
