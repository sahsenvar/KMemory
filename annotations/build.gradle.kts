@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

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

    // KMemory calisma-zamani nesnesi paylasilan degistirilebilir bir store onbellegi tutuyor.
    // 0.1.0'da atomicfu eklemek yerine bu yuzey JVM + Android'e sabitlendi; anotasyonlar,
    // PreferenceListener ve ReturnAdapter commonMain'de kalir (iOS dahil tum hedefler).
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Uretilen kodun ve KMemoryBuilder'in imzalarinda gorundukleri icin api.
            api(libs.data.store.preferences)
            api(libs.kotlinx.coroutines)
            api(libs.kotlinx.serialization.json)
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
