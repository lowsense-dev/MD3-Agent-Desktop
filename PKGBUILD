# Maintainer: lowsense-dev <128370358+lowsense-dev@users.noreply.github.com>
# Local package of this checkout: run makepkg -si in the repository root.
pkgname=md3-agent
pkgver=1.0.0
pkgrel=1
pkgdesc="AI assistant with tools, MCP and long-term memory"
arch=('x86_64')
url="https://github.com/lowsense-dev/MD3-Agent"
license=('GPL-3.0-only')
depends=('java-runtime>=21' 'libxtst' 'libxi' 'alsa-lib' 'fontconfig')
makedepends=('jdk21-openjdk')
optdepends=('bubblewrap: sandbox mode for commands and Python'
            'python: run_python tool'
            'wl-clipboard: paste files and pictures on Wayland'
            'xclip: paste files and pictures on X11'
            'xdg-desktop-portal: the desktop file picker'
            'gnome-keyring: keep API keys in the keyring'
            'speech-dispatcher: system voice when no voice provider is set')

build() {
    cd "$startdir"
    ./gradlew --no-configuration-cache -q :app:packageUberJarForCurrentOS
}

package() {
    cd "$startdir"
    install -Dm644 "app/build/compose/jars/md3-agent-linux-x64-$pkgver.jar" "$pkgdir/usr/share/md3-agent/md3-agent.jar"
    install -Dm755 packaging/md3-agent.sh "$pkgdir/usr/bin/md3-agent"
    install -Dm644 packaging/md3-agent.desktop "$pkgdir/usr/share/applications/md3-agent.desktop"
    for size in 48 128 256 512; do
        install -Dm644 "packaging/icons/md3-agent-$size.png" "$pkgdir/usr/share/icons/hicolor/${size}x${size}/apps/md3-agent.png"
    done
    install -Dm644 LICENSE "$pkgdir/usr/share/licenses/$pkgname/LICENSE"
}
