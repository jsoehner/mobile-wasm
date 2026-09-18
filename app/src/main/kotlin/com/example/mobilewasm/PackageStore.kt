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

    private fun isValidPackageName(packageName: String): Boolean =
        packageName.matches(PackageInstaller.PACKAGE_NAME_REGEX)

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
        installDir.listFiles()?.filter { it.isDirectory && isValidPackageName(it.name) }?.map { it.name } ?: emptyList()

    /** Returns the directory for [packageName], or `null` if not installed. */
    fun getPackageDir(packageName: String): File? {
        if (!isValidPackageName(packageName)) return null
        val dir = File(installDir, packageName)
        val canonicalInstallDir = installDir.canonicalPath + File.separator
        if (!dir.canonicalPath.startsWith(canonicalInstallDir) && dir.canonicalPath != installDir.canonicalPath) return null
        return dir.takeIf { it.isDirectory }
    }

    /** Parses and returns the manifest of [packageName], or `null` on error. */
    fun getManifest(packageName: String): WasmManifest? {
        if (!isValidPackageName(packageName)) return null
        val dir = getPackageDir(packageName) ?: return null
        val file = File(dir, "manifest.json")
        if (!file.exists()) return null
        return runCatching { ManifestParser.parse(file.readText()) }.getOrNull()
    }

    /**
     * Returns the raw Wasm bytes for [moduleName] within [packageName],
     * after looking up its file path through the manifest.
     */
    fun getModuleBytes(packageName: String, moduleName: String): ByteArray? {
        if (!isValidPackageName(packageName)) return null
        val packageDir = getPackageDir(packageName) ?: return null
        val manifest = getManifest(packageName) ?: return null
        val module   = ManifestValidator.findModule(manifest, moduleName) ?: return null
        val moduleFile = File(packageDir, module.file)
        val canonicalPackageDir = packageDir.canonicalPath + File.separator
        if (!moduleFile.canonicalPath.startsWith(canonicalPackageDir)) {
            return null
        }
        return moduleFile.takeIf { it.exists() }?.readBytes()
    }

    /** Deletes the package directory for [packageName]. Returns `true` on success. */
    fun removePackage(packageName: String): Boolean {
        if (!isValidPackageName(packageName)) return false
        val packageDir = getPackageDir(packageName) ?: return false
        return packageDir.deleteRecursively()
    }
}
