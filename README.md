# MobileWasm

An Android application that embeds the [WasmEdge](https://wasmedge.org/) runtime and provides
a full lifecycle for downloading, verifying, extracting, and executing Wasm packages.

## Features

| Feature | Details |
|---|---|
| **Package installer** | Downloads a ZIP over HTTP(S), verifies SHA-256, and extracts it with ZIP-slip protection |
| **Manifest parser/validator** | Parses `manifest.json`; supports multiple independent Wasm modules per package |
| **WasmEngine singleton** | Global native engine with `Mutex`-guarded `load` / `run`; hot-swaps the active module |
| **Run ABI** | `run(inPtr i32, inLen i32, outPtr i32, outCap i32) → outLen i32` with JSON in/out (≤ 65 536 bytes) |
| **Demo asset** | `assets/sample/demo.zip` — a prebuilt echo module ready to load from the UI |
| **Release binary** | GitHub Actions builds a signed APK on every push to `main` and publishes it as a GitHub Release on version tags |

## Architecture

```
app/
├── src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt          # Links against WasmEdge prebuilt (arm64-v8a)
│   │   └── wasmedge_jni.cpp        # JNI bridge: nativeInit / nativeLoad / nativeRun / nativeClose
│   ├── kotlin/com/example/mobilewasm/
│   │   ├── manifest/
│   │   │   ├── Manifest.kt         # WasmManifest + WasmModule data classes
│   │   │   ├── ManifestParser.kt   # JSON -> WasmManifest
│   │   │   └── ManifestValidator.kt# Semantic validation + module lookup
│   │   ├── PackageInstaller.kt     # Download, SHA-256 verify, ZIP-slip-safe extract
│   │   ├── PackageStore.kt         # Singleton on-disk package registry
│   │   ├── WasmEngine.kt           # Singleton native engine (load / run / hot-swap)
│   │   └── MainActivity.kt         # Demo Activity
│   └── assets/sample/demo.zip      # Bundled demo package (echo.wasm)
```

## Building

### Prerequisites

- Android Studio Hedgehog or newer (or Android SDK + NDK via command line)
- JDK 17
- Internet access during the first build (WasmEdge prebuilt is downloaded automatically)

### Command line

```bash
# Debug build
./gradlew assembleDebug

# Release build (uses debug keystore by default; see below for custom signing)
./gradlew assembleRelease
```

The APK is produced at `app/build/outputs/apk/release/app-release.apk`.

### Custom signing (optional)

Set these environment variables before running `assembleRelease`:

```bash
export KEYSTORE_PATH=/path/to/release.jks
export KEYSTORE_PASS=<store password>
export KEY_ALIAS=<key alias>
export KEY_PASS=<key password>
./gradlew assembleRelease
```

## Running the demo

1. Install the APK: `adb install app/build/outputs/apk/release/app-release.apk`
2. Open **MobileWasm** on the device.
3. Tap **Load Demo Package** -- the bundled `demo.zip` is extracted, the `echo` module is loaded.
4. Enter any JSON in the input field (max 65 536 bytes) and tap **Run Module**.
5. The module echoes the input back.

## Creating a custom Wasm package

1. Write a Wasm module that exports `memory` and `run(i32,i32,i32,i32)->i32`.
2. Create `manifest.json`:

```json
{
  "version": 1,
  "name": "my-package",
  "description": "My custom package",
  "modules": [
    { "name": "my-module", "file": "my_module.wasm" }
  ]
}
```

3. Zip together and record the SHA-256:

```bash
zip my-package.zip manifest.json my_module.wasm
sha256sum my-package.zip
```

4. Install from a URL in Kotlin:

```kotlin
val result = PackageStore.getInstance(context)
    .getInstaller()
    .installFromUrl("https://example.com/my-package.zip", "<sha256>", "my-package")

result.onSuccess { ir ->
    val bytes = store.getModuleBytes("my-package", "my-module")!!
    lifecycleScope.launch {
        engine.load("my-module", bytes)
        val output = engine.run("""{"key":"value"}""").getOrThrow()
    }
}
```

## CI / Releases

The [Build & Release APK](.github/workflows/release.yml) workflow:

- Runs on every push to `main` and every pull request -- uploads the APK as a workflow artifact.
- On a version tag (`v*.*.*`) -- creates a GitHub Release with the APK attached.

To publish a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## License

Apache 2.0
