#!/usr/bin/env bash
# Records the 40 second demo of the projection box on this project's emulator.
#   bash tools/record_demo.sh [out.mp4]
# Needs a debug build installed. Drives the scene with the debug broadcasts, so no taps are involved.
set -euo pipefail
DEV="${DEV:-emulator-5558}"
PKG=io.embodit.threeviews
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/../docs/media/ThreeViews-demo.mp4}"
mkdir -p "$(dirname "$OUT")"

cmd() { adb -s "$DEV" shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd $1 ${2:+--es a $2}" >/dev/null; }

adb -s "$DEV" shell am force-stop "$PKG"
adb -s "$DEV" shell am start -n "$PKG/.platform.LaunchActivity" >/dev/null
echo "waiting for the part library to warm up"; sleep 100
bash "$HERE/harness/tv.sh" fast on >/dev/null; sleep 4   # 60 fps instead of the idle 2-4 fps

# Remote paths go inside one quoted string or start with //, otherwise Git Bash rewrites them.
adb -s "$DEV" shell "rm -f /sdcard/threeviews-demo.mp4"
adb -s "$DEV" shell "screenrecord --size 1080x1080 --bit-rate 6000000 --time-limit 44 /sdcard/threeviews-demo.mp4" &
REC=$!
# The emulator drops to a few frames per second when its window is idle; keep nudging it.
( for i in 1 2 3; do sleep 12; bash "$HERE/harness/tv.sh" fast on >/dev/null 2>&1; done ) &
sleep 3
cmd select plate_hole;   sleep 4
cmd select bearing_seat; sleep 4
cmd focus FRONT; sleep 2
cmd focus TOP;   sleep 2
cmd focus LEFT;  sleep 2
cmd focus ALL;   sleep 1.5
cmd yaw 70;  sleep 2.5
cmd yaw -50; sleep 2.5
cmd reset;   sleep 2
cmd unfold;  sleep 4
cmd select notched_tube; sleep 3.5
cmd fold;    sleep 3.5
cmd select v_block; sleep 3
wait "$REC" || true
sleep 1
adb -s "$DEV" pull //sdcard/threeviews-demo.mp4 "$OUT" >/dev/null
adb -s "$DEV" shell "rm -f /sdcard/threeviews-demo.mp4"
ls -la "$OUT"
