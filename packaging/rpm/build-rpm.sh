#!/bin/sh
# Builds the rpm from dist/md3-agent. Needs rpmbuild (rpm-build on Fedora, rpm-tools on Arch).
set -eu
cd "$(dirname "$0")/../.."
if ! command -v rpmbuild >/dev/null; then
    echo "rpmbuild is not installed (rpm-build on Fedora/openSUSE, rpm-tools on Arch)." >&2
    exit 2
fi
[ -d dist/md3-agent ] || packaging/build-dist.sh
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
TOP=$(mktemp -d)
trap 'rm -rf "$TOP"' EXIT
rpmbuild -bb --define "_topdir $TOP" --define "version $VERSION" --define "dist_dir $PWD/dist/md3-agent" packaging/rpm/md3-agent.spec
cp "$TOP"/RPMS/x86_64/md3-agent-*.rpm dist/
ls dist/*.rpm
