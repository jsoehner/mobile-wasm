package com.example.mobilewasm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * High‑level wrapper around [WasmEngine] that provides typed methods for the chess
 * WebAssembly module. The underlying WASM module follows a simple JSON‑based
 * protocol where each request is a JSON object with an `action` field and the
 * required parameters. The module returns a JSON response that contains the
 * resulting board state (`board`) or other data.
 */
class WasmChessEngine private constructor() {
    private val engine = WasmEngine.getInstance()
    private var moduleLoaded = false

    companion object {
        private const val TAG = "WasmChessEngine"
        @Volatile private var instance: WasmChessEngine? = null
        fun getInstance(): WasmChessEngine =
            instance ?: synchronized(this) {
                instance ?: WasmChessEngine().also { instance = it }
            }
    }

    /** Load a compiled WASM binary and mark the module as ready. */
    suspend fun loadModule(name: String, wasmBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val result = engine.load(name, wasmBytes)
        if (result.isSuccess) moduleLoaded = true
        result
    }

    /** Initialise the chess board inside the WASM module. */
    suspend fun initBoard(): Result<String> = sendCommand("init_board", JSONObject())

    /** Make a move given a FEN string and a move in UCI format. */
    suspend fun makeMove(fen: String, move: String): Result<String> =
        sendCommand("make_move", JSONObject().apply {
            put("fen", fen)
            put("move", move)
        })

    /** Validate a move. Returns a boolean in the JSON response under `valid`. */
    suspend fun isValidMove(fen: String, move: String): Result<Boolean> =
        sendCommand("is_valid_move", JSONObject().apply {
            put("fen", fen)
            put("move", move)
        }).mapCatching {
            JSONObject(it).getBoolean("valid")
        }

    /** Retrieve all possible moves for the current board state. */
    suspend fun getPossibleMoves(fen: String): Result<List<String>> =
        sendCommand("get_possible_moves", JSONObject().apply { put("fen", fen) })
            .mapCatching { json ->
                val arr = JSONObject(json).getJSONArray("moves")
                List(arr.length()) { i -> arr.getString(i) }
            }

    /** Internal helper to send a JSON command to the WASM module. */
    private suspend fun sendCommand(action: String, payload: JSONObject): Result<String> {
        if (!moduleLoaded) return Result.failure(IllegalStateException("WASM module not loaded"))
        val request = payload.apply { put("action", action) }.toString()
        return engine.run(request)
    }
}
