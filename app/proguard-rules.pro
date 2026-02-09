# AvaCore ProGuard/R8 Rules

# Preserve Sherpa-ONNX JNI interfaces
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# Preserve Android TTS Service methods
-keep class com.github.opscalehub.avacore.service.AvaTtsService { *; }

# General optimizations
-dontwarn com.k2fsa.sherpa.onnx.**
