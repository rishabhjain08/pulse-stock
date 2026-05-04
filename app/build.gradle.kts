import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
}

// Load API key: local.properties for dev, env var for CI
val localProps = Properties().also { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) props.load(file.inputStream())
}
val finnhubApiKey: String = localProps.getProperty("FINNHUB_API_KEY")
    ?: System.getenv("FINNHUB_API_KEY")
    ?: ""
val poarvaultApiUrl: String = localProps.getProperty("POARVAULT_API_URL")
    ?: System.getenv("POARVAULT_API_URL")
    ?: ""
val poarvaultApiKey: String = localProps.getProperty("POARVAULT_API_KEY")
    ?: System.getenv("POARVAULT_API_KEY")
    ?: ""
val splitwiseConsumerKey: String = localProps.getProperty("SPLITWISE_CONSUMER_KEY")
    ?: System.getenv("SPLITWISE_CONSUMER_KEY")
    ?: ""
// SPLITWISE_CONSUMER_SECRET is intentionally not read here.
// It is only used by the Lambda for the token exchange and must never be baked into the APK.

// Android 16 (API 36) made Configuration.fontWeightAdjustment private.
// Compose 1.11.0 accesses it as a direct field → NoSuchFieldError crash on every
// Android 16 device. Force the whole androidx.compose.ui group to 1.12.0-alpha01
// which uses the getFontWeightAdjustment() getter instead.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.compose.ui") {
            useVersion("1.12.0-alpha01")
        }
    }
}

android {
    namespace = "com.pulsestock.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pulsestock.app"
        minSdk = 36
        targetSdk = 36
        // CI passes VERSION_CODE via env var (set to github.run_number).
        // Falls back to 1 for local builds.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 1
        versionName = "1.0"

        buildConfigField("String", "FINNHUB_API_KEY", "\"$finnhubApiKey\"")
        buildConfigField("String", "POARVAULT_API_URL", "\"$poarvaultApiUrl\"")
        buildConfigField("String", "POARVAULT_API_KEY", "\"$poarvaultApiKey\"")
        buildConfigField("String", "SPLITWISE_CONSUMER_KEY", "\"$splitwiseConsumerKey\"")
        // Verbose logcat logging — grep PulseLog to find and remove before production release
        buildConfigField("Boolean", "VERBOSE_LOGGING", "false")
    }

    signingConfigs {
        create("release") {
            // In CI: decoded from KEYSTORE_BASE64 secret into keystore/pulsestock.keystore
            // Locally: place the keystore at that path and set env vars
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../keystore/pulsestock.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "pulsestock"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
        debug {
            isDebuggable = true
            buildConfigField("Boolean", "VERBOSE_LOGGING", "true")
        }
    }

    // ── Distribution flavors ─────────────────────────────────────────────────
    // "internal" — Firebase Crashlytics enabled (crash logs sent to Firebase console)
    // "production" — zero Firebase code; no data collected
    flavorDimensions += "distribution"
    productFlavors {
        create("internal") {
            dimension = "distribution"
        }
        create("production") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation("sh.calvin.reorderable:reorderable-android:2.5.1")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher)
    implementation(libs.plaid.link)
    debugImplementation(libs.androidx.ui.tooling)

    // Force profileinstaller 1.4.1 — 1.3.x crashes on Android 16 with NoSuchMethodError
    // on PackageManager.PackageInfoFlags.of(long). Pulled transitively by activity-compose.
    implementation(libs.profileinstaller)
    implementation(libs.androidx.browser)

    // Firebase Crashlytics — internal flavor only; production builds have zero Firebase code
    "internalImplementation"(platform(libs.firebase.bom))
    "internalImplementation"(libs.firebase.crashlytics)
}
