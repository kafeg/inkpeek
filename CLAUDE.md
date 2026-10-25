# InkPeek

Pop-up dictionary for Android e-ink readers: local StarDict + AI (Claude) actions in one card over the page.
Spec — `docs/spec.md` (source of truth). Public repo (MIT): no secrets, keys, personal paths or IPs in commits.

## Stack
Kotlin, Android Views, minSdk 30 / targetSdk 35, arm64 only. Libraries: anthropic-java (Claude), OkHttp (OpenAI /
OpenRouter, downloads), coroutines, commons-compress + xz + zstd-jni (dictionary archives). Native: Bergamot
(submodule `app/src/main/cpp/bergamot`, RTranslator's Android fork, pinned) + fastText (submodule
`app/src/main/cpp/fasttext`) + JNI → `libinkpeek_mt.so`; llama.cpp (submodule `app/src/main/cpp/llama`) + JNI →
`libinkpeek_llm.so` (the on-device AI translation model); PCRE2 (submodule `app/src/main/cpp/pcre2`, for Bergamot's
sentence splitting: nothing is downloaded at build time); CMake 3.22.1 and NDK from the SDK (Gradle installs them).
Packages: `stardict` (pure JVM engine; `Library`: the packs' dictionaries, then other ones (`InkPeek/dicts`, folders
in place) — lookup, header, gloss by answer language; `Importer`; `Catalog` of public sources — JVM-tested), `packs`
(manifest, download, install), `ai` (requests, `Answers`), `mt` (Bergamot, language id, `OfflineTranslation`), `ui`:
`CardActivity` (window, header, icon bar) + one class per tab (`DictionaryPage`, `TranslatePage`, `AiPage`,
`WordsPage`), `MainActivity` (settings shell: ⚙ `AppPage` and ? `HelpPage` in `Pages.kt` open over it) + one class
per section (`LanguagesSettings` with `MethodPane` (offline translation), `AiSettings`, `CardSettings`,
`WordsSettings`), all built of `Rows` (the one row style; at most three sub-tabs, every setting two lines); `PacksActivity` / `CatalogActivity` (packs /
other dictionaries to download), `OrderActivity` (dictionary order), `Downloads` (one download for the process: packs,
dictionaries, the app), `Updates` + `About` (GitHub releases, in-app install), `TextActions` (selection, our copy /
share / web menu). Indexes: one builder thread and one build per dictionary for the process (`Library`, `StarDict`).
Without the in-app updater (F-Droid, stores): `./gradlew assembleRelease -Pinkpeek.updates=false` — see `docs/fdroid.md`.
Language packs are built in `packs/` (Python, see its README). JVM unit tests. Settings — `Prefs.kt`; `App` hands AI its settings (every screen).
New language (book or answer) or translation model: `packs/README.md` → "Adding a language" — every step, in order.

## Build and run
```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set io.github.kafeg.inkpeek MANAGE_EXTERNAL_STORAGE allow
adb shell am start -a colordict.intent.action.SEARCH --es EXTRA_QUERY Haus   # open the card
scripts/device-test.sh                  # packs on the device: headers, meanings, language, offline translation
```
Device tests run inside the installed release app (`testBuildType = "release"`, same signature, its packs); cases in
`app/src/androidTest/assets/cases.tsv`, results in `adb logcat -s InkPeekTest`. A failing case is a finding about the
packs or the code: fix the data or the code, never the expectation to make it pass. Boox: `adb exec-out screencap`
prints a line before the PNG — use `screencap -p /sdcard/s.png` and `adb pull`.
Android SDK: `local.properties` `sdk.dir` (here `~/Library/Android/sdk`) or `ANDROID_HOME`.
Version: `appVersion` in `gradle.properties` + the git commit count (`<appVersion>.<commits>`; the count is also the
version code; the app's update check compares it with the latest tag's last number).
Release: `scripts/release.sh [--install]` only (macOS; after a failure run it again — it resumes) — it checks (main,
clean, pushed, tests, a `CHANGELOG.md` section `## <appVersion>`), dates that section, tags, waits for `release.yml`
(starts it by hand when GitHub lost the push), checks the APK's checksum and signing certificate and that it is the
latest release; `--install` puts it on every connected device. Before it: the version's bullets in `CHANGELOG.md`
(what changed for people who use the app, per minor version); a build's release notes and the in-app update show the commit subjects
since the previous tag (minus `CHANGELOG`/`CI`/`Docs` ones), so write subjects for people; a new minor version →
`appVersion` and a new `## x.y — unreleased`.
Layout checks and README pictures: `scripts/screens.sh` (every settings tab, page by page, on each connected device;
`am start … --es open lang:other` opens a tab).
Release build on a device with the release installed: `keystore.properties` (gitignored: storeFile, storePassword,
keyAlias, keyPassword), `./gradlew assembleRelease`, `adb install -r` keeps the settings (a debug build needs an
uninstall). Two devices on adb: `ANDROID_SERIAL=<serial>` for every command. AI timings: `adb logcat -s InkPeek` (`ai … ms`). Boox screenshots turn white
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
- **Reference for LexiPeek** (desktop sibling, github.com/kafeg/lexipeek): InkPeek defines behaviour and data formats
  (`docs/formats.md`, cases in `tests/`); no shared code. A format changes in `docs/formats.md` and `tests/` first.
  Never change a case to make one app pass.
