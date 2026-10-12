import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: every number the user sees is computed here and tested without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

val goldenDir = rootProject.file("tools/model-reference/golden")

tasks.test {
    systemProperty("golden.dir", goldenDir.absolutePath)
    inputs.dir(goldenDir).withPropertyName("golden")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
