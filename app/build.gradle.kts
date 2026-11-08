plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The search server address is set at build time, never written into the source.
// Set it with the searchApiBase Gradle property (-PsearchApiBase=https://...) or the
// SEARCH_API_BASE environment variable. The default is a placeholder that shows a setup message.
val searchApiBase: String = (project.findProperty("searchApiBase") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("SEARCH_API_BASE")?.takeIf { it.isNotBlank() }
    ?: "https://search.example.com"

android {
    namespace = "com.hmedia.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hmedia.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SEARCH_API_BASE", "\"$searchApiBase\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // libVLC is the engine under the Void layer (latest on Maven Central as of this build).
    implementation("org.videolan.android:libvlc-all:3.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.webkit:webkit:1.14.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Unit tests run on the JVM. org.json is supplied here because the Android stub is not available to them.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Device and emulator tests (connectedDebugAndroidTest). Run them with the device-test workflow.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
