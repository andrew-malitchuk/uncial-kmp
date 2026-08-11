package uncial.convention.source.target

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.dsl.kotlinMultiplatform
import uncial.convention.core.catalog.pluginIdOf
import uncial.convention.core.catalog.versionOf

/**
 * Adds the JVM target to a KMP module.
 *
 * The bytecode target is lower than the toolchain on purpose: Uncial compiles on JDK 21 and
 * targets Java 11, so a consumer stuck on 11 can still use the JVM artifact.
 */
class JvmTargetConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        pluginManager.apply(pluginIdOf("kotlinMultiplatform"))
    }

    override fun Project.configureTargets() = kotlinMultiplatform {
        jvm {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(versionOf("jvmTarget")))
            }
        }
    }
}
