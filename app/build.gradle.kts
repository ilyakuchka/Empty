plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.ilyakuchka.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.ilyakuchka.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 112
        versionName = "1.1.2-test"

        buildConfigField("String", "API_URL", "\"https://api.senkuro.me/graphql\"")
        buildConfigField("String", "WS_URL", "\"wss://api.senkuro.me/graphql\"")
        buildConfigField("String", "SITE_URL", "\"https://senkuro.me\"")
    }

    // Постоянная публичная подпись: один и тот же ключ лежит в репозитории,
    // поэтому любая сборка ставится поверх предыдущей.
    signingConfigs {
        create("publicKey") {
            storeFile = rootProject.file("keystore/empty-public.jks")
            storePassword = "emptypublic"
            keyAlias = "empty"
            keyPassword = "emptypublic"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("publicKey")
        }
        debug {
            signingConfig = signingConfigs.getByName("publicKey")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
