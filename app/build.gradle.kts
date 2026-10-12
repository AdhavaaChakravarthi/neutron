import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI run number makes every build an upgrade of the last one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.adhavaa.commander"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adhavaa.commander"
        minSdk = 34
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
    }

    // One fixed key for every build, so each new APK installs over the old one and keeps your data.
    // Personal sideload only; see DECISIONS.md D-28.
    signingConfigs {
        create("personal") {
            storeFile = file("signing/commander.keystore")
            storePassword = "commander"
            keyAlias = "commander"
            keyPassword = "commander"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Render tests write PNGs here; CI attaches them to the release.
                test.systemProperty("screenshot.dir", project.layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
                // A hung test fails the build instead of the job running for hours; the log names the last test started.
                test.timeout.set(java.time.Duration.ofMinutes(20))
                test.testLogging { events("started", "failed") }
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Play Store target-SDK deadlines don't apply to a sideloaded personal app.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Room writes each schema version here so later releases can migrate data instead of wiping it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
}
