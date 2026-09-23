plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mine.mglegacy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mine.mglegacy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "alpha 0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.13.1")

    // Miuix - MIUI/HyperOS-styled theme for Compose
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
}
