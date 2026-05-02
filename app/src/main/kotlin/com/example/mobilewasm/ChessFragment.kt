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

class ChessFragment : Fragment() {

    private var _binding: FragmentChessBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var engine: WasmEngine

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
                    // In a real app, chess.wasm would be pre-compiled in assets.
                    // For now, we expect it to be there.
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
                    // The engine returns {"status":"ok"}
                    binding.chessView.movePiece(from, to)
                    setStatus("Move: $from to $to. Your turn.")
                },
                onFailure = {
                    setStatus("❌ Invalid move: ${it.message}")
                }
            )
        }
    }

    private fun setStatus(msg: String) {
        binding.tvChessStatus.text = msg
    }
}
