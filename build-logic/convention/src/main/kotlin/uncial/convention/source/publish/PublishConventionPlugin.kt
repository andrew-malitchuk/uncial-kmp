package uncial.convention.source.publish

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import nmcp.NmcpExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.plugins.signing.SigningExtension
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.naming.artifactId
import uncial.convention.core.publish.isSnapshot
import uncial.convention.core.publish.PublishingSecrets
import uncial.convention.core.publish.uncialMetadata

/**
 * Makes a module publishable: publications, POM, sources jar, signing, and the handshake
 * with the root project's aggregated upload.
 *
 * Applying this is only half of publishing a module -- the other half is adding it to
 * [PublishAggregationConventionPlugin]'s list, which is what actually puts it in the
 * deployment. A module that applies this and is missing from that list builds signed
 * artifacts locally and never reaches the Portal. (PUBLISHING.md §A2)
 */
class PublishConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        // `.base` rather than the full plugin: the full one drives itself from Gradle
        // properties (SONATYPE_HOST, RELEASE_SIGNING_ENABLED, ...), and every one of those
        // decisions is made here instead, in code that can be read.
        pluginManager.apply("com.vanniktech.maven.publish.base")
        // The Central Portal uploader. vanniktech's own uploader cannot be used: it reads
        // credentials exclusively through providers.gradleProperty, which is blind to
        // configure/signing/secrets.properties. (PUBLISHING.md §A0)
        pluginManager.apply("com.gradleup.nmcp")
        pluginManager.apply("signing")
    }

    override fun Project.configureArtifacts() {
        configurePublication()
        configureAggregationHandshake()
        configureSigning()
    }

    private fun Project.configurePublication() = extensions.configure<MavenPublishBaseExtension> {
        // `uncial-model`, `uncial-core`, ... matching base.archivesName from
        // uncial.kmp.base. KMP appends the target itself, so this one line also names
        // uncial-core-android, uncial-core-jvm, uncial-core-iosarm64 and the rest. Group and
        // version come from gradle.properties, the single source of truth. (PLAN.md §9.1)
        coordinates(
            groupId = group.toString(),
            artifactId = artifactId,
            version = version.toString(),
        )

        // Sources are real; javadoc is an empty jar that exists only because Maven Central
        // rejects an artifact without one. Dokka replaces it in phase 6. (PUBLISHING.md §A4)
        configureBasedOnAppliedPlugins(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
        )

        pom { uncialMetadata(project) }

        // Signs every publication, and -- the reason to call this rather than sign by hand
        // -- redirects the KMP Sign tasks into per-task directories, without which the klib
        // signatures of several targets collide (KT-61313).
        //
        // It also marks signing required for releases and optional for snapshots.
        signAllPublications()
    }

    /**
     * Exposes this module's publications to the root project's aggregation.
     *
     * Registering the per-module upload task is a side effect of asking for that
     * configuration; it is never run -- the release goes out as one aggregated bundle, from
     * the root. (PUBLISHING.md §A1)
     */
    private fun Project.configureAggregationHandshake() = extensions.configure<NmcpExtension> {
        publishAllPublications {}
    }

    /**
     * Installs the GPG key from the secrets file.
     *
     * `signAllPublications()` picks up an in-memory key only from a Gradle property, which
     * `configure/signing/secrets.properties` can never become. Absent key: snapshots still
     * publish, releases fail at signing time -- which is the intended asymmetry, so the
     * warning is a warning and not an error.
     */
    private fun Project.configureSigning() = extensions.configure<SigningExtension> {
        val secrets = PublishingSecrets(project)
        val key = secrets.signingKey

        if (key != null) {
            useInMemoryPgpKeys(secrets.signingKeyId, key, secrets.signingKeyPassword.orEmpty())
        } else if (!isSnapshot) {
            logger.warn(
                "No GPG key for $path: set SIGNING_KEY or configure/signing/secrets.properties. " +
                    "Signing a release will fail. See configure/signing/README.md.",
            )
        }
    }
}
