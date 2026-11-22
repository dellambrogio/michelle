#!/bin/bash
# Put the Anthropic API key into Michelle on the phone over USB (adb), so it needn't be typed on the phone.
# Michelle opens its settings with the key field focused; the key is typed in and saved. It isn't shown,
# logged or kept anywhere here. The phone must be unlocked.
#   tools/set-key.sh               takes it from .secrets/anthropic_api_key if there (not in git), else asks
#   tools/set-key.sh --clipboard   takes it from the clipboard (copy it first)
# The file holds the key alone (sk-ant-...) or ANTHROPIC_API_KEY=sk-ant-...
#   SERIAL=... tools/set-key.sh    one of several phones (adb devices lists them)
set -e
ADB=${ADB:-$HOME/Android/Sdk/platform-tools/adb}
command -v "$ADB" >/dev/null || ADB=adb
if [[ -n "$SERIAL" ]]; then
    ADB="$ADB -s $SERIAL"
elif (( $($ADB devices | grep -c "device$") > 1 )); then
    echo "More than one phone: SERIAL=... tools/set-key.sh, one of:"; $ADB devices -l | grep "device "; exit 1
fi
$ADB get-state >/dev/null 2>&1 || { echo "No phone: plug it in with USB debugging on."; exit 1; }

FILE="$(dirname "$0")/../.secrets/anthropic_api_key"
if [[ "$1" == "--clipboard" ]]; then
    KEY=$(wl-paste -n 2>/dev/null || xclip -o -selection clipboard 2>/dev/null || true)
elif [[ -f "$FILE" ]]; then
    KEY=$(sed -n 's/^\(export \)\{0,1\}\(ANTHROPIC_API_KEY=\)\{0,1\}["'"'"']\{0,1\}\(sk-ant-[A-Za-z0-9_-]*\).*/\3/p' "$FILE" | head -1)
else
    read -rsp "Anthropic API key (sk-ant-..., not shown): " KEY; echo
fi
KEY=${KEY//[[:space:]]/}
[[ "$KEY" == sk-ant-* ]] || { echo "That doesn't look like a key (sk-ant-...)."; exit 1; }
[[ "$KEY" =~ ^[A-Za-z0-9_-]+$ ]] || { echo "Unexpected characters in the key."; exit 1; }
if $ADB shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "The phone is locked: unlock it and run this again."; exit 1
fi

$ADB shell cmd statusbar collapse   # a pulled-down shade would catch the typing
$ADB shell am force-stop dev.michelle
$ADB shell am start -n dev.michelle/.MainActivity --es open key >/dev/null
sleep 2
$ADB shell input text "$KEY"
unset KEY
$ADB shell input keyevent KEYCODE_ENTER   # the keyboard's Done saves it
echo "Key saved in Michelle (settings shows \"A key is saved\")."
