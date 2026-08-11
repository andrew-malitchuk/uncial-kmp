package uncial.convention.core.publish

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom
import uncial.convention.core.identity.Uncial

/**
 * The POM every published Uncial module carries: everything Maven Central demands, and
 * nothing a module should have to repeat.
 *
 * The one field left to the module is [MavenPom.getDescription]. It is read from
 * `project.description` inside a `provider { }` so that the requirement is enforced when
 * the POM is generated rather than at apply time -- a module sets its description below its
 * `plugins { }` block, which has not run yet when this is called.
 */
internal fun MavenPom.uncialMetadata(project: Project) {
    name.set("Uncial ${project.name}")

    // Deliberately not defaulted: Central rejects a POM without a description, and a
    // generic one repeated twelve times tells a consumer nothing. A module that forgets to
    // describe itself fails here, at POM generation, rather than in the Portal.
    description.set(
        project.provider {
            requireNotNull(project.description) {
                "${project.path} is published but has no description; set one in its build.gradle.kts."
            }
        },
    )

    url.set(Uncial.REPOSITORY_URL)
    inceptionYear.set(Uncial.INCEPTION_YEAR)

    licenses {
        license {
            name.set(Uncial.LICENSE_NAME)
            url.set(Uncial.LICENSE_URL)
            distribution.set("repo")
        }
    }

    developers {
        developer {
            id.set(Uncial.DEVELOPER_ID)
            name.set(Uncial.DEVELOPER_NAME)
            email.set(Uncial.DEVELOPER_EMAIL)
        }
    }

    scm {
        val path = Uncial.REPOSITORY_URL.removePrefix("https://")
        connection.set("scm:git:$path.git")
        developerConnection.set("scm:git:ssh://$path.git")
        url.set("${Uncial.REPOSITORY_URL}/tree/main")
    }
}
