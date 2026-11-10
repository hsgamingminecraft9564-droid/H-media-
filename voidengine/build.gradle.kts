import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Versions checked against the public release pages in November 2026. Bump them if your project already
// uses newer ones (Gradle takes the highest version anyway).
val media3Version = "1.10.1"
val libVlcVersion = "3.7.6"          // libVLC 3.x API. Do NOT use 4.0.0-eapXX here: the API differs.
val coroutinesVersion = "1.10.2"

plugins {
    id("com.android.library")
    // If your project uses AGP 9+ with built-in Kotlin, delete the next line.
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hmedia.voidengine"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Media3 marks most of its API @UnstableApi; we opt in at compile time below.
        disable += "UnsafeOptInUsageError"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    api("androidx.media3:media3-datasource:$media3Version")
    implementation("org.videolan.android:libvlc-all:$libVlcVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("com.google.guava:guava:33.4.8-android")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}
