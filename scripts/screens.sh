#!/bin/bash
# Screenshots of every settings tab on each connected device, page by page (layout checks, README pictures).
#   scripts/screens.sh [out dir]        — in the app's current language; set it in Card → Main first
set -eu
OUT=${1:-build/screens}; PKG=io.github.kafeg.inkpeek
TABS="lang:packs lang:other lang:help ai:main ai:prompts ai:help card:main card:look card:icons words:word words:phrase words:history"
mkdir -p "$OUT"
shot() { adb -s "$1" shell screencap -p /sdcard/inkpeek-shot.png && adb -s "$1" pull -q /sdcard/inkpeek-shot.png "$2" && adb -s "$1" shell rm /sdcard/inkpeek-shot.png; }
for d in $(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
  size=$(adb -s "$d" shell wm size | awk '{ print $NF }' | tail -1); w=${size%x*}; h=${size#*x}
  for t in $TABS; do
    adb -s "$d" shell input keyevent KEYCODE_WAKEUP
    adb -s "$d" shell am start -S -n "$PKG/.ui.MainActivity" --es open "$t" >/dev/null; sleep 4
    prev=""
    for page in 1 2 3 4; do
      f="$OUT/$d-${t/:/-}-$page.png"; shot "$d" "$f"
      sum=$(shasum "$f" | cut -d' ' -f1); if [ "$sum" = "$prev" ]; then rm "$f"; break; fi; prev=$sum
      adb -s "$d" shell input swipe $((w / 2)) $((h * 8 / 10)) $((w / 2)) $((h * 3 / 10)) 400; sleep 2
    done
  done
  echo "$d: $(ls "$OUT"/"$d"-*.png | wc -l | tr -d ' ') screenshots"
done
echo "in $OUT"
