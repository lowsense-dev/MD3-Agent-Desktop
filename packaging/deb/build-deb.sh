#!/bin/sh
# Builds md3-agent_<version>_amd64.deb from dist/md3-agent with plain ar and tar, so it works on any distribution
# (dpkg-deb is not needed). The app goes to /opt/md3-agent with a launcher symlink in /usr/bin.
set -eu
cd "$(dirname "$0")/../.."
[ -d dist/md3-agent ] || packaging/build-dist.sh
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
ROOT="$WORK/root"
mkdir -p "$ROOT/opt" "$ROOT/usr/bin" "$ROOT/usr/share" "$WORK/control"
cp -a dist/md3-agent "$ROOT/opt/md3-agent"
mv "$ROOT/opt/md3-agent/share/applications" "$ROOT/usr/share/"
mv "$ROOT/opt/md3-agent/share/icons" "$ROOT/usr/share/"
rmdir "$ROOT/opt/md3-agent/share"
ln -s ../../opt/md3-agent/bin/md3-agent "$ROOT/usr/bin/md3-agent"
mkdir -p "$ROOT/usr/share/doc/md3-agent"
cp LICENSE "$ROOT/usr/share/doc/md3-agent/copyright"

SIZE=$(du -sk "$ROOT" | cut -f1)
cat > "$WORK/control/control" <<CONTROL
Package: md3-agent
Version: $VERSION
Architecture: amd64
Maintainer: lowsense-dev <128370358+lowsense-dev@users.noreply.github.com>
Installed-Size: $SIZE
Section: utils
Priority: optional
Depends: libc6, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libfreetype6, libfontconfig1, libasound2 | libasound2t64
Recommends: bubblewrap, python3, python3-venv, wl-clipboard, xclip, xdg-desktop-portal
Suggests: speech-dispatcher, gnome-keyring
Homepage: https://github.com/lowsense-dev/MD3-Agent-Desktop
Description: AI assistant with tools, MCP and long-term memory
 MD3 Agent Desktop: chat with any OpenAI-compatible, Anthropic or Gemini
 model, with web search, files, shell and Python tools, reminders, MCP servers
 and long-term memory, in a Material 3 Expressive interface.
CONTROL
cat > "$WORK/control/postinst" <<'POSTINST'
#!/bin/sh
set -e
command -v update-desktop-database >/dev/null && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null && gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
POSTINST
chmod 755 "$WORK/control/postinst"
(cd "$ROOT" && find . -type f ! -path ./DEBIAN/\* -printf '%P\n' | sort | xargs -d '\n' md5sum) > "$WORK/control/md5sums"

echo "2.0" > "$WORK/debian-binary"
tar --owner=0 --group=0 --numeric-owner -C "$WORK/control" -cJf "$WORK/control.tar.xz" .
tar --owner=0 --group=0 --numeric-owner -C "$ROOT" -cJf "$WORK/data.tar.xz" .
OUT="dist/md3-agent_${VERSION}_amd64.deb"
rm -f "$OUT"
(cd "$WORK" && ar rcD "$OLDPWD/$OUT" debian-binary control.tar.xz data.tar.xz)
echo "$OUT"
