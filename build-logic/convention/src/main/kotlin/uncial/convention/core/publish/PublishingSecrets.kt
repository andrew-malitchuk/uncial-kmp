package uncial.convention.core.publish

import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Resolves the credentials needed to sign and upload the published artifacts.
 *
 * The resolution order is axiom-sdk's, unchanged:
 *
 * 1. an environment variable — CI;
 * 2. `configure/signing/secrets.properties` — local development, gitignored;
 * 3. an ordinary Gradle property — `~/.gradle/gradle.properties`.
 *
 * The secrets file deliberately keeps axiom's key names, so one file copied verbatim works
 * for both SDKs; the mapping onto whatever a plugin wants happens at the call site.
 *
 * Nothing here reaches for `providers.gradleProperty`, and that is the whole point: Gradle 9
 * loads its properties before the settings script runs, so a project-local file can never
 * become a Gradle property, and every credential has to be handed to a plugin as a value.
 * (PUBLISHING.md §A0)
 *
 * `Properties.load` turns the literal `\n` inside the single-line PGP block back into real
 * newlines, which is what makes that mandatory single line work at all.
 */
internal class PublishingSecrets(
    rootDir: File,
    private val gradleProperty: (String) -> String?,
) {
    /**
     * The only form the convention plugins use.
     *
     * `findProperty` rather than `providers.gradleProperty`, for the reason above: a
     * project-local secrets file can never become a Gradle property.
     */
    constructor(project: Project) : this(
        rootDir = project.rootDir,
        gradleProperty = { key -> project.findProperty(key)?.toString() },
    )

    private val fromFile: Properties =
        Properties().apply {
            val file = rootDir.resolve("configure/signing/secrets.properties")
            if (file.exists()) file.reader().use { load(it) }
        }

    /** Short GPG key id, e.g. `15FF4516`. */
    val signingKeyId: String? get() = resolve("SIGNING_KEY_ID", "signing.keyId")

    /** The ASCII-armored private key block. */
    val signingKey: String? get() = resolve("SIGNING_KEY", "signing.key")

    /** Passphrase of [signingKey]. */
    val signingKeyPassword: String? get() = resolve("SIGNING_KEY_PASSWORD", "signing.password")

    /** Central Portal user token, username half. */
    val centralUsername: String? get() = resolve("CENTRAL_USERNAME", "centralUsername")

    /** Central Portal user token, password half. */
    val centralPassword: String? get() = resolve("CENTRAL_PASSWORD", "centralPassword")

    private fun resolve(environmentVariable: String, key: String): String? =
        System.getenv(environmentVariable)
            ?: fromFile.getProperty(key)
            ?: gradleProperty(key)
}
