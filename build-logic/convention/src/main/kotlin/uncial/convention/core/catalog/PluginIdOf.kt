package uncial.convention.core.catalog

import org.gradle.api.Project

/**
 * A plugin id from the catalog, so the plugins a convention plugin applies are versioned in
 * the same file as everything else the build depends on.
 */
internal fun Project.pluginIdOf(alias: String): String =
    libs.findPlugin(alias).get().get().pluginId
