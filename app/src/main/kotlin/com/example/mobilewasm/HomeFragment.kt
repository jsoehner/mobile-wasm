package com.example.mobilewasm

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.mobilewasm.databinding.FragmentHomeBinding
import com.example.mobilewasm.manifest.WasmManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding not initialized")
    
    private lateinit var store: PackageStore
    private lateinit var engine: WasmEngine

    companion object {
        private const val TAG            = "HomeFragment"
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        store  = PackageStore.getInstance(requireContext())
        engine = WasmEngine.getInstance()

        binding.btnLoad.setOnClickListener    { loadDemoPackage() }
        binding.btnLoadUrl.setOnClickListener { loadPackageFromUrl() }
        binding.btnLoadZip.setOnClickListener { pickZipLauncher.launch("application/zip") }
        binding.btnRun.setOnClickListener     { runModule() }
        
        // Initial state
        if (engine.activeModuleName == null) {
            binding.btnRun.isEnabled = false
        }

        // Long click to copy output
        binding.tvOutput.setOnLongClickListener {
            val text = binding.tvOutput.text.toString()
            if (text != "—") {
                copyToClipboard(text)
                true
            } else false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadDemoPackage() {
        lifecycleScope.launch {
            setUiBusy(true, "Installing demo package…")

            val result = withContext(Dispatchers.IO) {
                try {
                    val tempZip = File(requireContext().cacheDir, "demo.zip")
                    requireContext().assets.open(DEMO_ZIP).use { input ->
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
                store.getInstaller().installFromUrl(url, sha, URL_PACKAGE)
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
                    val input = requireContext().contentResolver.openInputStream(uri)
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
                setStatus("✅ Module '${firstModule.name}' ready")
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
        try {
            android.util.JsonReader(java.io.StringReader(json)).use { it.hasNext() }
        } catch (e: Exception) {
            setStatus("❌ Invalid JSON input")
            return
        }

        lifecycleScope.launch {
            setUiBusy(true, "Executing module…")
            val result = withContext(Dispatchers.IO) { engine.run(json) }
            result.fold(
                onSuccess  = {
                    binding.tvOutput.text = it
                    setStatus("✅ Execution successful")
                },
                onFailure  = {
                    binding.tvOutput.text = "Error: ${it.message}"
                    setStatus("❌ execution failed")
                }
            )
            setUiBusy(false)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("Wasm Output", text)
        clipboard.setPrimaryClip(clip)
        setStatus("📋 Copied to clipboard")
    }

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
