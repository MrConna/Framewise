plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.framewise"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.framewise"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // Release notes — 0.1.2 (diagnostics):
        //  • CameraX binding failures are now caught and surfaced to the UI via
        //    CameraState.errorMessage, shown as a red error banner with Retry.
        //  • Permanently-denied camera permission now shows a Settings guidance
        //    message instead of looping the request prompt.
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // Compose compiler matching Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core / lifecycle / coroutines ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Material Components (provides the Material3 XML theme used by the window) ---
    implementation("com.google.android.material:material:1.12.0")

    // --- Jetpack Compose (BOM keeps all Compose artifacts in sync) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // --- Navigation Compose ---
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // --- CameraX (live preview + frame-by-frame analysis pipeline) ---
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // --- ML Kit (on-device subject + pose perception) ---
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // --- Runtime permissions for Compose ---
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // --- Coil (async image loading for the gallery) ---
    implementation("io.coil-kt:coil-compose:2.7.0")

    // --- OpenCV (optional: horizon / histogram CV) ---
    // Add the OpenCV Android SDK as a module, then uncomment:
    // implementation(project(":opencv"))

    // --- Test ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // --- Debug-only Compose tooling ---
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
