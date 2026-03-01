import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.pixelwatchapptest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pixelwatchapptest"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        // Load local.properties to safely inject API Key
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val apiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"${apiKey}\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    
    // Compose BOM to prevent AGP alignment mutation issues
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    // Wear Input for RemoteInput
    implementation("androidx.wear:wear-input:1.1.0")
    // Wear Phone Interactions for launching intents on the phone 
    implementation("androidx.wear:wear-phone-interactions:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Google API Client for Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.6.0")
    // Kotlin Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
