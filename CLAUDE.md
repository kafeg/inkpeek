# InkPeek

Pop-up dictionary for Android e-ink readers: local StarDict + AI (Claude) actions in one card over the page.
Spec — `docs/spec.md` (source of truth). Public repo (MIT): no secrets, keys, personal paths or IPs in commits.

## Stack
Kotlin, Android Views, minSdk 30 / targetSdk 35, arm64 only. Libraries: anthropic-java (Claude), OkHttp (OpenAI /
OpenRouter, downloads), coroutines, commons-compress + zstd-jni. Native: Bergamot (submodule
`app/src/main/cpp/bergamot`, RTranslator's Android fork, pinned) + fastText (submodule `app/src/main/cpp/fasttext`) +
JNI → `libinkpeek_mt.so`; CMake 3.22.1 and NDK from the SDK (Gradle installs them). Packages: `stardict` (pure JVM
engine), `packs` (manifest, download, install), `ai`, `mt` (Bergamot engine, language id), `ui` (card, settings).
Language packs are built in `packs/` (Python, see its README). JVM unit tests. Settings — `Prefs.kt`.

## Build and run
```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                          # stardict unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set io.github.kafeg.inkpeek MANAGE_EXTERNAL_STORAGE allow
adb shell am start -a colordict.intent.action.SEARCH --es EXTRA_QUERY Haus   # open the card
scripts/device-test.sh                  # packs on the device: headers, meanings, language, offline translation
```
Device tests run inside the installed release app (`testBuildType = "release"`, same signature, its packs); cases in
`app/src/androidTest/assets/cases.tsv`, results in `adb logcat -s InkPeekTest`. A failing case is a finding about the
packs or the code: fix the data or the code, never the expectation to make it pass. Boox: `adb exec-out screencap`
prints a line before the PNG — use `screencap -p /sdcard/s.png` and `adb pull`.
Android SDK: `ANDROID_HOME` (macOS brew: `android-commandlinetools`).
Release build on a device with the release installed: `keystore.properties` (gitignored: storeFile, storePassword,
keyAlias, keyPassword), `VERSION_CODE=<above the installed> ./gradlew assembleRelease`, `adb install -r` keeps the
settings (a debug build needs an uninstall). AI timings: `adb logcat -s InkPeek` (`ai … ms`). Boox screenshots turn white
when the screen sleeps: `adb shell input keyevent KEYCODE_WAKEUP` first.

## Rules
- Branch model: `main` only, commit and push to `main`.
- Spec first: a new feature → `docs/spec.md` → implement by milestone, stop after each.
- **No AI in core.** Look-up, the header (article, plural, verb forms) and translation work from local data,
  offline. AI is only optional, marked tabs, inactive when AI is unavailable. No guessing heuristics in core data
  (no ending stripping): real data from the language packs.
- **An app, not a device.** Everything a user needs comes from the app's own downloads: curated language packs
  (dictionary, grammar, translation models) built by our scripts and covered by tests. Never fix quality by
  hand-installing or deleting files on a test device; a manual step there is a missing feature.
- KISS; comments — one line at most. No WebView, no animations, no colors (e-ink).
- The API key never goes to logs, UI or commits.
