# MobileWasm

An Android application **and** companion Progressive Web App (PWA) that run WebAssembly modules
in a sandboxed environment — on-device via [WasmEdge](https://wasmedge.org/) (Android) or
directly in the browser via the WebAssembly JavaScript API (PWA).

## Progressive Web App

The `pwa/` directory contains a self-contained PWA that mirrors the Android app's Wasm execution
model and shares the same run ABI:

```
run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) → outLen: i32
```

### Quick start

Serve the `pwa/` directory over HTTPS (required for service-worker and install prompt):

```bash
# With Python
cd pwa && python3 -m http.server 8080

# With Node.js (npx serve)
npx serve pwa
```

Open the URL in a modern browser. The app offers:

| Feature | Details |
|---|---|
| **Demo module** | Built-in echo module (same ABI as the Android `demo.zip`) — loads instantly, no file needed |
| **Upload .wasm** | Load any `.wasm` file from disk that exports `memory` and `run` |
| **Fetch from URL** | Download and compile a `.wasm` file from any URL |
| **Run module** | Send JSON input (≤ 65 536 bytes); see JSON output |
| **Offline support** | Service worker caches the app shell after first load |
| **Installable** | Meets PWA install criteria — add to home screen on mobile or desktop |

### PWA file layout

```
pwa/
├── index.html          # App shell
├── styles.css          # Dark-theme UI styles
├── app.js              # Wasm engine + UI logic
├── manifest.json       # Web App Manifest (icons, theme, display mode)
├── service-worker.js   # Cache-first offline strategy
├── icons/
│   ├── icon-192.png    # PWA icon (192 × 192)
│   └── icon-512.png    # PWA icon (512 × 512)
└── demo/
    └── echo.wat        # WAT source for the bundled echo module
```

---

An Android application that embeds the [WasmEdge](https://wasmedge.org/) runtime and provides
a full lifecycle for downloading, verifying, extracting, and executing Wasm packages.

Wasm modules run inside a hardware-enforced sandbox with no direct access to the OS,
filesystem, or network. All code reaches the device as a cryptographically verified ZIP
package, making it straightforward to deliver and update logic safely without shipping a
new APK.

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

## Using MobileWasm

### Running the demo

1. Install the APK:
   ```bash
   adb install app/build/outputs/apk/release/app-release.apk
   ```
2. Open **MobileWasm** on the device.
3. Tap **Load Demo Package** — the bundled `demo.zip` is extracted and the `echo` module is loaded.
4. Enter any JSON in the input field (max 65 536 bytes) and tap **Run Module**.
5. The module echoes the input back in the output area.

### Installing a package from a URL

Packages can be fetched at runtime without a new APK release. Supply the download URL and its
expected SHA-256 digest so the runtime can verify the download before extracting anything:

```kotlin
val store  = PackageStore.getInstance(context)
val result = store.getInstaller()
    .installFromUrl(
        url          = "https://example.com/my-package.zip",
        expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",   // 64-char hex digest
        packageName  = "my-package"
    )

result.onSuccess { ir ->
    Log.i("App", "Installed: ${ir.manifest.name} (${ir.manifest.modules.size} module(s))")
}
result.onFailure { err ->
    Log.e("App", "Install failed: ${err.message}")
}
```

### Loading and executing a module

After a package is installed, retrieve the Wasm bytes from the store and hand them to the
engine. All engine calls are `suspend` functions and must run on a coroutine:

```kotlin
val engine = WasmEngine.getInstance()

lifecycleScope.launch(Dispatchers.IO) {
    val bytes = store.getModuleBytes("my-package", "my-module")
        ?: error("module not found")

    engine.load("my-module", bytes).getOrThrow()       // hot-swaps any previous module

    val output = engine.run("""{"key":"value"}""").getOrThrow()
    withContext(Dispatchers.Main) { textView.text = output }
}
```

`engine.run` returns the JSON string produced by the module, or a `Result.failure` if no
module is loaded or the Wasm execution fails.

### Managing the package store

```kotlin
val store = PackageStore.getInstance(context)

// List all installed packages
val packages: List<String> = store.listPackages()

// Read a package's manifest
val manifest: WasmManifest? = store.getManifest("my-package")

// Remove a package
val removed: Boolean = store.removePackage("my-package")
```

### Creating a custom Wasm package

1. Write a Wasm module that exports `memory` and `run(i32,i32,i32,i32)->i32`.

2. Create `manifest.json`:
   ```json
   {
     "version": 1,
     "name": "my-package",
     "description": "My custom package",
     "modules": [
       { "name": "my-module", "file": "my_module.wasm", "description": "Does something useful" }
     ]
   }
   ```
   Constraints enforced by `ManifestValidator`:
   - `version` must be an integer ≥ 1. Only version `1` is currently defined; higher values pass validation but are not yet interpreted differently.
   - `name` must not be blank.
   - At least one module must be declared.
   - Every module `file` must end in `.wasm` and must not contain `..` or an absolute path.
   - Module names must be unique within the package.

3. Zip the files together and record the SHA-256:
   ```bash
   zip my-package.zip manifest.json my_module.wasm
   sha256sum my-package.zip
   ```

4. Host the ZIP at an HTTPS URL, then install it using `installFromUrl` as shown above.

### Run ABI

The engine calls the module's exported `run` function with this signature:

```
run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) → outLen: i32
```

| Parameter | Description |
|---|---|
| `inPtr` | Offset in Wasm linear memory where the JSON input starts (`0`) |
| `inLen` | Byte length of the JSON input (≤ 65 536) |
| `outPtr` | Offset in Wasm linear memory where the module should write its JSON output (`65536`) |
| `outCap` | Maximum bytes the module may write (`65536`) |
| return value | Actual byte length written to `outPtr`; negative or > `outCap` is treated as an error |

Both the input and output buffers live in the module's own linear memory — the host and module
share no pointers into host address space.

## Security

MobileWasm applies multiple independent layers of defence so that a malicious or buggy package
cannot harm the device or its data.

### WebAssembly sandbox

Every module executes inside the [WasmEdge](https://wasmedge.org/) runtime, which enforces
the WebAssembly security model:

- **Capability-free by default.** A Wasm module has no access to the filesystem, network,
  environment variables, or system calls unless the host explicitly imports those capabilities.
  MobileWasm does not grant any such imports, so modules are strictly isolated to computation
  over their own linear memory.
- **Memory isolation.** Each module instance has its own linear memory. It cannot read or write
  outside that region and has no visibility into host (JVM/Android) memory.
- **Structured control flow.** The WebAssembly spec forbids arbitrary jumps and stack smashing;
  branches must target declared labels, and indirect calls are validated through typed function
  tables. This eliminates whole classes of native-code exploits.
- **Validated before execution.** `WasmEdge_VMValidate` checks the module's type structure and
  instruction semantics before `WasmEdge_VMInstantiate` creates a live instance. A module that
  fails validation is rejected and never runs.

### Package integrity

Before a single byte of a downloaded package is extracted or executed, `PackageInstaller`
computes the SHA-256 digest of the raw download and compares it (case-insensitively) against
the caller-supplied expected digest:

```
SHA-256 mismatch → exception thrown, no extraction performed
```

This means a package can only run if it was produced by a party whose expected hash is known to
the application, preventing both tampering in transit and supply-chain substitution.

### ZIP-slip protection

ZIP archives can embed entries with paths like `../../etc/passwd` that, if extracted naively,
would write files outside the intended destination. MobileWasm guards against this at two
independent levels:

1. **Extraction level (`PackageInstaller.extractZipSafe`).** Every entry's canonical path is
   computed with `File.canonicalPath` and checked to start with the canonical destination
   directory before any bytes are written. Entries that fail the check raise a
   `SecurityException` and abort the entire extraction.
2. **Manifest level (`ManifestValidator`).** Module `file` fields are rejected if they contain
   `..` sequences or start with `/`, ensuring that even a hand-crafted manifest cannot redirect
   the engine to load a file from outside the package directory.

### Decompression bomb protection

ZIP entries are individually capped at **50 MiB**. An entry that exceeds this limit raises a
`SecurityException` and rolls back the installation, preventing a tiny ZIP from expanding into
an unbounded amount of disk or memory usage.

### Serialised execution

`WasmEngine` is guarded by a `kotlinx.coroutines.sync.Mutex`. Only one coroutine can call
`load` or `run` at a time. Hot-swapping (replacing the active module) rebuilds the entire VM
context atomically, so there is no window where a caller could observe a partially-loaded
module.

### Bounded host↔guest communication

The JSON I/O buffers are hard-coded to **65 536 bytes** each. Input larger than this limit is
rejected before any data is written into Wasm memory. The engine also validates the output
length returned by the module against the same cap, so a module cannot cause an out-of-bounds
read in the host.

### Threat model summary

| Threat | Mitigation |
|---|---|
| Tampered download | SHA-256 digest verification before extraction |
| Path-traversal in ZIP | Canonical-path check at extraction + manifest validator |
| Decompression bomb | 50 MiB per-entry cap |
| Module accessing device resources | WasmEdge sandbox; no capability imports |
| Module corrupting host memory | Wasm linear memory isolation |
| Malformed module crashing runtime | WasmEdge validate step before instantiation |
| Race condition / partial module swap | `Mutex`-serialised `load` and `run` |
| Oversized I/O buffer read | Hard 65 536-byte cap on input and output |

## CI / Releases

The [Build & Release APK](.github/workflows/release.yml) workflow:

- Runs on every push to `main` and every pull request — uploads the APK as a workflow artifact.
- On a version tag (`v*.*.*`) — creates a GitHub Release with the APK attached.

To publish a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## License

Apache 2.0
