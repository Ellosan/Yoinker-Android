import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

/**
 * Release signing, if this machine has a key.
 *
 * Read from keystore.properties beside the project, or from the environment so CI
 * can pass secrets without a file. Neither is committed. Without one the release
 * build still runs — it just comes out unsigned, which is fine for checking that
 * R8 hasn't broken anything and useless for anything else.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val keystorePath = secret("storeFile", "YOINKER_KEYSTORE")
val hasSigningKey = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "com.pylo.yoinker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pylo.yoinker"
        // The yt-dlp engine (python + ffmpeg native payloads) needs API 24+.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = secret("storePassword", "YOINKER_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "YOINKER_KEY_ALIAS")
                keyPassword = secret("keyPassword", "YOINKER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // One APK per ABI: the bundled engine carries a full python + ffmpeg for each
    // architecture, so a universal build is roughly four times the size it needs to be.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            // The universal APK is four engines in one file and triples build time.
            // CI passes -Pyoinker.universalApk=false to skip it.
            isUniversalApk = providers.gradleProperty("yoinker.universalApk")
                .map { it.toBoolean() }
                .getOrElse(true)
        }
    }

    packaging {
        // The engine unpacks its binaries from jniLibs at runtime, so they must be
        // stored uncompressed and extracted on install rather than loaded in place.
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    // yt-dlp + ffmpeg, running on-device. Same engine the desktop build shells out to.
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.youtubedl.aria2c)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
}
