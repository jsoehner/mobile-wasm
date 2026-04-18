package com.example.mobilewasm

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.mobilewasm.databinding.ActivityMainBinding
import com.example.mobilewasm.manifest.WasmManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Demo Activity that:
 *  1. Reads `assets/sample/demo.zip` and installs the "demo" package.
 *  2. Loads the first Wasm module declared in the manifest.
 *  3. Lets the user type JSON input and calls the module's `run` export.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: PackageStore
    private lateinit var engine: WasmEngine

    companion object {
        private const val TAG            = "MainActivity"
        private const val DEMO_PACKAGE   = "demo"
        private const val URL_PACKAGE    = "remote"
        private const val FILE_PACKAGE   = "local"
        private const val DEMO_ZIP       = "sample/demo.zip"
        private const val SHA256_HEX_LEN = 64
    }

    private val pickZipLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            setStatus("ZIP selection cancelled")
            return@registerForActivityResult
        }
        installFromZipUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep app content below system bars so top text is not obscured.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store  = PackageStore.getInstance(this)
        engine = WasmEngine.getInstance()

        binding.btnLoad.setOnClickListener    { loadDemoPackage() }
        binding.btnLoadUrl.setOnClickListener { loadPackageFromUrl() }
        binding.btnLoadZip.setOnClickListener { pickZipLauncher.launch("application/zip") }
        binding.btnRun.setOnClickListener     { runModule() }
        binding.btnRun.isEnabled = false
    }

    // ──────────────────────────────────────────────────────────────────────────

    private fun loadDemoPackage() {
        lifecycleScope.launch {
            setUiBusy(true, "Installing demo package…")

            val result = withContext(Dispatchers.IO) {
                try {
                    // Copy demo.zip from assets to a temp file for streaming
                    val tempZip = File(cacheDir, "demo.zip")
                    assets.open(DEMO_ZIP).use { input ->
                        tempZip.outputStream().use { output -> input.copyTo(output) }
                    }

                    store.getInstaller().installFromStream(tempZip.inputStream(), DEMO_PACKAGE)
                } catch (e: Exception) {
                    Log.e(TAG, "Asset copy failed", e)
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { ir -> onPackageInstalled(DEMO_PACKAGE, ir.manifest) },
                onFailure = { err ->
                    setStatus("❌ ${err.message}")
                    setUiBusy(false)
                }
            )
        }
    }

    private fun loadPackageFromUrl() {
        val url = binding.etUrl.text?.toString()?.trim().orEmpty()
        val sha = binding.etSha.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            setStatus("❌ Enter a package URL")
            return
        }
        if (!url.startsWith("https://")) {
            setStatus("❌ URL must start with https://")
            return
        }
        if (sha.length != SHA256_HEX_LEN || !sha.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            setStatus("❌ SHA-256 must be 64 hex characters")
            return
        }

        lifecycleScope.launch {
            setUiBusy(true, "Downloading and installing package…")
            val result = withContext(Dispatchers.IO) {
                store.getInstaller().installFromUrl(
                    url = url,
                    expectedSha256 = sha,
                    packageName = URL_PACKAGE
                )
            }
            result.fold(
                onSuccess = { ir -> onPackageInstalled(URL_PACKAGE, ir.manifest) },
                onFailure = {
                    setStatus("❌ URL install failed: ${it.message}")
                    setUiBusy(false)
                }
            )
        }
    }

    private fun installFromZipUri(uri: Uri) {
        lifecycleScope.launch {
            setUiBusy(true, "Installing package from ZIP…")
            val result = withContext(Dispatchers.IO) {
                try {
                    val input = contentResolver.openInputStream(uri)
                        ?: return@withContext Result.failure(IllegalArgumentException("Unable to read ZIP file"))
                    input.use {
                        store.getInstaller().installFromStream(it, FILE_PACKAGE)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.fold(
                onSuccess = { ir -> onPackageInstalled(FILE_PACKAGE, ir.manifest) },
                onFailure = {
                    setStatus("❌ ZIP install failed: ${it.message}")
                    setUiBusy(false)
                }
            )
        }
    }

    private suspend fun onPackageInstalled(packageName: String, manifest: WasmManifest) {
        val firstModule = manifest.modules.first()
        setStatus("Package '${manifest.name}' installed — loading module '${firstModule.name}'…")

        val loadResult = withContext(Dispatchers.IO) {
            val bytes = store.getModuleBytes(packageName, firstModule.name)
            if (bytes == null) {
                Result.failure<Unit>(IllegalStateException("Wasm bytes not found in store"))
            } else {
                engine.load(firstModule.name, bytes)
            }
        }

        loadResult.fold(
            onSuccess = {
                setStatus("✅ Module '${firstModule.name}' ready — ${manifest.modules.size} module(s) in package")
                binding.btnRun.isEnabled = true
            },
            onFailure = { err ->
                setStatus("❌ Load error: ${err.message}")
            }
        )
        setUiBusy(false)
    }

    private fun runModule() {
        val json = binding.etInput.text.toString().ifBlank { "{}" }
        lifecycleScope.launch {
            setUiBusy(true, "Running…")
            val result = withContext(Dispatchers.IO) { engine.run(json) }
            result.fold(
                onSuccess  = {
                    binding.tvOutput.text = it
                    setStatus("✅ Run completed")
                },
                onFailure  = {
                    binding.tvOutput.text = "❌ ${it.message}"
                    setStatus("❌ Run error: ${it.message}")
                }
            )
            setUiBusy(false)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    private fun setStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun setUiBusy(busy: Boolean, status: String = "") {
        if (status.isNotEmpty()) setStatus(status)
        binding.progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnLoad.isEnabled = !busy
        binding.btnLoadUrl.isEnabled = !busy
        binding.btnLoadZip.isEnabled = !busy
        if (!busy && engine.activeModuleName == null) binding.btnRun.isEnabled = false
    }
}
