import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** "<appVersion>.<commits>": the third number is the project's commit count, also the version code (it only grows). */
val commits = providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.get().trim().toIntOrNull() ?: 1

val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.kafeg.inkpeek"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.kafeg.inkpeek"
        minSdk = 30 // Android 11; the translation engine needs API 28+
        targetSdk = 35
        versionCode = commits
        versionName = "${providers.gradleProperty("appVersion").get()}.$commits"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") } // e-ink readers are 64-bit ARM; the engine is built for it only
        externalNativeBuild { cmake {
            arguments += listOf("-DANDROID_STL=c++_static", "-DANDROID_CPP_FEATURES=exceptions")
            targets += listOf("inkpeek_mt", "inkpeek_llm") // the JNI libraries only, not marian's or llama's tools
        } }
    }
    ndkVersion = "27.3.13750724"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    testOptions { unitTests.isReturnDefaultValues = true } // android.util.Log in JVM tests
    testBuildType = "release" // device tests run in the installed release app (same signature, its packs)
    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    // Compressed dex and .so: a smaller APK to sideload (minSdk 28+ would store them uncompressed).
    packaging { dex { useLegacyPackaging = true }; jniLibs { useLegacyPackaging = true }; resources { excludes += setOf("META-INF/*.md", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/versions/**") } }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.anthropic:anthropic-java:2.34.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10") // .tar.xz dictionaries (FreeDict)
    implementation("com.github.luben:zstd-jni:1.5.6-8@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
