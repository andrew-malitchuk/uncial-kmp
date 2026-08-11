package uncial.convention.source.kmp

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.naming.artifactId
import uncial.convention.core.catalog.intVersionOf
import uncial.convention.core.dsl.kotlinMultiplatform
import uncial.convention.core.catalog.pluginIdOf

/**
 * The floor every Uncial module stands on: Kotlin Multiplatform with a strict public API
 * and a committed ABI dump.
 *
 * It registers no targets. A module says which platforms it is for by applying
 * `uncial.target.android` / `.jvm` / `.ios` on top of this, which keeps the platform matrix
 * -- the thing that genuinely varies across the module graph -- visible in the module's own
 * `plugins { }` block. (PLAN.md §6.6)
 */
class KmpBaseConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        pluginManager.apply(pluginIdOf("kotlinMultiplatform"))
    }

    override fun Project.configureKotlin() = kotlinMultiplatform {
        jvmToolchain(intVersionOf("jdk"))

        // Every published module is strict about its public surface: no inferred
        // visibility, no accidental API. (PLAN.md §6)
        explicitApi()

        // Kotlin's built-in ABI validation replaces the standalone
        // binary-compatibility-validator plugin: ./gradlew updateKotlinAbi / checkKotlinAbi.
        // Calling this enables validation; there is no `enabled` property any more.
        abiValidation()

        compilerOptions {
            // `expect class` is Beta and warns on every compilation. Raster genuinely has to
            // be one -- it wraps Bitmap / BufferedImage / CGImage -- so the warning is
            // noise, not a signal.
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    override fun Project.configureSourceSets() = kotlinMultiplatform {
        sourceSets.commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    override fun Project.configureArtifacts() {
        // Published artifacts are uncial-model, uncial-core, ... while Gradle paths stay
        // readable as :sdk:model. Keeps the klib unique name and the AAR/JAR names aligned
        // with the Maven coordinates. (PLAN.md §6)
        extensions.configure<BasePluginExtension> {
            archivesName.set(artifactId)
        }
    }
}
