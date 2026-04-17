// NOLINT(build/header_guard)
#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include <wasmedge/wasmedge.h>

#define TAG        "WasmEngine"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Wasm linear-memory layout for host↔guest JSON transfer
static constexpr uint32_t IN_OFFSET  = 0u;
static constexpr uint32_t OUT_OFFSET = 65536u;   // page 1 (64 KiB)
static constexpr uint32_t OUT_CAP    = 65536u;
static constexpr uint32_t WASM_MAX_INPUT  = 65536u;

struct EngineState {
    WasmEdge_VMContext    *vm    = nullptr;
    WasmEdge_StoreContext *store = nullptr;
    bool                   loaded = false;

    EngineState() {
        WasmEdge_ConfigureContext *conf = WasmEdge_ConfigureCreate();
        store = WasmEdge_StoreCreate();
        vm    = WasmEdge_VMCreate(conf, store);
        WasmEdge_ConfigureDelete(conf);
    }

    ~EngineState() {
        if (vm)    WasmEdge_VMDelete(vm);
        if (store) WasmEdge_StoreDelete(store);
    }

    // Recreate the VM to discard the previous module (hot-swap).
    void reset() {
        if (vm)    WasmEdge_VMDelete(vm);
        if (store) WasmEdge_StoreDelete(store);
        WasmEdge_ConfigureContext *conf = WasmEdge_ConfigureCreate();
        store   = WasmEdge_StoreCreate();
        vm      = WasmEdge_VMCreate(conf, store);
        loaded  = false;
        WasmEdge_ConfigureDelete(conf);
    }
};

// ─────────────────────────────────────────────────────────────────────────────
// JNI entry points
// ─────────────────────────────────────────────────────────────────────────────
extern "C" {

static void ThrowRuntimeException(JNIEnv *env, const char *msg) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, msg);
    }
}

/**
 * Allocate a new engine. Returns an opaque handle (pointer cast to jlong),
 * or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_example_mobilewasm_WasmEngine_nativeInit(JNIEnv * /*env*/, jobject /*thiz*/) {
    auto *state = new EngineState();
    if (!state->vm) {
        LOGE("Failed to create WasmEdge VM");
        delete state;
        return 0L;
    }
    LOGI("WasmEdge VM created (handle=%p)", state);
    return reinterpret_cast<jlong>(state);
}

/**
 * Free the engine previously allocated by nativeInit.
 */
JNIEXPORT void JNICALL
Java_com_example_mobilewasm_WasmEngine_nativeClose(JNIEnv * /*env*/, jobject /*thiz*/,
                                                   jlong handle) {
    if (handle == 0L) return;
    auto *state = reinterpret_cast<EngineState *>(handle);
    LOGI("Closing WasmEdge VM (handle=%p)", state);
    delete state;
}

/**
 * Load (and instantiate) a Wasm module from a raw byte array.
 *  0  → success
 * -1  → load error
 * -2  → validate error
 * -3  → instantiate error
 */
JNIEXPORT jint JNICALL
Java_com_example_mobilewasm_WasmEngine_nativeLoad(JNIEnv *env, jobject /*thiz*/,
                                                  jlong handle, jbyteArray wasmBytes) {
    if (handle == 0L) return -1;
    auto *state = reinterpret_cast<EngineState *>(handle);

    jsize  len   = env->GetArrayLength(wasmBytes);
    jbyte *bytes = env->GetByteArrayElements(wasmBytes, nullptr);
    if (!bytes) return -1;

    // Hot-swap: destroy the previous module and start fresh.
    state->reset();

    WasmEdge_Result res = WasmEdge_VMLoadWasmFromBuffer(
            state->vm,
            reinterpret_cast<const uint8_t *>(bytes),
            static_cast<uint32_t>(len));
    env->ReleaseByteArrayElements(wasmBytes, bytes, JNI_ABORT);

    if (!WasmEdge_ResultOK(res)) {
        LOGE("Load failed: %s", WasmEdge_ResultGetMessage(res));
        return -1;
    }

    res = WasmEdge_VMValidate(state->vm);
    if (!WasmEdge_ResultOK(res)) {
        LOGE("Validate failed: %s", WasmEdge_ResultGetMessage(res));
        return -2;
    }

    res = WasmEdge_VMInstantiate(state->vm);
    if (!WasmEdge_ResultOK(res)) {
        LOGE("Instantiate failed: %s", WasmEdge_ResultGetMessage(res));
        return -3;
    }

    state->loaded = true;
    LOGI("Module loaded and instantiated successfully (%d bytes)", len);
    return 0;
}

