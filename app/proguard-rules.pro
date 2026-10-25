# R8 shrinks the libraries. The app's own classes and the Kotlin library stay whole and named: the device tests'
# APK (androidTest, run against the release app) calls them and leaves out what the app has; crash traces read as the source.
-dontobfuscate
-keep class io.github.kafeg.inkpeek.** { *; }
-keep class kotlin.** { *; }
