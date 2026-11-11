import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Windows app: the shared screens in a window, with the computer's side in
// shared/src/jvmMain (DesktopPhone). Packaged as an .msi with its own Java inside, so nothing
// else needs installing. Building it needs no Android SDK: only :core and the jvm part of
// :shared are compiled.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val appVersionName: String = providers.gradleProperty("appVersionName").get()

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main on a computer: the window's own thread.
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "io.github.randomtools3377.checklist.desktop.MainKt"
        jvmArgs += listOf(
            "-Dchecklist.version=$appVersionName",
            "-Dfile.encoding=UTF-8",
            // Dates and times as the computer's own region settings show them.
            "-Djava.locale.providers=HOST,CLDR",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Checklist"
            packageVersion = appVersionName
            description = "Goals, ticks, points and a shop of rewards, kept on this computer."
            vendor = "Checklist"
            // The parts of Java the app uses (see the suggestRuntimeModules task); nothing for networking is added.
            modules("java.desktop", "java.instrument", "java.logging", "jdk.accessibility", "jdk.localedata", "jdk.unsupported")

            windows {
                menu = true
                menuGroup = "Checklist"
                shortcut = true
                perUserInstall = true
                // Fixed for good: it is how Windows knows a new version replaces the old one.
                upgradeUuid = "ba65efb4-02cc-4c38-ad9f-a6771c1e2dab"
                iconFile.set(project.file("icons/checklist.ico"))
            }
        }
    }
}
