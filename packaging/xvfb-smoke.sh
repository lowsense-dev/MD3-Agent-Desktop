#!/bin/sh
# Starts the built app in an invisible X server with throwaway data folders and no session bus, saves a screenshot to
# dist/smoke.png and prints the log: a real start of the real binary without a window on anyone's desktop.
# Needs xorg-server-xvfb (xvfb-run), xwd and ImageMagick.
set -eu
cd "$(dirname "$0")/.."
BIN=$(readlink -f "${1:-dist/md3-agent/bin/md3-agent}")
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -m 700 "$TMP/run"
env -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS XDG_DATA_HOME="$TMP/data" XDG_CONFIG_HOME="$TMP/config" XDG_STATE_HOME="$TMP/state" \
    XDG_CACHE_HOME="$TMP/cache" XDG_RUNTIME_DIR="$TMP/run" \
    xvfb-run -a -s "-screen 0 1280x800x24" sh -c "timeout 25 '$BIN' & PID=\$!; sleep 18; xwd -root -silent > '$TMP/shot.xwd'; wait \$PID; echo exit=\$?" > "$TMP/log.txt" 2>&1 || true
magick "$TMP/shot.xwd" dist/smoke.png
cat "$TMP/log.txt"
# timeout ends a healthy app with 124; anything else means it quit or crashed on its own.
grep -q "exit=124" "$TMP/log.txt"
