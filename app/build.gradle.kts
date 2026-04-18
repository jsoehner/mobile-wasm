import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val wasmedgeVersion = "0.14.1"
val wasmedgeDir = layout.buildDirectory.dir("wasmedge").get().asFile
val wasmedgeSha256 = providers.gradleProperty("wasmedgeSha256").orNull

private val WASM_PAGE_SIZE = 65536

private fun readVarUInt32(bytes: ByteArray, start: Int): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var index = start
    while (true) {
        if (index >= bytes.size) error("Unexpected EOF while reading LEB128")
        val b = bytes[index].toInt() and 0xFF
        index++
        result = result or ((b and 0x7F) shl shift)
        if ((b and 0x80) == 0) return result to index
        shift += 7
        if (shift > 35) error("Invalid LEB128 sequence")
    }
}

private fun skipInitExpr(bytes: ByteArray, start: Int): Int {
    if (start >= bytes.size || (bytes[start].toInt() and 0xFF) != 0x41) {
        error("Only i32.const init expressions are supported in demo validator")
    }
    val (_, afterConst) = readVarUInt32(bytes, start + 1)
    if (afterConst >= bytes.size || (bytes[afterConst].toInt() and 0xFF) != 0x0B) {
        error("Init expression must terminate with end opcode")
    }
    return afterConst + 1
}

private fun validateDemoWasm(wasm: ByteArray) {
    if (wasm.size < 8) error("Wasm too small")
    val magic = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
    val version = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    if (!wasm.copyOfRange(0, 4).contentEquals(magic)) error("Invalid wasm magic")
    if (!wasm.copyOfRange(4, 8).contentEquals(version)) error("Unsupported wasm version")

    var offset = 8
    var memoryMinPages: Int? = null
    var maxDataEnd = 0

    while (offset < wasm.size) {
        val sectionId = wasm[offset].toInt() and 0xFF
        offset += 1
        val (sectionSize, sectionPayloadStart) = readVarUInt32(wasm, offset)
        val sectionPayloadEnd = sectionPayloadStart + sectionSize
        if (sectionPayloadEnd > wasm.size) error("Section exceeds wasm size")

        when (sectionId) {
            5 -> {
                var i = sectionPayloadStart
                val (count, afterCount) = readVarUInt32(wasm, i)
                i = afterCount
                if (count > 0) {
                    val (flags, afterFlags) = readVarUInt32(wasm, i)
                    i = afterFlags
                    val (minPages, afterMin) = readVarUInt32(wasm, i)
                    i = afterMin
                    if ((flags and 0x1) != 0) {
                        readVarUInt32(wasm, i)
                    }
                    memoryMinPages = minPages
                }
            }

            11 -> {
                var i = sectionPayloadStart
                val (count, afterCount) = readVarUInt32(wasm, i)
                i = afterCount
                repeat(count) {
                    val (kind, afterKind) = readVarUInt32(wasm, i)
                    i = afterKind
                    val dataOffset = when (kind) {
                        0 -> {
                            if ((wasm[i].toInt() and 0xFF) != 0x41) error("Unsupported active data expr")
                            val (off, afterOff) = readVarUInt32(wasm, i + 1)
                            if ((wasm[afterOff].toInt() and 0xFF) != 0x0B) error("Data expr missing end opcode")
                            i = afterOff + 1
                            off
                        }

                        1 -> 0

                        2 -> {
                            val (_, afterMemIdx) = readVarUInt32(wasm, i)
                            i = afterMemIdx
                            if ((wasm[i].toInt() and 0xFF) != 0x41) error("Unsupported active data expr")
                            val (off, afterOff) = readVarUInt32(wasm, i + 1)
                            if ((wasm[afterOff].toInt() and 0xFF) != 0x0B) error("Data expr missing end opcode")
                            i = afterOff + 1
                            off
                        }

                        else -> error("Unsupported data segment kind: $kind")
                    }

                    val (size, afterSize) = readVarUInt32(wasm, i)
                    i = afterSize
                    if (i + size > sectionPayloadEnd) error("Data segment exceeds section payload")
                    val end = dataOffset + size
                    if (end > maxDataEnd) maxDataEnd = end
                    i += size
                }
            }
        }

        offset = sectionPayloadEnd
    }

    val minPages = memoryMinPages ?: error("Demo wasm must declare a memory section")
    val memoryBytes = minPages * WASM_PAGE_SIZE
    if (maxDataEnd > memoryBytes) {
        error("Data segment out of bounds: end=$maxDataEnd memory=$memoryBytes")
    }
}

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
    into(layout.buildDirectory.dir("wasmedge_jniLibs/arm64-v8a").get().asFile)
}

tasks.register("validateDemoPackage") {
    val demoZip = file("src/main/assets/sample/demo.zip")
    inputs.file(demoZip)

    doLast {
        if (!demoZip.exists()) error("Missing demo package: ${demoZip.path}")
        ZipFile(demoZip).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
                ?: error("demo.zip missing manifest.json")
            val echoEntry = zip.getEntry("echo.wasm")
                ?: error("demo.zip missing echo.wasm")

            val manifest = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            if (!manifest.contains("\"modules\"")) {
                error("demo manifest does not declare modules")
            }

            val wasm = zip.getInputStream(echoEntry).use { it.readBytes() }
            validateDemoWasm(wasm)
            logger.lifecycle("Demo package validated: ${demoZip.name} (${wasm.size} bytes wasm)")
        }
    }
}

android {
    namespace = "com.example.mobilewasm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mobilewasm"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
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
            version = "3.31.5"
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

tasks.named("preBuild").configure {
    dependsOn("validateDemoPackage")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
