package uncial.convention.source.target

import org.gradle.api.Project
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.dsl.kotlinMultiplatform
import uncial.convention.core.catalog.pluginIdOf

/**
 * Adds the iOS targets to a KMP module.
 *
 * All three of them, always: device (`iosArm64`), Apple-silicon simulator
 * (`iosSimulatorArm64`) and Intel simulator (`iosX64`). An XCFramework has to carry every
 * slice a consumer might build for, and dropping one is a linker error in somebody else's
 * project rather than a failure here. (PUBLISHING.md §5)
 */
class IosTargetConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        pluginManager.apply(pluginIdOf("kotlinMultiplatform"))
    }

    override fun Project.configureTargets() = kotlinMultiplatform {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
    }
}
