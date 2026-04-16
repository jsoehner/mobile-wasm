package com.example.mobilewasm.manifest

/** Single Wasm module entry declared inside a [WasmManifest]. */
data class WasmModule(
    val name: String,
    val file: String,
    val description: String = ""
)

/**
 * Root manifest model parsed from `manifest.json` inside a WasmEdge ZIP package.
 *
 * Example JSON:
 * ```json
 * {
 *   "version": 1,
 *   "name": "my-package",
 *   "description": "Optional human-readable description",
 *   "modules": [
 *     { "name": "hello", "file": "hello.wasm", "description": "Echo module" }
 *   ]
 * }
 * ```
 */
data class WasmManifest(
    val version: Int,
    val name: String,
    val description: String = "",
    val modules: List<WasmModule>
)
