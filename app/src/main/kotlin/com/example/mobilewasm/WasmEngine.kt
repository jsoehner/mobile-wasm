package com.example.mobilewasm

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-wide singleton that owns the native WasmEdge runtime.
 *
 * The engine is guarded by a [Mutex] so that concurrent coroutine callers
 * cannot race on [load] / [run].  [load] hot-swaps the active module by
 * recreating the VM internally—the previous module is discarded atomically.
 *
 * Run ABI (Wasm export): `run(inPtr i32, inLen i32, outPtr i32, outCap i32) → outLen i32`
 * – JSON input  is written into wasm linear memory at offset 0 (max 65 536 bytes)
 * – JSON output is read  from wasm linear memory at offset 65 536 (max 65 536 bytes)
 */
class WasmEngine private constructor() {

    private val mutex          = Mutex()
    private var nativeHandle   = 0L
    private var activeModule   : String? = null

    companion object {
        private const val TAG = "WasmEngine"

        @Volatile private var instance: WasmEngine? = null

        fun getInstance(): WasmEngine =
            instance ?: synchronized(this) {
                instance ?: WasmEngine().also { instance = it }
            }
    }

    init {
        System.loadLibrary("mobilewasm")
        nativeHandle = nativeInit()
        check(nativeHandle != 0L) { "Failed to initialise WasmEdge native engine" }
        Log.i(TAG, "WasmEngine initialised")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API (suspend – all calls must happen from a coroutine)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Load [wasmBytes] as the module named [moduleName].
     * Any previously active module is discarded (hot-swap).
     */
    suspend fun load(moduleName: String, wasmBytes: ByteArray): Result<Unit> =
        mutex.withLock {
            val code = nativeLoad(nativeHandle, wasmBytes)
            if (code == 0) {
                activeModule = moduleName
                Log.i(TAG, "Module '$moduleName' loaded (${wasmBytes.size} bytes)")
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("nativeLoad returned error code $code"))
            }
        }

    /**
     * Execute the active module's `run` export with [jsonInput].
     * Returns the JSON string produced by the module.
     */
    suspend fun run(jsonInput: String): Result<String> =
        mutex.withLock {
            if (activeModule == null) {
                return Result.failure(IllegalStateException("No module is loaded"))
            }
            Result.success(nativeRun(nativeHandle, jsonInput))
        }

    /** Returns the name of the currently active module, or `null`. */
    val activeModuleName: String? get() = activeModule

    /** Release the native engine. After this call the singleton is invalid. */
    fun close() {
        val h = nativeHandle
        nativeHandle = 0L
        activeModule = null
        instance     = null
        if (h != 0L) nativeClose(h)
        Log.i(TAG, "WasmEngine closed")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // JNI declarations
    // ──────────────────────────────────────────────────────────────────────────

    private external fun nativeInit(): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeLoad(handle: Long, wasmBytes: ByteArray): Int
    private external fun nativeRun(handle: Long, jsonInput: String): String
}
