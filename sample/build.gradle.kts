plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.google.ksp)
}

dependencies {
    implementation(projects.annotations)
    ksp(projects.compiler)
    implementation(libs.datastore.preferences.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okio)
}
