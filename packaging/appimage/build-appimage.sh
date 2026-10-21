#!/bin/sh
# Builds MD3_Agent-<version>-x86_64.AppImage from dist/md3-agent. Needs appimagetool on PATH; this script never
# downloads anything (get appimagetool from https://github.com/AppImage/appimagetool/releases).
set -eu
cd "$(dirname "$0")/../.."
if ! command -v appimagetool >/dev/null; then
    echo "appimagetool is not installed. Download it from https://github.com/AppImage/appimagetool/releases, make it executable, put it on PATH and run this again." >&2
    exit 2
fi
[ -d dist/md3-agent ] || packaging/build-dist.sh
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
APPDIR=$(mktemp -d)/MD3_Agent.AppDir
trap 'rm -rf "$(dirname "$APPDIR")"' EXIT
mkdir -p "$APPDIR/usr"
cp -a dist/md3-agent/. "$APPDIR/usr/"
cp packaging/md3-agent.desktop "$APPDIR/md3-agent.desktop"
cp packaging/icons/md3-agent-256.png "$APPDIR/md3-agent.png"
cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/sh
exec "$(dirname "$(readlink -f "$0")")/usr/bin/md3-agent" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"
ARCH=x86_64 appimagetool "$APPDIR" "dist/MD3_Agent-$VERSION-x86_64.AppImage"