/**
 * Execute the exported "run" function of the active module.
 *
 * ABI:  run(inPtr i32, inLen i32, outPtr i32, outCap i32) → outLen i32
 *
 * The host writes the JSON input into wasm linear memory at IN_OFFSET,
 * then reads the JSON output back from OUT_OFFSET after the call.
 *
 * Returns a JSON string; on error the returned JSON contains an "error" key.
 */
JNIEXPORT jstring JNICALL
Java_com_example_mobilewasm_WasmEngine_nativeRun(JNIEnv *env, jobject /*thiz*/,
                                                 jlong handle, jstring jsonInput) {
    if (handle == 0L) {
        ThrowRuntimeException(env, "engine not initialized");
        return nullptr;
    }
    auto *state = reinterpret_cast<EngineState *>(handle);
    if (!state->loaded) {
        ThrowRuntimeException(env, "no module loaded");
        return nullptr;
    }

    if (jsonInput == nullptr) {
        ThrowRuntimeException(env, "null input");
        return nullptr;
    }

    // ── Obtain UTF-8 JSON input ──
    const char *inputCStr = env->GetStringUTFChars(jsonInput, nullptr);
    if (!inputCStr) {
        ThrowRuntimeException(env, "failed to read input string");
        return nullptr;
    }
    std::string input(inputCStr);
    env->ReleaseStringUTFChars(jsonInput, inputCStr);

    if (input.size() > WASM_MAX_INPUT) {
        ThrowRuntimeException(env, "input exceeds 65536 bytes");
        return nullptr;
    }

    // ── Locate the active module instance ──
    const WasmEdge_ModuleInstanceContext *modInst = WasmEdge_VMGetActiveModule(state->vm);
    if (!modInst) {
        ThrowRuntimeException(env, "module instance not found");
        return nullptr;
    }

    WasmEdge_String                memName = WasmEdge_StringCreateByCString("memory");
    WasmEdge_MemoryInstanceContext *memInst =
            WasmEdge_ModuleInstanceFindMemory(modInst, memName);
    WasmEdge_StringDelete(memName);
    if (!memInst) {
        ThrowRuntimeException(env, "memory not exported by module");
        return nullptr;
    }

    // ── Write JSON input to wasm memory ──
    WasmEdge_Result wRes = WasmEdge_MemoryInstanceSetData(
            memInst,
            reinterpret_cast<const uint8_t *>(input.data()),
            IN_OFFSET,
            static_cast<uint32_t>(input.size()));
    if (!WasmEdge_ResultOK(wRes)) {
        ThrowRuntimeException(env, "failed to write input to wasm memory");
        return nullptr;
    }

    // ── Call run(inPtr, inLen, outPtr, outCap) → outLen ──
    WasmEdge_Value params[4] = {
        WasmEdge_ValueGenI32(static_cast<int32_t>(IN_OFFSET)),
        WasmEdge_ValueGenI32(static_cast<int32_t>(input.size())),
        WasmEdge_ValueGenI32(static_cast<int32_t>(OUT_OFFSET)),
        WasmEdge_ValueGenI32(static_cast<int32_t>(OUT_CAP)),
    };
    WasmEdge_Value   results[1];
    WasmEdge_String  funcName = WasmEdge_StringCreateByCString("run");
    WasmEdge_Result  res      = WasmEdge_VMExecute(state->vm, funcName, params, 4, results, 1);
    WasmEdge_StringDelete(funcName);

    if (!WasmEdge_ResultOK(res)) {
        LOGE("Execute failed: %s", WasmEdge_ResultGetMessage(res));
        ThrowRuntimeException(env, "wasm execute failed");
        return nullptr;
    }

    int32_t outLen = WasmEdge_ValueGetI32(results[0]);
    if (outLen < 0 || static_cast<uint32_t>(outLen) > OUT_CAP) {
        ThrowRuntimeException(env, "module returned invalid output length");
        return nullptr;
    }
    if (outLen == 0) return env->NewStringUTF("{}");

    // ── Read JSON output from wasm memory ──
    std::vector<uint8_t> outBuf(static_cast<size_t>(outLen));
    wRes = WasmEdge_MemoryInstanceGetData(
            memInst, outBuf.data(), OUT_OFFSET, static_cast<uint32_t>(outLen));
    if (!WasmEdge_ResultOK(wRes)) {
        ThrowRuntimeException(env, "failed to read output from wasm memory");
        return nullptr;
    }

    return env->NewStringUTF(
        std::string(reinterpret_cast<char *>(outBuf.data()),
                    static_cast<size_t>(outLen)).c_str());
}

} // extern "C"
