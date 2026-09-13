# Building Curmudgeon Rotation

## Requirements

- JDK 17 (`java -version` must report 17 or newer)
- Android SDK with **platform android-36** and **build-tools 36.0.0**
- Internet access on the first build (Gradle 9.4.1 wrapper, AGP 9.2.1, Kotlin 2.3.20 and the
  AndroidX libraries are downloaded from Maven Central / Google Maven)

## Setup

```sh
git clone <this repository> curmudgeon-rotation
cd curmudgeon-rotation
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or export ANDROID_HOME
```

## Build and test

```sh
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk` (package `app.curmudgeon.rotation.debug`, debug-signed)
- `app/build/outputs/apk/release/app-release.apk` (package `app.curmudgeon.rotation`, R8-minified),
  or `app-release-unsigned.apk` when no signing key is configured
- Unit test reports: `app/build/reports/tests/testDebugUnitTest/index.html`

Lint: `./gradlew lintDebug` (report in `app/build/reports/`).

## Release signing (optional)

Release builds are signed only if `~/.android-keys/curmudgeon-upload.properties` exists. It is
read by `app/build.gradle.kts` and must never be committed:

```properties
storeFile=/absolute/path/to/curmudgeon-upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the build still succeeds and produces an unsigned release APK.

Verify a signed APK:

```sh
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```
