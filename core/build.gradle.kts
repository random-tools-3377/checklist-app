import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The rules for points, days and the shop, in plain Kotlin so they are tested without a phone.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
