# No reflection-based libraries yet; default rules are enough.
# TensorFlow Lite calls into Java from native code.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
