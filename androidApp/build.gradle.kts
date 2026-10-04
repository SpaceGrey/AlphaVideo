plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.alphavideo.demo"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.alphavideo.demo"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":demo"))
    implementation("androidx.activity:activity-compose:1.12.0")
}
