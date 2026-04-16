# Default ProGuard rules for release builds.
# WasmEdge JNI bridge — keep native method declarations
-keep class com.example.mobilewasm.WasmEngine {
    private native *;
}

# Keep manifest model classes (used with org.json reflection-free parsing)
-keep class com.example.mobilewasm.manifest.** { *; }

# Standard Android / Kotlin rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.**
