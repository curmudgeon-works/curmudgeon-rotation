// SPDX-License-Identifier: GPL-3.0-only
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 36
    namespace = "app.curmudgeon.rotation"

    defaultConfig {
        applicationId = "app.curmudgeon.rotation"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // the app is English-only; drops ~40 unused library translations from the APK
    androidResources {
        localeFilters += listOf("en")
    }

    // Upload key lives OUTSIDE the repo: ~/.android-keys/curmudgeon-upload.properties with
    // storeFile/storePassword/keyAlias/keyPassword. Without it, release builds are simply unsigned.
    val uploadKeyProps = Properties().apply {
        val f = File(System.getProperty("user.home"), ".android-keys/curmudgeon-upload.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (uploadKeyProps.isNotEmpty()) {
            create("release") {
                storeFile = File(uploadKeyProps.getProperty("storeFile"))
                storePassword = uploadKeyProps.getProperty("storePassword")
                keyAlias = uploadKeyProps.getProperty("keyAlias")
                keyPassword = uploadKeyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadKeyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        target {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    // no Google-encrypted dependency metadata blob in the APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    testImplementation("junit:junit:4.13.2")
    // android.jar only has org.json stubs; the real implementation is needed on the JVM
    testImplementation("org.json:json:20250517")
}
