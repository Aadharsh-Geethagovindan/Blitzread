plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.blitzread"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.blitzread"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.readium.kotlin-toolkit:readium-shared:3.1.2")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.1.2")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.1.2")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

}