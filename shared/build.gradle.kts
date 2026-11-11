import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What Android and iPhone share beyond the rules in :core: the database and the code around it,
// and the screens (Compose Multiplatform). What only a phone can do goes through Phone, which
// each phone fills in. The jvm target is only for building and testing it on any machine.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.room.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            api(libs.cmp.icons.core)
            implementation(libs.cmp.resources)
            api(libs.jb.lifecycle.viewmodel.compose)
            api(libs.jb.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.room.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment.ktx)
            implementation(libs.androidx.core.ktx)
        }
        // The iPhone and the desktop checks use SQLite that comes with the app; Android keeps the
        // phone's own, as it always has.
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.github.randomtools3377.checklist.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The database layout of every version is kept in shared/schemas, so later versions can move the
// owner's data forward instead of losing it. CI fails if it is not committed.
room {
    schemaDirectory("$projectDir/schemas")
}

// The fonts, sounds, GIFs and emoji list that come with the app, for every phone.
compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.randomtools3377.checklist.ui.resources"
    generateResClass = always
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
