#!/usr/bin/env bash
# Takes store screenshots of the debug build on a running emulator. Usage: take.sh <apk> <out dir>
set -u
apk=$1; out=$2; mkdir -p "$out"
pkg=com.vannam.photo; act=$pkg/app.lumora.editor.MainActivity
adb install -r "$apk"
# No system error dialogs (a slow emulator makes the launcher look unresponsive).
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
# Clean status bar: fixed clock, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
start() { adb shell am force-stop $pkg; adb shell am start -n $act --es demo "$1" >/dev/null; }
cap() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; adb exec-out screencap -p > "$out/$1.png"; echo "took $1 ($(stat -c %s "$out/$1.png") bytes)"; }
shot() { start "$2"; sleep "$3"; cap "$1"; }
# Warm-up run so first-launch work (dex, shaders) doesn't land in a screenshot.
start home; sleep 30
shot home home 30
shot intro intro 15
shot editor editor 30
shot ai ai 10
shot collage collage 25
shot background background 150
shot restore restore 300
start studio; sleep 25
adb shell input swipe 800 1100 420 1100 1500; sleep 4; cap studio
start panorama; sleep 25
adb shell input swipe 800 1100 420 1100 1500; sleep 4; cap panorama
