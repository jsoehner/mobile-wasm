package com.example.mobilewasm.manifest

/** Validates a parsed [WasmManifest] and provides lookup helpers. */
object ManifestValidator {

    /**
     * Returns [Result.success] when the manifest is well-formed, or
     * [Result.failure] with a descriptive [IllegalArgumentException] otherwise.
     */
    fun validate(manifest: WasmManifest): Result<Unit> {
        if (manifest.version < 1) {
            return Result.failure(
                IllegalArgumentException("Manifest version must be >= 1, got ${manifest.version}")
            )
        }
        if (manifest.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Manifest 'name' must not be blank"))
        }
        if (manifest.modules.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Manifest must declare at least one module")
            )
        }

        val seenNames = mutableSetOf<String>()
        for (module in manifest.modules) {
            if (module.name.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Every module must have a non-blank 'name'")
                )
            }
            if (module.file.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Module '${module.name}' has a blank 'file' path")
                )
            }
            if (!module.file.endsWith(".wasm")) {
                return Result.failure(
                    IllegalArgumentException(
                        "Module '${module.name}' file '${module.file}' must end with .wasm"
                    )
                )
            }
            // Reject path-traversal sequences (ZIP-slip guard at the manifest level)
            if (module.file.contains("..") || module.file.startsWith("/")) {
                return Result.failure(
                    IllegalArgumentException(
                        "Module '${module.name}' file '${module.file}' contains an unsafe path"
                    )
                )
            }
            if (!seenNames.add(module.name)) {
                return Result.failure(
                    IllegalArgumentException("Duplicate module name: '${module.name}'")
                )
            }
        }

        return Result.success(Unit)
    }

    /** Returns the first module whose name matches [name], or `null`. */
    fun findModule(manifest: WasmManifest, name: String): WasmModule? =
        manifest.modules.find { it.name == name }
}
