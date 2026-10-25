# F-Droid

The F-Droid build is made and signed by F-Droid from the sources (not our APK: two separate signatures, GitHub and
F-Droid builds do not update each other). It has no in-app updater: `-Pinkpeek.updates=false` (`gradleprops` below)
drops the update check, its download and the REQUEST_INSTALL_PACKAGES permission.

Descriptions, icon, screenshots: `fastlane/metadata/android/<locale>/` (F-Droid reads them from the tagged commit).
A release's notes there: `changelogs/<versionCode>.txt` (≤ 500 characters; versionCode = the commit count of the tag):
write them as `changelogs/next.txt` per locale before a release, `scripts/release.sh` gives the file its number.

Native code builds from submodules only: Bergamot, fastText, llama.cpp, PCRE2 (pinned to 10.39, what ssplit would
otherwise download at configure time).

Anti-feature: NonFreeNet — optional AI (Anthropic, OpenAI, OpenRouter) and the packs downloaded from GitHub releases.

Recipe draft for fdroiddata (`metadata/io.github.kafeg.inkpeek.yml`; fill the version, test with `fdroid build -v -l`):

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Optional AI features use Anthropic, OpenAI or OpenRouter; language packs and models are downloaded from the project's GitHub releases.
Categories:
  - Translation & Dictionary
  - Reading
License: MIT
SourceCode: https://github.com/kafeg/inkpeek
IssueTracker: https://github.com/kafeg/inkpeek/issues
Changelog: https://github.com/kafeg/inkpeek/blob/main/CHANGELOG.md

AutoName: InkPeek

RepoType: git
Repo: https://github.com/kafeg/inkpeek.git

Builds:
  - versionName: 0.8.NNN
    versionCode: NNN
    commit: vX.Y.NNN
    subdir: app
    submodules: true
    gradle:
      - yes
    gradleprops:
      - inkpeek.updates=false
    rm:
      - app/src/main/cpp/llama/models
      - app/src/main/cpp/llama/examples/llama.android
    scandelete:
      - app/src/main/cpp/bergamot/wasm/module/package.json
      - app/src/main/cpp/bergamot/wasm/module/worker/package.json
      - app/src/main/cpp/fasttext/website/package.json
    ndk: 27.3.13750724

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v\d+\.\d+\.\d+$
UpdateCheckData: '|^v\d+\.\d+\.(\d+)$||^v(.+)$'
CurrentVersion: 0.8.NNN
CurrentVersionCode: NNN
```

Open questions for the first `fdroid build`: CMake 3.22.1 on the build server (`sdkmanager 'cmake;3.22.1'` in
`prebuild` if missing); whether reviewers ask about zstd-jni's prebuilt .so (Maven Central, allowed by the policy).
