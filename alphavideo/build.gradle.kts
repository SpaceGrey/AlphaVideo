import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "dev.alphavideo"
        compileSdk = 36
        minSdk = 29
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
        }
        iosMain.dependencies {
            implementation("org.jetbrains.skiko:skiko:0.144.6")
        }
    }
}

val buildAndroidDecoder by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("bash", "scripts/build-android-decoder.sh")
    inputs.dir("src/androidMain/cpp")
    inputs.file(rootProject.file("scripts/build-android-decoder.sh"))
    outputs.dir("src/androidMain/jniLibs")
}
tasks.configureEach {
    if (name.contains("JniLib", ignoreCase = true)) dependsOn(buildAndroidDecoder)
}
