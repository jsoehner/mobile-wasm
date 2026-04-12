cmake_minimum_required(VERSION 3.22.1)
project(mobilewasm CXX)

# ── WasmEdge prebuilt location (passed from Gradle via -DWASMEDGE_PREBUILT_DIR) ──
if(NOT DEFINED WASMEDGE_PREBUILT_DIR)
    message(FATAL_ERROR
        "WASMEDGE_PREBUILT_DIR is not set. "
        "Run the 'downloadWasmedge' Gradle task before building.")
endif()

set(WASMEDGE_LIB "${WASMEDGE_PREBUILT_DIR}/lib/libwasmedge.so")
set(WASMEDGE_INCLUDE "${WASMEDGE_PREBUILT_DIR}/include")

if(NOT EXISTS "${WASMEDGE_LIB}")
    message(FATAL_ERROR "libwasmedge.so not found at ${WASMEDGE_LIB}. "
        "Run the 'downloadWasmedge' Gradle task first.")
endif()

# ── Imported WasmEdge shared library ──
add_library(wasmedge SHARED IMPORTED)
set_target_properties(wasmedge PROPERTIES
    IMPORTED_LOCATION "${WASMEDGE_LIB}"
    INTERFACE_INCLUDE_DIRECTORIES "${WASMEDGE_INCLUDE}"
)

# ── JNI bridge ──
add_library(mobilewasm SHARED
    wasmedge_jni.cpp
)

target_include_directories(mobilewasm PRIVATE "${WASMEDGE_INCLUDE}")

target_link_libraries(mobilewasm
    wasmedge
    android
    log
)
