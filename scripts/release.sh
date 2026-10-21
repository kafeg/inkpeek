#!/bin/bash
# A release, the same way every time: scripts/release.sh [--install]
#   checks (main, clean, pushed, tests, a CHANGELOG.md section for appVersion), dates that section if "unreleased",
#   tags v<appVersion>.<commits>, waits for release.yml, checks the APK's checksum and signing certificate;
#   --install puts it on every connected device (adb install -r keeps the settings).
set -euo pipefail
cd "$(dirname "$0")/.."
CERT=e655a24474da565a2e3b9fb515cd94df44753cca45548baa935bb2dc7f1de1ae   # SHA-256 of the release signing certificate
die() { echo "release: $*" >&2; exit 1; }

[ "$(git branch --show-current)" = main ] || die "not on main"
[ -z "$(git status --porcelain)" ] || die "uncommitted changes"
git fetch -q origin main
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main is not the same as origin/main"
V=$(sed -n 's/^appVersion=//p' gradle.properties)
grep -q "^## $V\b" CHANGELOG.md || die "CHANGELOG.md has no section '## $V'"
./gradlew -q testDebugUnitTest

if grep -q "^## $V — unreleased" CHANGELOG.md; then
  sed -i '' "s/^## $V — unreleased/## $V — $(date +%F)/" CHANGELOG.md
  git commit -qam "CHANGELOG: $V released" && git push -q origin main
fi
TAG="v$V.$(git rev-list --count HEAD)"
git tag "$TAG" && git push -q origin "$TAG"
echo "tagged $TAG"

# The tag's run; GitHub's webhooks sometimes lose a push: then it is started by hand.
run=""
for _ in $(seq 1 12); do
  run=$(gh run list --workflow release.yml --limit 10 --json databaseId,headBranch -q ".[] | select(.headBranch == \"$TAG\") | .databaseId" | head -1)
  [ -n "$run" ] && break; sleep 5
done
if [ -z "$run" ]; then
  gh workflow run release.yml -f tag="$TAG"; sleep 10
  run=$(gh run list --workflow release.yml --limit 5 --json databaseId,headBranch,event -q ".[] | select(.event == \"workflow_dispatch\") | .databaseId" | head -1)
fi
echo "build: run $run"
gh run watch "$run" --exit-status >/dev/null || die "the build failed: gh run view $run --log-failed"

dir=$(mktemp -d)
gh release download "$TAG" -D "$dir"
(cd "$dir" && shasum -a 256 -c "inkpeek-$TAG.apk.sha256" >/dev/null) || die "checksum mismatch"
signer=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner | tail -1)
"$signer" verify --print-certs "$dir/inkpeek-$TAG.apk" | grep -q "SHA-256 digest: $CERT" || die "signed with another key"
[ "$(gh api repos/kafeg/inkpeek/releases/latest -q .tag_name)" = "$TAG" ] || die "$TAG is not the latest release"
echo "released $TAG: https://github.com/kafeg/inkpeek/releases/tag/$TAG"

if [ "${1:-}" = "--install" ]; then
  for d in $(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
    echo "$d: $(adb -s "$d" install -r "$dir/inkpeek-$TAG.apk" | tail -1)"
  done
fi
rm -rf "$dir"
