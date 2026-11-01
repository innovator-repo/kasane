plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.kasane"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.kasane"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.5.0"
        // The Lume core is built for these ABIs by tools/build-core.sh.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // A fixed debug key in the repo, so each CI build installs over the last one without losing data.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The release key every app shares (supplied by CI secrets, never committed), from KASANE_* variables.
        val keystorePath = System.getenv("KASANE_KEYSTORE")
        val keystorePassword = System.getenv("KASANE_KEYSTORE_PASSWORD")
        if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = System.getenv("KASANE_KEY_ALIAS") ?: "sigil"
                keyPassword = System.getenv("KASANE_KEY_PASSWORD") ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the release key (CI), fall back to the debug key so the APK still installs for testing.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // The same app and key as release, without R8 or resource shrinking: roughly twice as fast to build,
        // a larger file. It installs over the release build and keeps its data. For your own phone.
        create("personal") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
