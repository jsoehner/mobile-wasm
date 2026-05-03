plugins {
    id("com.android.application") version "8.7.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("org.owasp.dependencycheck") version "9.0.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}
// Apply the plugins to all subprojects
subprojects {
    apply(plugin = "org.owasp.dependencycheck")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        // Do not fail the build on style violations
        ignoreFailures = true
    }
}

// DependencyCheck configuration disabled due to compatibility issues
// dependencyCheck {
//     // Use the OWASP Dependency-Check CLI
//     failBuildOnCVSS = 7.0f
//     suppressionFile = "dependency-check-suppressions.xml"
//     // Skip network update to avoid 403 when no API key is provided
//     nvd {
//         update = false
//     }
// }
