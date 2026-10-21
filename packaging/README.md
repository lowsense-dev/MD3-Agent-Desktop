# Packaging

Everything is built from `dist/md3-agent`, made by `packaging/build-dist.sh`:
the uber jar, a jlinked Java runtime (English and Russian locale data) and a `bin/md3-agent` launcher.
The jpackage launcher is not used: some JDK 21 builds (Arch's among them) start the JVM with empty arguments from it
and crash.

| Format | Command | Needs | Installs to |
|---|---|---|---|
| Folder | `packaging/build-dist.sh` | JDK 21 | `dist/md3-agent` |
| `.deb` | `packaging/deb/build-deb.sh` | `ar`, `tar`, `xz` (no dpkg) | `/opt/md3-agent`, `/usr/bin/md3-agent` |
| `.rpm` | `packaging/rpm/build-rpm.sh` | `rpmbuild` | `/opt/md3-agent`, `/usr/bin/md3-agent` |
| AppImage | `packaging/appimage/build-appimage.sh` | `appimagetool` on PATH | single file |
| Arch | `makepkg -si` in the repository root | `jdk21-openjdk` to build | jar in `/usr/share/md3-agent`, system Java |

The deb, rpm and AppImage carry their own Java runtime; the Arch package uses the system `java-runtime>=21`.
Builds are x86_64 only (the Compose/Skia native library in the jar is built for the current platform).

Optional runtime pieces, listed as Recommends/Suggests/optdepends: `bubblewrap` (sandbox), `python3` (+ `python3-venv`
on Debian/Ubuntu), `wl-clipboard` or `xclip` (pasting files and pictures), `xdg-desktop-portal` (file picker),
a Secret Service keyring (`gnome-keyring`, KWallet 6, KeePassXC), `speech-dispatcher` (system voice).
`md3-agent --check-environment` prints what was found and the install command for what is missing.
