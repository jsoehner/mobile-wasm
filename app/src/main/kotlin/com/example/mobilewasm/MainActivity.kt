package com.example.mobilewasm

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
        private const val TAG          = "MainActivity"
        private const val DEMO_PACKAGE = "demo"
        private const val DEMO_ZIP     = "sample/demo.zip"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store  = PackageStore.getInstance(this)
        engine = WasmEngine.getInstance()

        binding.btnLoad.setOnClickListener { loadDemoPackage() }
        binding.btnRun.setOnClickListener  { runModule()       }
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
                    assets.open(DEMO_ZIP).use { it.copyTo(tempZip.outputStream()) }

                    store.getInstaller().installFromStream(tempZip.inputStream(), DEMO_PACKAGE)
                } catch (e: Exception) {
                    Log.e(TAG, "Asset copy failed", e)
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { ir -> onPackageInstalled(ir.manifest) },
                onFailure = { err ->
                    setStatus("❌ ${err.message}")
                    setUiBusy(false)
                }
            )
        }
    }

    private suspend fun onPackageInstalled(manifest: WasmManifest) {
        val firstModule = manifest.modules.first()
        setStatus("Package '${manifest.name}' installed — loading module '${firstModule.name}'…")

        val loadResult = withContext(Dispatchers.IO) {
            val bytes = store.getModuleBytes(DEMO_PACKAGE, firstModule.name)
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
                onSuccess  = { binding.tvOutput.text = it },
                onFailure  = { binding.tvOutput.text = "❌ ${it.message}" }
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
        if (!busy && engine.activeModuleName == null) binding.btnRun.isEnabled = false
    }
}
