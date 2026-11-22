#!/usr/bin/env bash
# README screenshots from an emulator, with made-up data: builds the demo app (dev.michelle.demo, -Pdemo),
# installs it on the running emulator, seeds it (DemoSeedActivity), and captures Today, a meal and the Plan
# into docs/. Only ever talks to the emulator: the phone and its real data are never touched.
# Start the emulator first, e.g.: android emulator create medium_phone && android emulator start medium_phone
set -euo pipefail
cd "$(dirname "$0")/.."
adb="$HOME/Android/Sdk/platform-tools/adb"
emu=$("$adb" devices | awk '/^emulator-/ && $2 == "device" {print $1; exit}')
[ -n "$emu" ] || { echo "no emulator running" >&2; exit 1; }
a() { "$adb" -s "$emu" "$@"; }
app=dev.michelle.demo

./gradlew -q assembleDebug -Pdemo
apk=$(mktemp --suffix=.apk); trap 'rm -f "$apk"' EXIT
cp app/build/outputs/apk/debug/app-debug.apk "$apk"
./gradlew -q assembleDebug   # put the normal debug APK back, so `just install` can't pick up the demo one

a install -r "$apk" >/dev/null
a shell pm clear $app >/dev/null
for p in READ_WEIGHT WRITE_WEIGHT READ_HEIGHT WRITE_NUTRITION READ_HEALTH_DATA_HISTORY; do
    a shell pm grant $app android.permission.health.$p
done

# two food photos, CC0 from Wikimedia Commons
photos=$(mktemp -d); trap 'rm -f "$apk"; rm -rf "$photos"' EXIT
commons=https://upload.wikimedia.org/wikipedia/commons/thumb
curl -fsSL -o "$photos/breakfast.jpg" "$commons/0/0b/Yogurt_with_raisins_and_banana_-_Massachusetts.jpg/960px-Yogurt_with_raisins_and_banana_-_Massachusetts.jpg"
curl -fsSL -o "$photos/lunch.jpg" "$commons/6/61/Pesto_Pasta_%28Unsplash%29.jpg/960px-Pesto_Pasta_%28Unsplash%29.jpg"
a shell run-as $app mkdir -p files/demo
for f in breakfast lunch; do
    a push "$photos/$f.jpg" /data/local/tmp/demo-$f.jpg >/dev/null
    a shell chmod 644 /data/local/tmp/demo-$f.jpg
    a shell run-as $app cp /data/local/tmp/demo-$f.jpg files/demo/$f.jpg
    a shell rm /data/local/tmp/demo-$f.jpg
done
a shell am start -W -n $app/dev.michelle.DemoSeedActivity >/dev/null
sleep 6

# a tidy status bar
a shell settings put global sysui_demo_allowed 1
demo() { a shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 1630; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide; demo notifications -e visible false

shot() {
    sleep 3; a exec-out screencap -p > "docs/$1.png"
    python3 -I -c 'import sys; from PIL import Image; Image.open(sys.argv[1]).convert("RGB").resize((540, 1200), Image.LANCZOS).save(sys.argv[1], optimize=True)' "docs/$1.png"
    echo "docs/$1.png"
}
a shell am start -W -n $app/dev.michelle.MainActivity >/dev/null
shot today
a shell input tap 540 1080          # the second meal
shot meal
a shell input keyevent 4; sleep 2
a shell input tap 880 148           # Plan
sleep 3; shot plan
demo exit
