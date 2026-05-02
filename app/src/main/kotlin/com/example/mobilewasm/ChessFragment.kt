// ... existing code ...
        binding.btnReset.setOnClickListener {
            binding.chessView.resetBoard()
            setStatus("Game reset")
        }

        // New: Handle loading a custom/user-created module from source code.
        binding.btnLoadCustom.setOnClickListener {
            simulateCompilationAndLoad()
        }
    }

    override fun onDestroyView() {
// ... existing code ...
    /** 
     * Loads the fixed, bundled chess engine asset (e.g., from assets/chess.wasm).
     */
    private fun loadAssetEngine() {
// ... existing code ...
        }
    }
    
    /** 
     * 
     * Simulates the entire flow:
     * 1. Read Source Code (Mock).
     * 2. Compile (using WasmCompilerService).
     * 3. Load the resulting WASM bytes into the WasmEngine.
     */
    private fun simulateCompilationAndLoad() {
        lifecycleScope.launch {
            setStatus("Compiling new engine... (Simulated source input)")
            
            // Step 1: Mock Source Code Input
            val mockSourceCode = "// Example chess logic in C++ or Rust..."
            
            // Step 2: Simulate Compilation
            val compiledBytes = WasmCompilerService.compile(mockSourceCode, "C++")
            
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

    /** 
     * Placeholder function. This function must be replaced 
     * by reading the output of a simulated compiler component.
     * In a real app, this would trigger a system file picker and 
     * read the WASM file contents.
     */
    private fun generateMockWasmBytes(): ByteArray {
        // This function is now obsolete as we use the compiler service directly.
        Log.w(TAG, "generateMockWasmBytes is now obsolete. Use simulateCompilationAndLoad instead.")
        return ByteArray(0) 
    }
}