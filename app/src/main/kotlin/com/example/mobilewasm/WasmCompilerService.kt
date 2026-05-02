package com.example.mobilewasm

import android.util.Log

/**
 * A service class simulating the process of compiling a source (e.g., C++/Rust/Chess logic)
 * into the final WASM binary format.
 */
class WasmCompilerService {

    companion object {
        private const val TAG = "WasmCompiler"
        
        /** Minimal valid WASM module header: \0asm + version 1 */
        private val WASM_MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
    }

    /**
     * Simulates compiling source code into a WASM byte array.
     *
     * @param sourceCode The raw source code to compile.
     * @param language The source language (e.g., "C++", "Rust").
     * @return A ByteArray representing the compiled WASM module, or null on failure.
     */
    fun compile(sourceCode: String, language: String): ByteArray? {
        Log.d(TAG, "Starting compilation simulation for $language source code...")

        if (sourceCode.isBlank()) {
            Log.e(TAG, "Cannot compile: Source code is blank.")
            return null
        }

        // To simulate a successful compilation, we generate a valid WASM header
        // followed by mock data that satisfies basic size requirements.
        val mockSize = 1024 + (sourceCode.length % 1024)
        val result = ByteArray(WASM_MAGIC.size + mockSize)
        
        // Copy magic header
        System.arraycopy(WASM_MAGIC, 0, result, 0, WASM_MAGIC.size)
        
        // Fill the rest with some "code" (just nops or similar, doesn't really matter for simulation)
        for (i in WASM_MAGIC.size until result.size) {
            result[i] = 0x01 // i32.const or similar-ish byte
        }

        Log.i(TAG, "Compilation successful. Generated mock WASM bytes (${result.size} bytes).")
        return result
    }
}