# R8 shrinks the libraries. The app's own classes and the Kotlin library stay whole and named: the device tests'
# APK (androidTest, run against the release app) calls them and leaves out what the app has; crash traces read as the source.
-dontobfuscate
-keep class io.github.kafeg.inkpeek.** { *; }
-keep class kotlin.** { *; }
# The AI client builds its JSON with Jackson by reflection and annotations: both stay whole.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
# Desktop Java reflection types of its JSON-schema helper (structured outputs, not used here): not on Android.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
