#!/bin/sh
# Language packs on a connected device through the app's own code: headers, meanings, language detection, offline
# translation (cases: app/src/androidTest/assets/cases.tsv). Needs keystore.properties and the packs installed in the app.
set -e
cd "$(dirname "$0")/.."
./gradlew -q assembleRelease assembleReleaseAndroidTest
adb install -r app/build/outputs/apk/release/app-release.apk >/dev/null
adb install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk >/dev/null
adb logcat -c
out=$(adb shell am instrument -w io.github.kafeg.inkpeek.test/androidx.test.runner.AndroidJUnitRunner)
echo "$out" | tail -3
adb logcat -d -s InkPeekTest:I | sed 's/^.*InkPeekTest: //' | grep -v '^PASS' || true
# am instrument exits 0 on failed tests: its summary says.
echo "$out" | grep -q '^OK ('
