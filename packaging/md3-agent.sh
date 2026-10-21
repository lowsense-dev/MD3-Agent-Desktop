#!/bin/sh
# Launcher for the jar install (PKGBUILD): system Java 21+, same flags as the bundled-runtime builds.
exec java -Dmd3agent.launcher=md3-agent -Dawt.useSystemAAFontSettings=on -jar /usr/share/md3-agent/md3-agent.jar "$@"
