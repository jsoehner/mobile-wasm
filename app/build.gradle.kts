import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    jvmToolchain(21)
}

val wasmedgeVersion = "0.14.1"
val wasmedgeDir = layout.buildDirectory.dir("wasmedge").get().asFile
val wasmedgeSha256 = providers.gradleProperty("wasmedgeSha256").orNull



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
        val archive = layout.buildDirectory.file("wasmedge_android.tar.gz").get().asFile

        logger.lifecycle("Downloading WasmEdge $wasmedgeVersion for Android arm64…")
        archive.parentFile.mkdirs()

        val maxAttempts = 5
        var attempt = 1
        var lastError: Exception? = null
        while (attempt <= maxAttempts) {
            var conn: HttpURLConnection? = null
            try {
                archive.delete()
                conn = URI(archiveUrl).toURL().openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 120_000
                conn.setRequestProperty("User-Agent", "mobile-wasm-ci")

                // Follow up to 5 redirects manually (release assets often redirect).
                repeat(5) {
                    if (conn!!.responseCode in 300..399) {
                        val loc = conn!!.getHeaderField("Location")
                            ?: error("Redirect missing Location header")
                        conn!!.disconnect()
                        conn = URI(loc).toURL().openConnection() as HttpURLConnection
                        conn!!.instanceFollowRedirects = false
                        conn!!.connectTimeout = 15_000
                        conn!!.readTimeout = 120_000
                        conn!!.setRequestProperty("User-Agent", "mobile-wasm-ci")
                    }
                }

                val code = conn!!.responseCode
                check(code in 200..299) {
                    "WasmEdge download failed with HTTP $code from $archiveUrl"
                }

                conn!!.inputStream.use { input -> archive.outputStream().use { input.copyTo(it) } }
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                if (attempt == maxAttempts) break
                val delaySec = attempt * 5
                logger.warn(
                    "WasmEdge download attempt $attempt/$maxAttempts failed: ${e.message}. " +
                    "Retrying in ${delaySec}s…"
                )
                Thread.sleep(delaySec * 1000L)
            } finally {
                conn?.disconnect()
            }
            attempt += 1
        }

        if (lastError != null) {
            throw lastError
        }

        if (!wasmedgeSha256.isNullOrBlank()) {
            val actualSha256 = MessageDigest.getInstance("SHA-256")
                .digest(archive.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actualSha256.equals(wasmedgeSha256, ignoreCase = true)) {
                "WasmEdge archive SHA-256 mismatch. expected=$wasmedgeSha256 actual=$actualSha256"
            }
            logger.lifecycle("WasmEdge archive SHA-256 verified")
        } else {
            logger.warn("wasmedgeSha256 not set; skipping archive integrity verification")
        }

        logger.lifecycle("Extracting WasmEdge prebuilt to ${wasmedgeDir}…")
        wasmedgeDir.mkdirs()
        val exitValue = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", wasmedgeDir.absolutePath, "--strip-components=1")
            .inheritIO()
            .start()
            .waitFor()
        if (exitValue != 0) throw GradleException("Tar extraction failed with exit code $exitValue")

        markerFile.writeText(wasmedgeVersion)
        archive.delete()
    }
}

// Copy libwasmedge.so into the jniLibs source set so Android packages it
tasks.register<Copy>("copyWasmedgeLib") {
    dependsOn("downloadWasmedge")
    from("${wasmedgeDir}/lib/libwasmedge.so")
    into(layout.buildDirectory.dir("wasmedge_jniLibs/arm64-v8a").get().asFile)
}



android {
    namespace = "com.example.mobilewasm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mobilewasm"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
            version = "4.3.2"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(layout.buildDirectory.dir("wasmedge_jniLibs"))
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
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.3")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
