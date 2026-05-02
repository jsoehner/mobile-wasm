package com.example.mobilewasm

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.mobilewasm.databinding.FragmentChessBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment that demonstrates a Chess game powered by a WASM engine.
 * Includes a simulation of compiling a custom engine from source.
 */
class ChessFragment : Fragment() {

    private var _binding: FragmentChessBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var engine: WasmEngine
    private val compilerService = WasmCompilerService()

    companion object {
        private const val TAG = "ChessFragment"
        private const val CHESS_WASM = "chess.wasm"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        engine = WasmEngine.getInstance()

        binding.chessView.onMoveListener = { from, to ->
            handleMove(from, to)
        }

        binding.btnReset.setOnClickListener {
            binding.chessView.resetBoard()
            setStatus("Game reset")
        }

        binding.btnLoadCustom.setOnClickListener {
            simulateCompilationAndLoad()
        }

        loadEngine()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadEngine() {
        lifecycleScope.launch {
            setStatus("Loading Chess engine…")
            val result = withContext(Dispatchers.IO) {
                try {
                    // Search for chess.wasm in assets
                    val bytes = requireContext().assets.open(CHESS_WASM).use { it.readBytes() }
                    engine.load("chess_engine", bytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load chess engine", e)
                    Result.failure(e)
                }
            }
            
            result.fold(
                onSuccess = { setStatus("White to move") },
                onFailure = { setStatus("❌ Engine load failed: ${it.message}") }
            )
        }
    }

    private fun handleMove(from: String, to: String) {
        val json = """{"action":"move","from":"$from","to":"$to"}"""
        
        lifecycleScope.launch {
            setStatus("Thinking…")
            val result = withContext(Dispatchers.IO) {
                engine.run(json)
            }
            
            result.fold(
                onSuccess = {
                    binding.chessView.movePiece(from, to)
                    setStatus("Move: $from to $to. Your turn.")
                },
                onFailure = {
                    setStatus("❌ Invalid move: ${it.message}")
                }
            )
        }
    }

    /** 
     * Simulates the entire flow:
     * 1. Read Source Code (Mock).
     * 2. Compile (using WasmCompilerService).
     * 3. Load the resulting WASM bytes into the WasmEngine.
     */
    private fun simulateCompilationAndLoad() {
        lifecycleScope.launch {
            setStatus("Compiling new engine... (Simulated source input)")
            
            // Step 1: Mock Source Code Input
            val mockSourceCode = "// Example chess logic in C++ or Rust...\n// Modified for custom behavior."
            
            // Step 2: Simulate Compilation
            val compiledBytes = withContext(Dispatchers.Default) {
                compilerService.compile(mockSourceCode, "C++")
            }
            
            if (compiledBytes == null) {
                setStatus("❌ Compilation failed. Check source code and compiler setup.")
                return@launch
            }
            
            // Step 3: Load the resulting WASM bytes
            setStatus("Compilation successful. Attempting to load module...")
            val result = withContext(Dispatchers.IO) {
                engine.load("user_compiled_engine", compiledBytes)
            }
            
            result.fold(
                onSuccess = { setStatus("✅ Successfully compiled and loaded custom engine.") },
                onFailure = { setStatus("❌ Loading failed: ${it.message}") }
            )
        }
    }

    private fun setStatus(msg: String) {
        binding.tvChessStatus.text = msg
    }
}