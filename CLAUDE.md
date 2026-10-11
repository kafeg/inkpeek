# InkPeek

Pop-up dictionary for Android e-ink readers: local StarDict + AI (Claude) actions in one card over the page.
Spec — `docs/spec.md` (source of truth). Public repo (MIT): no secrets, keys, personal paths or IPs in commits.

## Stack
Kotlin, Android Views, minSdk 26 / targetSdk 35, no third-party libraries. Packages: `stardict` (pure JVM),
`ai`, `ui`. JVM unit tests for `stardict`.

## Build and run
```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                          # stardict unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set io.github.kafeg.inkpeek MANAGE_EXTERNAL_STORAGE allow
adb shell am start -a colordict.intent.action.SEARCH --es EXTRA_QUERY Haus   # open the card
```
Android SDK: `ANDROID_HOME` (macOS brew: `android-commandlinetools`).

## Rules
- Branch model: `main` only, commit and push to `main`.
- Spec first: a new feature → `docs/spec.md` → implement by milestone, stop after each.
- KISS; comments — one line at most. No WebView, no animations, no colors (e-ink).
- The API key never goes to logs, UI or commits.
