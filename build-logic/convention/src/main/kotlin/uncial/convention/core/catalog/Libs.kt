package uncial.convention.core.catalog

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The `libs` version catalog, as seen from a project the convention plugins are applied to.
 *
 * A binary plugin gets none of the generated `libs.` accessors a build script has, so every
 * lookup goes through this catalog by alias. The aliases are resolved with `.get()` on
 * purpose: a typo should fail configuration, not silently skip a version.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named(CATALOG_NAME)

private const val CATALOG_NAME = "libs"
