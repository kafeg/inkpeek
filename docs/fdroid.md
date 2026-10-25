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

Recipe for fdroiddata (`metadata/io.github.kafeg.inkpeek.yml`, as `fdroid rewritemeta` writes it). On 0.8.200 `lint`,
`checkupdates` and a full `fdroid build` pass (APK: version 200, no REQUEST_INSTALL_PACKAGES, the three native libraries).
Fill the version:

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Optional AI features use Anthropic, OpenAI or OpenRouter; language packs
      and models are downloaded from the project's GitHub releases.
Categories:
  - Reading
  - Translation & Dictionary
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
    rm:
      - app/src/main/cpp/llama/models
      - app/src/main/cpp/llama/examples/llama.android
      - app/src/main/cpp/pcre2/testdata
    scandelete:
      - app/src/main/cpp/bergamot/wasm/module/package.json
      - app/src/main/cpp/bergamot/wasm/module/worker/package.json
      - app/src/main/cpp/fasttext/website/package.json
    ndk: 27.3.13750724
    gradleprops:
      - inkpeek.updates=false

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v\d+\.\d+\.\d+$
UpdateCheckData: '|^v\d+\.\d+\.(\d+)$||^v(.+)$'
CurrentVersion: 0.8.NNN
CurrentVersionCode: NNN
```

Test locally before a merge request (Docker; the image is x86-64, emulated on ARM Macs: ~15 min). In a clone of
https://gitlab.com/fdroid/fdroiddata with the recipe in `metadata/`:

```bash
F="docker run --rm --platform linux/amd64 -v fdroid-sdk:/opt/android-sdk -v $PWD:/repo -w /repo registry.gitlab.com/fdroid/docker-executable-fdroidserver:master"
$F lint io.github.kafeg.inkpeek && $F rewritemeta io.github.kafeg.inkpeek && $F checkupdates --allow-dirty io.github.kafeg.inkpeek
docker run --rm --platform linux/amd64 -v fdroid-sdk:/opt/android-sdk --entrypoint sh registry.gitlab.com/fdroid/docker-executable-fdroidserver:master -c '. /etc/profile.d/bsenv.sh; sdkmanager "ndk;27.3.13750724"'   # once: the NDK into the SDK volume
$F build -v -l io.github.kafeg.inkpeek    # the APK: unsigned/io.github.kafeg.inkpeek_<code>.apk
```

The scanner fails on binaries in a submodule's test data (`rm:` them, as pcre2/testdata); its warnings about PDFs, media
and test models in the submodules do not stop the build.

Open question for review: zstd-jni's prebuilt .so (Maven Central, allowed by the policy) may be asked about.
