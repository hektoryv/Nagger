plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Set by CI so every build gets a higher version code than the last.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.example.nag"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nag"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // The signing key is NOT in this repo. CI writes it from a secret before building,
    // and Android Studio users drop their own copy at signing/nag.jks. Android only
    // allows an in-place update when the new APK carries the same signature as the
    // installed one, so the key has to be stable across builds.
    val keystoreFile = rootProject.file("signing/nag.jks")

    signingConfigs {
        create("shared") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "nagsigning"
                keyAlias = "nag"
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "nagsigning"
            }
        }
    }

    buildTypes {
        debug {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("shared")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}
