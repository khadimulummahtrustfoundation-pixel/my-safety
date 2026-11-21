plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
base {
    archivesName.set("NafsGuard-Lite")
}
android {
    namespace = "com.mysafety.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mysafety.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
