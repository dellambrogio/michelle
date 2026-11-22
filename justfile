# Michelle — photos of meals to calories, on the phone.

adb     := env("HOME") / "Android/Sdk/platform-tools/adb"
app     := "dev.michelle"
debug   := "app/build/outputs/apk/debug/app-debug.apk"
backups := ".backup"

# list recipes
default:
    @just --list

# debug APK
build:
    ./gradlew assembleDebug

# unit tests
test:
    ./gradlew testDebugUnitTest

# release APK (signed if MICHELLE_KEYSTORE is set in ~/.gradle/gradle.properties, else unsigned)
release:
    ./gradlew assembleRelease
    @ls app/build/outputs/apk/release/*.apk

# copy the app's data off the phone (meals, photos, settings) into .backup/, and check it opens
backup:
    #!/usr/bin/env bash
    set -euo pipefail
    mkdir -p {{backups}} && chmod 700 {{backups}}
    b={{backups}}/michelle-$(date +%Y%m%d-%H%M%S).tar
    {{adb}} exec-out run-as {{app}} tar cf - databases files shared_prefs > "$b"
    chmod 600 "$b"
    t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
    tar xf "$b" -C "$t" databases
    python3 -I -c 'import sqlite3, sys; c = sqlite3.connect(sys.argv[1]); print(sys.argv[2] + ":", c.execute("select count(*) from meal").fetchone()[0], "meals,", c.execute("pragma integrity_check").fetchone()[0])' "$t/databases/michelle.db" "$b"

# build, back up the phone's data, then update the app keeping it (never uninstalls)
install: build backup
    {{adb}} install -r {{debug}}

# start Michelle on the phone
run:
    {{adb}} shell am start -n {{app}}/.MainActivity

# Michelle's log: timings, Claude's answers, Health Connect (Ctrl-C to stop)
log:
    {{adb}} logcat -s michelle:*

# put the Anthropic API key on the phone (from .secrets/anthropic_api_key, or asks)
key *args:
    tools/set-key.sh {{args}}

# analyze a photo without the camera (debug builds); delete the test meal afterwards
analyze photo:
    {{adb}} push "{{photo}}" /data/local/tmp/michelle-test.jpg
    {{adb}} shell chmod 644 /data/local/tmp/michelle-test.jpg
    {{adb}} shell run-as {{app}} sh -c "'mkdir -p cache/capture && cp /data/local/tmp/michelle-test.jpg cache/capture/test.jpg'"
    {{adb}} shell rm /data/local/tmp/michelle-test.jpg
    {{adb}} shell am start -n {{app}}/.MainActivity --es photo /data/user/0/{{app}}/cache/capture/test.jpg
    @echo "watch it with: just log"

# put a backup back on the phone, replacing what's there now (takes a backup of that first)
[confirm("Replace Michelle's data on the phone with this backup? (a backup of the current data is taken first) [y/N]")]
restore file: backup
    {{adb}} shell am force-stop {{app}}
    {{adb}} exec-in run-as {{app}} tar xf - < "{{file}}"
    @echo "restored {{file}}; start it with: just run"

# README screenshots into docs/, from the running emulator with made-up data (never the phone)
screenshots:
    tools/screenshots.sh
