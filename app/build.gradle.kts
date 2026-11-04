plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.embertv"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.embertv"
        minSdk = 26
        targetSdk = 35
        // Each GitHub build gets a higher version so the TV always accepts it as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
        // Where built-in updates are fetched from (this repository's releases).
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "michaelgillespie88/ember-tv"
        buildConfigField("String", "UPDATE_REPO", "\"$repo\"")
    }

    // A fixed key so each new build installs over the previous one on the TV.
    // The signing key lives in GitHub's private secrets, never in the code. The build writes it to
    // release.p12 just before building (see .github/workflows/build.yml).
    signingConfigs {
        create("ember") {
            storeFile = file("release.p12")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "streammania"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            // R8 optimises the app and its libraries (Compose especially): smoother scrolling and
            // quicker screens, notably on Fire TV Sticks. Our own code is kept as written.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ember")
        }
        debug {
            signingConfig = signingConfigs.getByName("ember")
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
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Installs Compose's ready-made performance profiles on the device (sideloaded apps miss them otherwise).
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // QR codes for signing in from a phone
    implementation("com.google.zxing:core:3.5.3")
}
