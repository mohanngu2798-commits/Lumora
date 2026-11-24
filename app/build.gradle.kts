plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    // Change this to a domain you own before the first Play Store upload. It can never change afterwards.
    namespace = "app.lumora.editor"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.lumora.editor"
        minSdk = 29          // Android 10+: scoped storage, no storage permission needed
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        // Phones and most emulators; keeps the AI runtime small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // The AI model is memory-mapped straight from the APK, so it must stay uncompressed.
    androidResources {
        noCompress += "tflite"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.tensorflow.lite)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
