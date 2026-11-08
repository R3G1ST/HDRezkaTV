import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String): String = localProps.getProperty(key, "")

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.hdrezka.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hdrezka.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 58
        versionName = "0.43.4"
    }

    // Личная сборка для сайдлоада: release оптимизирован R8
    // и подписан локальным debug-ключом.
    signingConfigs {
        create("localRelease") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("user") {
            dimension = "edition"
            resValue("string", "app_name", "HDrezka TV")
            buildConfigField("boolean", "EMBEDDED_PERSONAL", "false")
            buildConfigField("String", "EMBEDDED_GITHUB_OWNER", quoted(""))
            buildConfigField("String", "EMBEDDED_GITHUB_REPO", quoted(""))
            buildConfigField("String", "EMBEDDED_GITHUB_TOKEN", quoted(""))
            buildConfigField("String", "EMBEDDED_COOKIES_B64", quoted(""))
            buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", quoted("Ov23linCOGowYZuzx9Vh"))
        }
        create("personal") {
            dimension = "edition"
            resValue("string", "app_name", "HDrezka TV")
            buildConfigField("boolean", "EMBEDDED_PERSONAL", "true")
            buildConfigField("String", "EMBEDDED_GITHUB_OWNER", quoted(secret("GITHUB_OWNER")))
            buildConfigField("String", "EMBEDDED_GITHUB_REPO", quoted(secret("GITHUB_REPO")))
            buildConfigField("String", "EMBEDDED_GITHUB_TOKEN", quoted(secret("GITHUB_TOKEN")))
            buildConfigField("String", "EMBEDDED_COOKIES_B64", quoted(secret("HDREZKA_COOKIES_B64")))
            buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", quoted("Ov23linCOGowYZuzx9Vh"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("localRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.paging:paging-runtime-ktx:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
