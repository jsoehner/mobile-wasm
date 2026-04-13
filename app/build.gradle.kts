import java.net.HttpURLConnection
import java.net.URL as JavaURL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val wasmedgeVersion = "0.14.1"
val wasmedgeDir = file("${buildDir}/wasmedge")

// ──────────────────────────────────────────────────────────────
// Task: download WasmEdge Android arm64 prebuilt before CMake
// ──────────────────────────────────────────────────────────────
tasks.register("downloadWasmedge") {
    val markerFile = file("${wasmedgeDir}/.downloaded_${wasmedgeVersion}")
    outputs.file(markerFile)

    doLast {
        if (markerFile.exists()) return@doLast

        val archiveUrl =
            "https://github.com/WasmEdge/WasmEdge/releases/download/$wasmedgeVersion/" +
            "WasmEdge-$wasmedgeVersion-android_aarch64.tar.gz"
        val archive = file("${buildDir}/wasmedge_android.tar.gz")

        logger.lifecycle("Downloading WasmEdge $wasmedgeVersion for Android arm64…")
        archive.parentFile.mkdirs()

        var conn = JavaURL(archiveUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        // Follow up to 5 redirects manually (some releases redirect)
        repeat(5) {
            if (conn.responseCode in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                conn = JavaURL(loc).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
            }
        }
        conn.inputStream.use { input -> archive.outputStream().use { input.copyTo(it) } }

        logger.lifecycle("Extracting WasmEdge prebuilt to ${wasmedgeDir}…")
        wasmedgeDir.mkdirs()
        exec {
            commandLine(
                "tar", "xzf", archive.absolutePath,
                "-C", wasmedgeDir.absolutePath,
                "--strip-components=1"
            )
        }

        markerFile.writeText(wasmedgeVersion)
        archive.delete()
    }
}

// Copy libwasmedge.so into the jniLibs source set so Android packages it
tasks.register<Copy>("copyWasmedgeLib") {
    dependsOn("downloadWasmedge")
    from("${wasmedgeDir}/lib/libwasmedge.so")
    into("${buildDir}/wasmedge_jniLibs/arm64-v8a")
}

android {
    namespace = "com.example.mobilewasm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mobilewasm"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DWASMEDGE_PREBUILT_DIR=${wasmedgeDir}"
                )
                cppFlags("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    signingConfigs {
        create("release") {
            // CI: override via env vars KEYSTORE_PATH / KEYSTORE_PASS / KEY_ALIAS / KEY_PASS
            // Fallback: debug keystore produces a self-signed but installable APK
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASS")
            } else {
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
        }
        debug {
            isDebuggable = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.5"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("${buildDir}/wasmedge_jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
    }
}

// Ensure WasmEdge is downloaded before any CMake or JNI-lib merge task runs
tasks.matching {
    it.name.startsWith("configureCMake") ||
    it.name.startsWith("buildCMake") ||
    it.name.startsWith("externalNativeBuild")
}.configureEach { dependsOn("downloadWasmedge") }

tasks.matching {
    it.name.startsWith("merge") && it.name.contains("JniLib", ignoreCase = true)
}.configureEach { dependsOn("copyWasmedgeLib") }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
