plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.publish)
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.data.store.preferences)
            implementation(libs.kotlinx.coroutines)
        }
    }
}

android {
    namespace = "io.github.sahsenvar.kmemory"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.sahsenvar",
        artifactId = "kmemory-annotations",
        version = "0.1.0"
    )

    pom {
        name = "KMemory Annotations"
        description =
            "This module contains the annotations used by the KMemory library. It is a separate module to avoid adding unnecessary dependencies to the main library."
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
