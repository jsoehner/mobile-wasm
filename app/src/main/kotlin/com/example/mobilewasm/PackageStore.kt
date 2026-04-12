package com.example.mobilewasm

import android.content.Context
import com.example.mobilewasm.manifest.ManifestParser
import com.example.mobilewasm.manifest.ManifestValidator
import com.example.mobilewasm.manifest.WasmManifest
import java.io.File

/**
 * Application-wide singleton that owns the on-disk package store.
 *
 * Packages are kept under `<filesDir>/packages/<packageName>/`.
 * Use [getInstaller] to obtain a [PackageInstaller] bound to this store,
 * and [getModuleBytes] to retrieve compiled Wasm bytes ready for
 * [WasmEngine.load].
 */
class PackageStore private constructor(context: Context) {

    private val installDir = File(context.filesDir, "packages").also { it.mkdirs() }
    private val installer  = PackageInstaller(installDir)

    companion object {
        @Volatile private var instance: PackageStore? = null

        fun getInstance(context: Context): PackageStore =
            instance ?: synchronized(this) {
                instance ?: PackageStore(context.applicationContext).also { instance = it }
            }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Package management
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the shared [PackageInstaller] for this store. */
    fun getInstaller(): PackageInstaller = installer

    /** Returns the names of all installed packages. */
    fun listPackages(): List<String> =
        installDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    /** Returns the directory for [packageName], or `null` if not installed. */
    fun getPackageDir(packageName: String): File? =
        File(installDir, packageName).takeIf { it.isDirectory }

    /** Parses and returns the manifest of [packageName], or `null` on error. */
    fun getManifest(packageName: String): WasmManifest? {
        val file = File(installDir, "$packageName/manifest.json")
        if (!file.exists()) return null
        return runCatching { ManifestParser.parse(file.readText()) }.getOrNull()
    }

    /**
     * Returns the raw Wasm bytes for [moduleName] within [packageName],
     * after looking up its file path through the manifest.
     */
    fun getModuleBytes(packageName: String, moduleName: String): ByteArray? {
        val manifest = getManifest(packageName) ?: return null
        val module   = ManifestValidator.findModule(manifest, moduleName) ?: return null
        return File(installDir, "$packageName/${module.file}")
            .takeIf { it.exists() }
            ?.readBytes()
    }

    /** Deletes the package directory for [packageName]. Returns `true` on success. */
    fun removePackage(packageName: String): Boolean =
        File(installDir, packageName).takeIf { it.exists() }?.deleteRecursively() ?: false
}
