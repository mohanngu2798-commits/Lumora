#!/usr/bin/env bash
# Takes store screenshots of the debug build on a running emulator. Usage: take.sh <apk> <out dir>
set -u
apk=$1; out=$2; mkdir -p "$out"
pkg=com.vannam.photo; act=$pkg/app.lumora.editor.MainActivity
adb install -r "$apk"
# Clean status bar: fixed clock, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
shot() { # name demo wait
  adb shell am force-stop $pkg
  adb shell am start -n $act --es demo "$2" >/dev/null
  sleep "$3"
  adb exec-out screencap -p > "$out/$1.png"
  echo "took $1 ($(stat -c %s "$out/$1.png") bytes)"
}
shot 1-home home 25
shot 2-editor editor 20
shot 3-background background 120
shot 4-ai ai 6
shot 5-studio studio 20
shot 6-collage collage 15
shot 7-intro intro 8
