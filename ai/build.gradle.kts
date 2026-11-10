import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Checklist AI: the optional app that runs the AI on the phone for the checklist app. It is
 * built, signed and released together with the checklist app, with the same version, so the
 * two are always in step.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName: String = providers.gradleProperty("appVersionName").get()

/** 1.2.3 becomes 1002003, the same as the checklist app. */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toInt() }
    require(parts.size == 3) { "appVersionName must look like 1.2.3, not $name" }
    return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
}

android {
    namespace = "io.github.randomtools3377.checklist.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.randomtools3377.checklist.ai"
        minSdk = 26
        targetSdk = 35
        versionName = appVersionName
        versionCode = versionCodeOf(appVersionName)
    }

    buildTypes {
        release {
            // Signed by CI in a separate job, like the checklist app.
            isMinifyEnabled = false
            // Phones only: the engine for 64-bit ARM, which keeps the download smaller.
            ndk { abiFilters += listOf("arm64-v8a") }
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

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        // The engine's native code is stored compressed, so the APK is smaller to download.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.litertlm)
    // Voice: Vosk turns speech into words on the phone (Apache 2.0), with JNA to reach it.
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
}
