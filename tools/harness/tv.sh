#!/usr/bin/env bash
# ThreeViews wrapper around emu.sh: pinned to this project's emulator instance.
#   tools/harness/tv.sh shot NAME | cmd NAME [a] [s] | click X Y | drag ... | waitlog REGEX | log | info
export DEV="${DEV:-emulator-5558}"
export AVD="${AVD:-PICO_6.1_Views}"
export PKG="${PKG:-io.embodit.threeviews}"
export TAG="${TAG:-ThreeViews}"
HERE="$(cd "$(dirname "$0")" && pwd)"
export SHOTS="${SHOTS:-$(cd "$HERE/../.." && pwd)/.scratch/shots}"
exec bash "$HERE/emu.sh" "$@"
