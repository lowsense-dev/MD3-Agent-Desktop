#!/bin/sh
# Builds dist/md3-agent: the uber jar, a jlinked Java runtime (en and ru locale data) and a launcher script.
# Every package format (deb, rpm, AppImage) is made from this folder.
set -eu
cd "$(dirname "$0")/.."
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
OUT=dist/md3-agent

./gradlew -q :app:packageUberJarForCurrentOS
rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/lib" "$OUT/share/applications"

"$JAVA_HOME/bin/jlink" \
    --add-modules java.base,java.desktop,java.datatransfer,java.instrument,java.logging,java.management,java.naming,java.prefs,java.sql,java.xml,jdk.charsets,jdk.crypto.ec,jdk.httpserver,jdk.localedata,jdk.security.auth,jdk.unsupported,jdk.zipfs \
    --include-locales=en,ru \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
    --output "$OUT/runtime"

cp "app/build/compose/jars/md3-agent-linux-x64-$VERSION.jar" "$OUT/lib/md3-agent.jar"
cat > "$OUT/bin/md3-agent" <<'LAUNCHER'
#!/bin/sh
# Resolves its own folder so it works from /opt, an AppImage mount or a symlink in /usr/bin.
HERE=$(dirname "$(readlink -f "$0")")
exec "$HERE/../runtime/bin/java" -Dmd3agent.launcher=md3-agent -Dawt.useSystemAAFontSettings=on -jar "$HERE/../lib/md3-agent.jar" "$@"
LAUNCHER
chmod +x "$OUT/bin/md3-agent"
cp packaging/md3-agent.desktop "$OUT/share/applications/"
for size in 48 128 256 512; do
    mkdir -p "$OUT/share/icons/hicolor/${size}x${size}/apps"
    cp "packaging/icons/md3-agent-$size.png" "$OUT/share/icons/hicolor/${size}x${size}/apps/md3-agent.png"
done
cp LICENSE "$OUT/"
echo "$OUT ($VERSION) $(du -sh "$OUT" | cut -f1)"
