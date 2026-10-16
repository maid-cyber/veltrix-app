plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.veltrix.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.veltrix.vpn"
        minSdk = 24
        targetSdk = 35
        // broj builda sa GitHuba (svaki novi build ima veći broj, po njemu aplikacija zna da postoji novija verzija)
        val build = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.1.$build"
        buildConfigField("String", "UPDATE_REPO", "\"maid-cyber/veltrix-app\"")
        // adresa panela za probni nalog (dok nema naloga); mijenja se u GitHub varijabli PANEL_URL
        buildConfigField("String", "PANEL_URL", "\"${System.getenv("PANEL_URL")?.takeIf { it.isNotBlank() } ?: "http://178.104.237.74"}\"")
    }

    signingConfigs {
        // probni ključ (za prave kupce zamijeni ključem iz GitHub tajni)
        // pravi ključ: dolazi iz GitHub tajni (ANDROID_KEYSTORE_B64, ANDROID_KEYSTORE_PASSWORD)
        val ks = System.getenv("RELEASE_KEYSTORE")
        if (!ks.isNullOrBlank() && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "veltrix"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
        create("test") {
            storeFile = file("veltrix-test.jks")
            storePassword = "veltrix-test"
            keyAlias = "veltrix"
            keyPassword = "veltrix-test"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("test")
        }
        debug {
            signingConfig = signingConfigs.getByName("test")
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
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
