package uncial.convention.source.base

import org.gradle.api.Plugin
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plugin ids are API -- every module names them as strings -- and so is the
 * `implementationClass` behind each one, which nothing checks at compile time. Rename or
 * move a plugin class without updating `build.gradle.kts` and the build fails at apply
 * time, in whichever module applies it first, with a `ClassNotFoundException`.
 *
 * This reads the generated plugin descriptors off the test classpath and resolves each
 * class, which is the same lookup Gradle performs -- without running a build.
 */
class PluginRegistrationTest {

    @Test
    fun `every plugin id resolves to a convention plugin class`() {
        PLUGIN_IDS.forEach { id ->
            val descriptor = assertNotNull(
                javaClass.getResourceAsStream("/META-INF/gradle-plugins/$id.properties"),
                "no descriptor for '$id' -- is it registered in build.gradle.kts?",
            )
            val implementationClass = descriptor.use { stream ->
                Properties().apply { load(stream) }.getProperty("implementation-class")
            }
            assertNotNull(implementationClass, "'$id' has no implementation-class")

            val type = runCatching { Class.forName(implementationClass) }.getOrNull()
            assertNotNull(type, "'$id' points at $implementationClass, which does not exist")
            assertTrue(
                Plugin::class.java.isAssignableFrom(type),
                "$implementationClass is not a Plugin<Project>",
            )
            assertTrue(
                BaseConventionPlugin::class.java.isAssignableFrom(type),
                "$implementationClass must extend BaseConventionPlugin so the step order is fixed",
            )
        }
    }

    @Test
    fun `no plugin is registered that this list does not know about`() {
        // The descriptors are generated one per `register` block, so a new plugin that
        // nobody documented shows up here rather than in a reviewer's memory.
        val descriptors = javaClass.getResource("/META-INF/gradle-plugins")
            ?.toURI()
            ?.let { java.io.File(it).list() }
            .orEmpty()
            .filter { it.endsWith(".properties") }
            .map { it.removeSuffix(".properties") }
            .sorted()

        assertEquals(PLUGIN_IDS.sorted(), descriptors)
    }

    private companion object {
        val PLUGIN_IDS = listOf(
            "uncial.kmp.base",
            "uncial.target.android",
            "uncial.target.jvm",
            "uncial.target.ios",
            "uncial.language-data",
            "uncial.publish",
            "uncial.publish.aggregation",
        )
    }
}
