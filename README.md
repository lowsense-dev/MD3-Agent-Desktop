# MD3 Agent Desktop

The desktop version (Linux) of [MD3 Agent](https://github.com/huecoder/MD3-Agent): an AI assistant with tools, MCP servers and
long-term memory in a Material 3 Expressive interface. It runs on any Linux distribution, on X11 and Wayland, in any
desktop (GNOME, KDE, XFCE, Cinnamon, Hyprland, Sway and others).

## What it does

- Chat with any OpenAI-compatible endpoint, Anthropic or Gemini; presets for OpenAI, Anthropic, Google, DeepSeek and
  OpenRouter; several keys per provider, rotated.
- Agents with their own prompt, look and voice; session compaction with a context meter; usage statistics.
- Tools, each Off, Ask or Auto:
  - web search and reading pages (DuckDuckGo, SearXNG, Brave, Jina, Firecrawl), weather;
  - `run_command` and `run_python` in **System** mode (your own bash and python3) or **Sandbox** mode
    (bubblewrap: only the agent's workspace is visible, network by switch); Python uses the agent's own venv, so
    `pip install` works;
  - files in the workspace or your home folder, showing files and pictures in the chat;
  - reminders, alarms and timers: desktop notifications, and a window with a chime, Stop and Snooze;
  - memory, other chats, charts, diagrams, quick replies, questions;
  - any number of MCP servers (HTTP with OAuth through a loopback redirect, or stdio).
- Attach files with the system file picker, by dragging them onto the chat, or by pasting files and pictures with Ctrl+V.
- Dictation (any Whisper-compatible provider) and spoken replies (provider voices, or speech-dispatcher).
- A quick-question window over everything (`md3-agent --overlay`, or a global shortcut where the desktop supports
  the GlobalShortcuts portal).
- Import from the phone: on Android, Settings → Export for PC; here, Settings → Import from phone.

Not included: the camera and controlling the screen or other apps, which the phone version has.

## Install

| Distribution | Package | How |
|---|---|---|
| Debian, Ubuntu, Mint, Pop!_OS | `md3-agent_<version>_amd64.deb` | `sudo apt install ./md3-agent_*.deb` |
| Fedora, openSUSE | `md3-agent-<version>.x86_64.rpm` | `sudo dnf install ./md3-agent-*.rpm` / `sudo zypper install ./md3-agent-*.rpm` |
| Arch and derivatives | `PKGBUILD` in this repository | `makepkg -si` |
| Anything else with glibc | `MD3_Agent-<version>-x86_64.AppImage` | `chmod +x` and run |

The deb, rpm and AppImage bring their own Java runtime. The Arch package uses the system Java (21 or newer).
How the packages are built is in [packaging/README.md](packaging/README.md).

Optional pieces turn on features when present: `bubblewrap` (sandbox), `python3` and on Debian/Ubuntu `python3-venv`,
`wl-clipboard` or `xclip` (pasting files and pictures), `xdg-desktop-portal` (file picker), a Secret Service keyring
(gnome-keyring, KWallet 6, KeePassXC) for API keys, `speech-dispatcher` (system voice). Run
`md3-agent --check-environment` to see what was found and how to install the rest; Settings → Access shows the same.

## Command line

| Flag | |
|---|---|
| `--overlay` | Open the quick-question window |
| `--chat <id>` | Open a chat |
| `--reminder <id>` | Fire a reminder (used by the systemd timers) |
| `--check-environment` | Print the optional components found and exit |
| `--version` | Print the version and exit |

A second launch passes its flag to the running window instead of starting another copy.

## Where things are kept

- `$XDG_DATA_HOME/md3-agent` (`~/.local/share/md3-agent`): chats database, attachments, the agent's workspace and venv.
- `$XDG_CONFIG_HOME/md3-agent` (`~/.config/md3-agent`): settings. API keys and tokens in them are encrypted with a key
  kept in the desktop keyring, or, without one, in `key` there (readable only by you).
- `$XDG_STATE_HOME/md3-agent/log.txt`: the log.
- Reminders that must fire while the app is closed are systemd user timers named `md3-agent-reminder-*`.
  Without systemd they fire while the app runs, and missed ones show at the next start.

## Keyboard

Enter sends, Shift+Enter starts a new line, Ctrl+V pastes text, files or pictures, Ctrl+N starts a chat, Ctrl+K
searches, Ctrl+, opens settings, Escape (or the mouse's back button) goes back.

## Known limits

- The window is drawn through XWayland on Wayland (standard OpenJDK has no native Wayland toolkit yet).
- x86_64 only for the packages; other architectures build from source.
- No Flatpak or Snap: their sandbox would take away the System shell and the system Python.
- The AppImage needs glibc; on musl distributions (Alpine) build from source with a system Java.

## Build

JDK 21 is needed. `./gradlew run` starts the app, `./gradlew :app:test` runs the tests (screens are rendered off-screen
to `app/build/screens`, nothing opens on the desktop), `packaging/build-dist.sh` builds the runnable folder.

## License

GPL-3.0, like the Android app it is ported from.
