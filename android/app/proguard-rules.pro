# Rebuild — keep Lua / reflection / ONNX / app entry points
-keep class com.tapsprite.agent.** { *; }
-keep class org.luaj.** { *; }
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn ai.onnxruntime.**
-dontwarn org.luaj.**
