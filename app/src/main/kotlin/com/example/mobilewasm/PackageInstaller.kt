package com.example.mobilewasm

import android.util.Log
import com.example.mobilewasm.manifest.ManifestParser
import com.example.mobilewasm.manifest.ManifestValidator
import com.example.mobilewasm.manifest.WasmManifest
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Downloads (or receives) a WasmEdge ZIP package, verifies its SHA-256,
 * performs ZIP-slip-safe extraction, and validates the embedded `manifest.json`.
 *
 * @param installDir Root directory under which packages are extracted.
 *                   Each package lands in `installDir/<packageName>/`.
 */
class PackageInstaller(private val installDir: File) {

    data class InstallResult(
        val manifest: WasmManifest,
        val installPath: File
    )

    companion object {
        private const val TAG              = "PackageInstaller"
        private const val MANIFEST_NAME    = "manifest.json"
        private const val MAX_ENTRY_BYTES  = 50L * 1024 * 1024   // 50 MiB per file
        private const val BUFFER_SIZE      = 8192
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Download the package from [url], verify its SHA-256 against [expectedSha256]
     * (case-insensitive hex), extract it as [packageName], and parse/validate
     * the manifest.
     */
    fun installFromUrl(
        url: String,
        expectedSha256: String,
        packageName: String
    ): Result<InstallResult> = runCatching {
        Log.i(TAG, "Downloading package from $url")
        val bytes = downloadBytes(url)
        verifySha256(bytes, expectedSha256)
        installBytes(bytes.inputStream(), packageName).getOrThrow()
    }

    /**
     * Read the package bytes directly from [stream], extract as [packageName],
     * and parse/validate the manifest.  No SHA-256 check is performed.
     */
    fun installFromStream(
        stream: InputStream,
        packageName: String
    ): Result<InstallResult> = runCatching {
        installBytes(stream, packageName).getOrThrow()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────────────────

    private fun downloadBytes(urlString: String): ByteArray {
        var conn = URL(urlString).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        // Manually follow up to 5 redirects
        repeat(5) {
            if (conn.responseCode in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                conn = URL(location).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
            }
        }
        return conn.inputStream.use { it.readBytes() }
    }

    private fun verifySha256(data: ByteArray, expectedHex: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val actual = digest.digest(data).joinToString("") { "%02x".format(it) }
        check(actual.equals(expectedHex.trim(), ignoreCase = true)) {
            "SHA-256 mismatch — expected: $expectedHex  got: $actual"
        }
        Log.i(TAG, "SHA-256 verified ✓")
    }

    private fun installBytes(stream: InputStream, packageName: String): Result<InstallResult> {
        val packageDir = File(installDir, packageName)
        packageDir.mkdirs()

        return try {
            extractZipSafe(stream, packageDir)

            val manifestFile = File(packageDir, MANIFEST_NAME)
            if (!manifestFile.exists()) {
                packageDir.deleteRecursively()
                return Result.failure(
                    IllegalArgumentException("Package is missing $MANIFEST_NAME")
                )
            }

            val manifest = ManifestParser.parse(manifestFile.readText())
            ManifestValidator.validate(manifest).onFailure { err ->
                packageDir.deleteRecursively()
                return Result.failure(err)
            }

            Log.i(TAG, "Package '$packageName' installed — ${manifest.modules.size} module(s)")
            Result.success(InstallResult(manifest, packageDir))
        } catch (e: Exception) {
            packageDir.deleteRecursively()
            Log.e(TAG, "Installation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts a ZIP stream into [destDir] while guarding against ZIP-slip.
     *
     * Every entry's canonical path is verified to start with the canonical
     * destination path before any bytes are written.  Individual entries are
     * also capped at [MAX_ENTRY_BYTES].
     */
    private fun extractZipSafe(stream: InputStream, destDir: File) {
        val canonicalDest = destDir.canonicalPath + File.separator
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name)

                // ZIP-slip guard
                if (!target.canonicalPath.startsWith(canonicalDest)) {
                    throw SecurityException("ZIP-slip attempt blocked: '${entry.name}'")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    var written = 0L
                    target.outputStream().use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (zip.read(buf).also { read = it } != -1) {
                            written += read
                            if (written > MAX_ENTRY_BYTES) {
                                throw SecurityException(
                                    "Entry '${entry.name}' exceeds $MAX_ENTRY_BYTES byte limit"
                                )
                            }
                            out.write(buf, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
