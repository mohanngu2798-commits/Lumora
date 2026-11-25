plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    // The code namespace stays as it is; the Play Store identity is applicationId below.
    namespace = "app.lumora.editor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vannam.photo"   // Play Store ID, can never change after the first upload
        minSdk = 29          // Android 10+: scoped storage, no storage permission needed
        targetSdk = 36
        versionCode = 9
        versionName = "0.8.0"
        // Phones and most emulators; keeps the AI runtime small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Release signing comes from CI secrets; nothing secret lives in the repo.
    val ks = System.getenv("VANNAM_KEYSTORE_FILE")
    signingConfigs {
        if (ks != null && file(ks).exists()) create("upload") {
            storeFile = file(ks)
            storePassword = System.getenv("VANNAM_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("VANNAM_KEY_ALIAS")
            keyPassword = System.getenv("VANNAM_KEY_PASSWORD")
        }
    }
    // The 174 MB face model ships to Play as an install-time asset pack so the base app stays under Play's size limit.
    assetPacks += listOf(":ai_models")
    // Debug APKs (installed directly, not through Play) carry the model inside the app instead.
    sourceSets["debug"].assets.srcDir(rootProject.file("ai_models/src/main/assets"))

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
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

// The face restoration model is larger than GitHub's 100 MB file limit, so it is stored in parts under
// /models and joined into the app's assets before each build.
val joinModels by tasks.registering {
    val parts = rootProject.fileTree("models") { include("gfpgan_v1_4.tflite.part*") }
    val out = rootProject.file("ai_models/src/main/assets/gfpgan_v1_4.tflite")
    inputs.files(parts)
    outputs.file(out)
    doLast {
        val sorted = parts.files.sortedBy { it.name }
        if (sorted.isEmpty()) return@doLast
        out.outputStream().use { o -> sorted.forEach { p -> p.inputStream().use { it.copyTo(o) } } }
    }
}
tasks.named("preBuild") { dependsOn(joinModels) }
project(":ai_models").tasks.matching { it.name != "joinModels" }.configureEach { dependsOn(joinModels) }
