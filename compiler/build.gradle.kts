plugins {
    alias(libs.plugins.publish)
    kotlin("jvm")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.sahsenvar",
        artifactId = "kmemory-compiler",
        version = "0.1.0"
    )

    pom {
        name = "KMemory Compiler"
        description = "This module contains the KSP processor for the KMemory library. It is a separate module to avoid adding unnecessary dependencies to the main library."
        inceptionYear = "2026"
        url = "https://github.com/sahsenvar/KMemory"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "sahsenvar"
                name = "Sahan Senvar"
                url = "https://github.com/sahsenvar"
            }
        }

        scm {
            url = "https://github.com/sahsenvar/KMemory"
        }
    }
}

dependencies {
    // KSP
    implementation(libs.symbol.processing)

    // KOIN
    implementation(libs.koin.core)

    // KOTLIN
    implementation(libs.kotlinx.coroutines)

    // KMEMORY MODULES
    implementation(projects.annotations)
}
