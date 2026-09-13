// SPDX-License-Identifier: GPL-3.0-only
// Top-level build file; application dependencies belong in app/build.gradle.kts.

buildscript {
    val kotlinVersion = "2.3.20"
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
