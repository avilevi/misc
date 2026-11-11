plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hcexport"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.hcexport"
        minSdk = 26
        targetSdk = 36
        val buildNum = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        val major = System.getenv("VERSION_MAJOR") ?: "1"
        versionCode = buildNum
        versionName = "$major.$buildNum"
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
    }

    signingConfigs {
        create("release") {
            storeFile     = rootProject.file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias      = System.getenv("KEY_ALIAS") ?: ""
            keyPassword   = System.getenv("KEYSTORE_PASSWORD") ?: ""
        }
    }
    buildTypes {
        debug {
            // Keeps a locally-built debug app from colliding with the release app.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig  = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // The app has no fragments; the check only sees an old transitive version
        // pulled in by an AndroidX library. ActivityResult is used from Activities.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
}
