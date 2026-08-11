package uncial.convention.source.target

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.dsl.androidLibrary
import uncial.convention.core.naming.androidNamespace
import uncial.convention.core.catalog.intVersionOf
import uncial.convention.core.catalog.pluginIdOf
import uncial.convention.core.catalog.versionOf
import java.io.File

/**
 * Adds the Android target to a KMP module.
 *
 * AGP 9 compiles Kotlin itself, so this applies `com.android.kotlin.multiplatform.library`
 * -- never `org.jetbrains.kotlin.android`, which AGP 9 rejects outright. There are no
 * flavors, no build types and no `BuildConfig` in this DSL; a library needs none of them.
 */
class AndroidTargetConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        pluginManager.apply(pluginIdOf("kotlinMultiplatform"))
        pluginManager.apply(pluginIdOf("androidKmpLibrary"))
    }

    override fun Project.configureTargets() = androidLibrary {
        // The SDK has no resources, so the namespace only has to be unique. Derived from
        // the module name, so a new module needs no wiring.
        namespace = androidNamespace
        compileSdk = intVersionOf("android-compileSdk")
        minSdk = intVersionOf("android-minSdk")

        withHostTestBuilder {}

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(versionOf("jvmTarget")))
        }
    }

    override fun Project.configureArtifacts() {
        // Any consumer-rules/*.pro in the module travels INSIDE the AAR, so a consumer's
        // release build keeps what JNI and androidx.startup reach reflectively. Wired here
        // rather than per module because AGP's DSL types are only on the classpath of the
        // plugin that applies AGP. (PLAN.md §8.5)
        val rules = consumerKeepRules()
        if (rules.isEmpty()) return

        androidLibrary {
            optimization {
                // consumerKeepRules is a read-only property, not a configuration block.
                consumerKeepRules.publish = true
                rules.forEach { file -> consumerKeepRules.file(file) }
            }
        }
    }

    /**
     * The module's consumer ProGuard rules, resolved eagerly -- and this cannot be made lazy.
     *
     * AGP's `ConsumerKeepRules.file(Any)` resolves its argument through `DslServices.file()`
     * into a `MutableList<java.io.File>` on the spot, so handing it a Provider or a
     * FileCollection buys nothing: it would be unwrapped at the same moment. The cost is one
     * directory listing per module, and the configuration cache records that listing as an
     * input, so adding or deleting a .pro invalidates it correctly.
     */
    private fun Project.consumerKeepRules(): List<File> =
        fileTree(CONSUMER_RULES_DIRECTORY) { include("*.pro") }
            .files
            .sortedBy { it.name }

    private companion object {
        const val CONSUMER_RULES_DIRECTORY = "consumer-rules"
    }
}
