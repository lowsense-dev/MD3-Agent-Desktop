# MD3 Agent for Linux Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port MD3 Agent (Android) to a Compose Multiplatform Desktop app that runs on any Linux distribution, with all tools except camera and screen control.

**Architecture:** One Gradle module `app` (Kotlin/JVM). Code is copied from `~/Projects/MD3-Agent/app/src/main/java/dev/huecoder/md3agent` keeping package names. Android-only parts are replaced by Linux adapters in `system/`, each with an availability probe and a fallback. Android string resources stay as XML and are read through a generated `R` object and a small runtime loader, so the ~900 `R.string.*` call sites keep working.

**Tech Stack:**
- Kotlin 2.4.20, JDK 21.
- Compose Multiplatform 1.13.0-alpha02 with material3 `org.jetbrains.compose.material3:material3:1.13.0-alpha02`.
- Room 2.8.5 (JVM) + `androidx.sqlite:sqlite-bundled-jvm:2.7.1`, DataStore preferences-core 1.2.1.
- JetBrains navigation-compose 2.10.0-rc01 and lifecycle-viewmodel-compose 2.11.0.
- OkHttp 5.5.0, kotlinx.serialization 1.11.0, coroutines 1.11.0 (`-swing`).
- Coil 3.6.3, multiplatform-markdown-renderer 0.46.0, material-kolor 5.0.1, graphics-shapes-desktop 1.1.0, dyn4j 6.0.0, `org.scilab.forge:jlatexmath:1.0.7`.
- dbus-java 5.2.2 (core + native-unixsocket).

**Spec:** `docs/superpowers/specs/2026-10-21-md3-agent-linux-design.md`

## Global Constraints

- Distro-, DE- and init-agnostic. No Hyprland, GNOME or systemd assumptions without a fallback.
- Runs on X11 and Wayland (XWayland).
- Out of scope: camera, `phone_screen`, `phone_action`, `phone_screenshot`, `read_screen`, accessibility, Shizuku, Termux, root, wake word, tray, Flatpak, Snap, sync.
- Data in `$XDG_DATA_HOME/md3-agent/` (default `~/.local/share/md3-agent/`). Config in `$XDG_CONFIG_HOME/md3-agent/` (default `~/.config/md3-agent/`).
- Single-instance socket at `$XDG_RUNTIME_DIR/md3-agent.sock`, fallback `/tmp/md3-agent-$UID.sock`.
- CLI flags: `--overlay`, `--chat <id>`, `--reminder <id>`, `--version`, `--check-environment`.
- Export file: `"format": 1`, PBKDF2-HMAC-SHA256 600 000 iterations + AES-256-GCM for keys and tokens.
- `run_command` and `run_python` default to Ask. `run_command` has modes System (`bash -lc`, else `sh -lc`) and Sandbox (`bwrap`).
- A missing system component never crashes the app: a fallback runs, or the tool is disabled with a hint naming the package for the detected distro.
- Packages: `.deb` and `.rpm` (jpackage, bundled jlinked JRE), AppImage (bundled JRE), PKGBUILD (`depends=(java-runtime>=21)`).
- UI copy is in English and Russian, same keys as the Android app.
- Commits are local only, author `lowsense-dev <128370358+lowsense-dev@users.noreply.github.com>`, passed per commit with `git -c`.
- UI is verified by headless rendering to PNG (`ImageComposeScene`). Never open windows on the user's desktop during development.

## Review Focus

1. **Paths with spaces or non-ASCII names** (attachments, `~/Документы/x y.pdf`, `file://` URIs with `%20`). They must import, paste and pass to `bwrap` intact. Test in Task 6 (`ClipboardParserTest.decodesPercentEncodedUri`) and Task 5 (`SandboxArgsTest.keepsPathsWithSpaces`).
2. **Second launch while the app is running** (desktop file click, hotkey, systemd reminder). It must reach the running window, not open a second DB writer. Test in Task 7 (`InstanceSocketTest.secondLaunchForwardsArguments`). A stale socket file left after a crash must not block start: `InstanceSocketTest.staleSocketIsReplaced`.
3. **Reminder set for the past or while the app is closed and systemd is missing.** It is shown once on next start, marked missed, never repeated. Test in Task 8 (`ReminderSchedulerTest.missedReminderFiresOnceOnStart`).
4. **Wrong password or truncated export file.** Clear error, nothing written. Test in Task 14 (`ExportCryptoTest.wrongPasswordFailsWithoutPartialImport`).
5. **Long-running or output-flooding command or script** (`yes`, infinite loop). Killed at timeout with the whole process tree, output capped, UI stays responsive. Test in Task 5 (`ProcessRunnerTest.killsTreeOnTimeout`, `ProcessRunnerTest.capsOutput`).

---

### Task 1: Project scaffold, R generation, headless render

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, Gradle wrapper (copy from MD3-Agent), `.gitignore`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/dev/huecoder/md3agent/Main.kt`
- Create: `app/src/main/kotlin/dev/huecoder/md3agent/ui/text/Strings.kt`
- Create: `app/src/main/resources/strings/values/strings.xml`, `.../values-ru/strings.xml` (copied)
- Test: `app/src/test/kotlin/dev/huecoder/md3agent/ui/text/StringsTest.kt`, `app/src/test/kotlin/dev/huecoder/md3agent/RenderSmokeTest.kt`

**Interfaces:**
- Produces: generated `dev.huecoder.md3agent.R` with `object string { const val name: Int }`, `object plurals`. Generated by Gradle task `generateR` from `src/main/resources/strings/values/strings.xml` into `build/generated/r/`, wired before `compileKotlin`.
- Produces: `object Strings { fun get(id: Int, vararg args: Any): String; fun plural(id: Int, count: Int, vararg args: Any): String; var locale: Locale }`.
  - Lazy-loads both XML files.
  - Unescapes Android escapes (`\'`, `\"`, `\n`, `\t`, `\@`, `\?`, `\\`, surrounding quotes).
  - Formats with `String.format(locale, …)`.
  - Plural rules: en (`one` for 1, else `other`), ru (one/few/many/other by CLDR integer rules).
- Produces: `@Composable fun stringResource(id: Int, vararg args: Any): String` and `@Composable fun pluralStringResource(id: Int, count: Int, vararg args: Any): String` in `dev.huecoder.md3agent.ui.text`.

- [ ] **Step 1:** Gradle build. Plugins: kotlin-jvm, kotlin compose, `org.jetbrains.compose` 1.13.0-alpha02, kotlin serialization, KSP 2.3.12. JVM toolchain 21. `compose.desktop.application { mainClass = "dev.huecoder.md3agent.MainKt" }`. Add the `generateR` task.
- [ ] **Step 2:** Write `StringsTest`:
  - `unescapesApostropheAndNewline` (raw `Don\'t\nstop` → `Don't` + newline + `stop`);
  - `russianPluralForms` (1 → one, 3 → few, 5 → many, 21 → one, 11 → many);
  - `fallsBackToEnglishWhenKeyMissingInRussian`;
  - `formatsPositionalArgs` (`%1$s of %2$d`).
- [ ] **Step 3:** Run `./gradlew :app:test --tests '*StringsTest'`. Expected: FAIL (`Strings` undefined).
- [ ] **Step 4:** Implement `Strings` and the composables.
- [ ] **Step 5:** `Main.kt`: `application { Window(title = "MD3 Agent") { MaterialExpressiveTheme { LoadingIndicator(); Surface(shape = MaterialShapes.Cookie9Sided.toShape()) {} } } }`. This proves the expressive APIs compile.
- [ ] **Step 6:** `RenderSmokeTest.rendersExpressiveComponents`: `ImageComposeScene(400, 300) { … }.render()` returns a non-blank image, saved to `build/screens/smoke.png`.
- [ ] **Step 7:** Run `./gradlew :app:test`. Expected: PASS. Look at `build/screens/smoke.png`.
- [ ] **Step 8:** Commit "scaffold compose desktop app with android string resources".

### Task 2: Portable core (llm, net, mcp, tools, conversation, prompt)

**Files:**
- Copy (unchanged unless noted) into `app/src/main/kotlin/dev/huecoder/md3agent/`: `llm/**`, `net/**`, `mcp/**`, `prompt/SystemPromptRenderer.kt`, `conversation/**` (except `BackgroundWorkService.kt`), `tools/**` (except `tools/phone/**`, `ReadScreenTool.kt`), `ui/text/UiText.kt`.
- Create: `platform/Log.kt` (`object Log { fun d/i/w/e(tag: String, msg: String, t: Throwable? = null) }` writing to stderr and `$XDG_STATE_HOME/md3-agent/log.txt`, rotated at 1 MB).
- Create: `prompt/DeviceInfoProvider.kt` (Linux).
- Copy tests from `app/src/test/java/**` for these packages.

**Interfaces:**
- Replace `android.util.Log` → `dev.huecoder.md3agent.platform.Log`, and `android.util.Base64` → `java.util.Base64`, by sed across copied files.
- `class DeviceInfoProvider { fun deviceName(): String; fun osVersion(): String; fun batteryDescription(): String? }`:
  - `deviceName` from `/sys/class/dmi/id/product_name` or hostname;
  - `osVersion` is `PRETTY_NAME` from `/etc/os-release` plus `uname -r`;
  - battery from `/sys/class/power_supply/BAT*/capacity`, or null.

  `SystemPromptRenderer` calls are renamed from `androidVersion` to `osVersion`, and the prompt text says "Linux desktop" instead of "Android phone".
- `UiText.Resource(id: Int, args)` stays. `UiText.resolve()` (no Context) uses `Strings`, and `@Composable asString()` uses `stringResource`.
- `RunPythonTool` loses the `PhoneControl` parameter and the phone bridge. `RunCommandTool` takes `ShellAccess` from Task 5 (signature kept: `shell(mode)`, `status(mode)`).

- [ ] **Step 1:** Copy the files and tests and apply the sed replacements. Remove phone references (`PhoneControl`, `ScriptPhone`, `ScriptBridge` in python tool).
- [ ] **Step 2:** Run `./gradlew :app:compileKotlin`. Fix remaining Android references. Missing types from Tasks 3–5 get a temporary stub interface in the same package, replaced when that task lands.
- [ ] **Step 3:** Run `./gradlew :app:test`. Expected: all copied tests for these packages pass.
- [ ] **Step 4:** Commit "port llm, mcp, tools and conversation core".

### Task 3: XDG paths, secrets, settings

**Files:**
- Create: `system/paths/AppPaths.kt`, `system/secrets/SecretStore.kt`
- Copy and adapt: `data/settings/**` (`SecretCipher` keeps the `TextCipher` interface), `data/updates/**`
- Test: `system/paths/AppPathsTest.kt`, `system/secrets/SecretStoreTest.kt`, copied settings tests

**Interfaces:**
- `class AppPaths(env: Map<String, String> = System.getenv(), home: File = File(System.getProperty("user.home")))` with `val data: File`, `val config: File`, `val state: File`, `val runtime: File`, `val cache: File`, `val workspace: File` (= `data/workspace`), `val attachments: File`, `val uploads: File`, `val venv: File`, `val database: File` (= `data/md3agent.db`).
- `interface SecretStore { fun loadOrCreateKey(): ByteArray }`. Implementations:
  - `SecretServiceStore`: dbus `org.freedesktop.secrets`, item label "MD3 Agent settings key", attribute `application=md3-agent`;
  - `FileKeyStore(file)`: 32 random bytes, file mode `0600` via `PosixFilePermissions`.

  `SecretStore.default(paths)` tries Secret Service and falls back to file, logging a warning.
- `class SecretCipher(store: SecretStore) : TextCipher` with AES-256-GCM, the same encoding format as Android (`iv || ciphertext`, Base64).
- `SettingsRepository(paths: AppPaths, cipher: TextCipher, scope)` uses `PreferenceDataStoreFactory.createWithPath { (paths.config / "settings.preferences_pb").toOkioPath() }`.

- [ ] **Step 1:** Write `AppPathsTest`:
  - `usesXdgVariablesWhenSet`;
  - `fallsBackToHomeDefaults`;
  - `runtimeFallsBackToTmpWithUid` (no `XDG_RUNTIME_DIR` → `/tmp/md3-agent-<uid>`).
- [ ] **Step 2:** Write `SecretStoreTest`:
  - `fileKeyIsCreatedWith0600AndReused`;
  - `cipherRoundTrips`.
- [ ] **Step 3:** Run both. Expected: FAIL.
- [ ] **Step 4:** Implement, then port `data/settings/**`. Delete Android-only fields in settings models: phone use, Shizuku, Termux, root shell modes, wake word, camera frame. Rename shell modes to `SYSTEM` and `SANDBOX`.
- [ ] **Step 5:** Run `./gradlew :app:test`. Expected: PASS, including the copied `SettingsRepositoryTest` adapted to `AppPaths` in a temp dir.
- [ ] **Step 6:** Commit "xdg paths, keyring-backed secrets, settings".

### Task 4: Database, attachments, chats, memory, files library

**Files:**
- Copy and adapt: `data/db/**`, `data/chat/**`, `data/attachments/**`, `data/memory/**`, `data/models/**`, `data/reminders/**`, `data/files/**`, `system/files/DeviceFiles.kt`
- Test: copied tests for these packages

**Interfaces:**
- `AppDatabase.build(paths: AppPaths): AppDatabase` uses `Room.databaseBuilder<AppDatabase>(paths.database.path).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)`.
  - Version reset to 1, `autoMigrations` removed, `exportSchema = false`.
  - `SQLiteConstraintException` is replaced by `androidx.sqlite.SQLiteException`, checked for a constraint message.
- `android.net.Uri` is replaced by `java.io.File` in `AttachmentStore.import(file: File, displayName: String = file.name): Attachment` and `FileLibrary.import(files: List<File>)`. Image decoding uses `javax.imageio.ImageIO`, with downscaling via `java.awt.Image.getScaledInstance` replaced by `Graphics2D` bilinear drawing at the same max side as Android.
- `DeviceFiles(workspace, attachments, staging, uploads, allowedRoots: () -> List<File>)`. `sharedStorage` is replaced by `allowedRoots` (default home, from settings).
- `ChatDrafts` and `SearchHistory` take `AppPaths` and store JSON in `config/`.

- [ ] **Step 1:** Copy and adapt. Write `AppDatabaseTest.insertsAndReadsChatInTempDir` (temp file DB, insert a chat and a message, read them back through the DAO Flow).
- [ ] **Step 2:** Run `./gradlew :app:test`. Expected: copied repository tests and the new DB test pass.
- [ ] **Step 3:** Commit "room database, attachments and files on the jvm".

### Task 5: Shell and Python (System and Sandbox)

**Files:**
- Create: `system/shell/ShellAccess.kt`, `system/shell/SystemShell.kt`, `system/shell/SandboxShell.kt`, `system/shell/SandboxArgs.kt`
- Copy and adapt: `system/shell/ProcessRunner.kt` (drop Bundle), `system/python/PythonRuntime.kt` (rewrite)
- Test: `SandboxArgsTest.kt`, `ProcessRunnerTest.kt`, `PythonRuntimeTest.kt`

**Interfaces:**
- `enum class ShellMode { SYSTEM, SANDBOX }`.
- `interface Shell { suspend fun run(command: String, timeoutMs: Long, workdir: File): ShellResult }`.
- `class ShellAccess(paths: AppPaths, network: () -> Boolean)` with:
  - `fun shell(mode: ShellMode): Shell`;
  - `suspend fun status(mode: ShellMode): AccessStatus`, where `AccessStatus { READY, MISSING, BLOCKED }` and `BLOCKED` carries a reason string;
  - `val workspace: File`.
- `object SandboxArgs { fun build(workspace: File, network: Boolean, command: List<String>): List<String> }`.
  - Args: `bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/lib64 /lib64 --symlink usr/bin /bin --ro-bind /etc /etc --proc /proc --dev /dev --tmpfs /tmp --bind <workspace> <workspace> --chdir <workspace> --unshare-all [--share-net] --die-with-parent --new-session -- <command>`.
  - Symlinks are added only if the host path is a symlink. Otherwise `--ro-bind` the real dir if it exists.
- Probe: `bwrap --ro-bind / / true` with a 3 s timeout. Non-zero exit → `BLOCKED` with stderr as the reason, e.g. "setting up uid map: Permission denied" on Ubuntu AppArmor.
- `ProcessRunner.run(command, timeoutMs, workdir, environment, maxOutputBytes = 256 * 1024)`:
  - starts with `ProcessBuilder`;
  - on timeout destroys `ProcessHandle.descendants()` and then the process (forcibly after 2 s);
  - output past the cap is dropped, with a `[output truncated]` marker.
- `class PythonRuntime(paths: AppPaths, shellAccess: ShellAccess)`:
  - `suspend fun run(code: String, workdir: File, timeoutSeconds: Long, mode: ShellMode): PythonResult`;
  - `val version: String` (from `python3 --version`, cached);
  - `suspend fun ensureVenv(): Boolean` (`python3 -m venv <paths.venv>`; false if the module is missing);
  - the script is written to a temp file in `workdir` and run with the venv python if present, else system `python3`. In SANDBOX mode it goes through `SandboxArgs`, with the venv ro-bound.

- [ ] **Step 1:** Write `SandboxArgsTest`:
  - `networkOffByDefault` (no `--share-net`);
  - `networkFlagAddsShareNet`;
  - `keepsPathsWithSpaces` (workspace `/tmp/a b/ws` is passed as one arg);
  - `commandFollowsDoubleDash`.
- [ ] **Step 2:** Write `ProcessRunnerTest`:
  - `killsTreeOnTimeout` (`sh -c 'sleep 100 & sleep 100'`, timeout 500 ms → `timedOut`, no surviving child, checked via `ProcessHandle`);
  - `capsOutput` (`yes | head -c 2000000` → stdout ≤ cap + marker).
- [ ] **Step 3:** Write `PythonRuntimeTest`:
  - `runsScriptAndCapturesOutput` (`print(2+2)` → `4`);
  - `reportsFailure` (`raise ValueError("x")` → `failed`, output contains `ValueError`);
  - `timesOut` (`while True: pass`, 1 s → `timedOut`).

  Skip with `assumeTrue` if `python3` is missing.
- [ ] **Step 4:** Run the tests. Expected: FAIL.
- [ ] **Step 5:** Implement. Port `RunCommandTool` and `RunPythonTool` descriptions: modes are System and Sandbox, packages are "whatever is installed in the agent venv; install with `pip install` via run_command in System mode".
- [ ] **Step 6:** Run `./gradlew :app:test`. Expected: PASS. Sandbox tests that need a working `bwrap` are guarded by the probe.
- [ ] **Step 7:** Commit "system and bubblewrap sandbox shells, python runner".

### Task 6: Clipboard, file portal, notifications, appearance (D-Bus)

**Files:**
- Create:
  - `system/dbus/Bus.kt` (lazy session `DBusConnection`, null if unavailable);
  - `system/clipboard/ClipboardReader.kt`, `system/clipboard/ClipboardParser.kt`;
  - `system/files/FilePicker.kt`;
  - `system/notifications/Notifier.kt`;
  - `system/appearance/SystemAppearance.kt`.
- Test: `ClipboardParserTest.kt`, `NotifierTest.kt` (fake bus interface)

**Interfaces:**
- `sealed interface ClipboardContent { data class Text(val text: String); data class Files(val files: List<File>); data class Image(val png: ByteArray) }`.
- `class ClipboardReader { suspend fun read(): ClipboardContent? }`:
  1. AWT `Toolkit.getSystemClipboard()` flavors: `javaFileListFlavor`, `imageFlavor`, `stringFlavor`, and `text/uri-list` as a string.
  2. If AWT yields no files or images: on `WAYLAND_DISPLAY`, `wl-paste --list-types` then `wl-paste --type <t>`; else `xclip -selection clipboard -t TARGETS -o` then `-t <t> -o`, or `xsel -b -o` for text.
- `object ClipboardParser { fun uriList(text: String): List<File> }`: skips `#` comments, accepts only `file://`, percent-decodes UTF-8, trims `\r`.
- `class FilePicker(bus: Bus)`:
  - `suspend fun open(multiple: Boolean, imagesOnly: Boolean): List<File>`;
  - `suspend fun save(suggestedName: String): File?`.

  Uses portal `org.freedesktop.portal.FileChooser` OpenFile/SaveFile with a `handle_token`, awaiting the `org.freedesktop.portal.Request.Response` signal. Response code ≠ 0 → empty list or null. When the portal is missing, falls back to `java.awt.FileDialog`.
- `class Notifier(bus: Bus)`:
  - `suspend fun show(title: String, body: String, actions: List<Pair<String, String>>, onAction: (String) -> Unit): Int?`;
  - `fun close(id: Int)`.

  Uses `org.freedesktop.Notifications.Notify` with `app_name = "MD3 Agent"`, `ActionInvoked` signal. Returns null when no server; callers then show an in-app window.
- `class SystemAppearance(bus: Bus) { val darkMode: StateFlow<Boolean?>; val accent: StateFlow<Color?> }`: portal `org.freedesktop.portal.Settings` `ReadOne("org.freedesktop.appearance", "color-scheme" | "accent-color")` plus the `SettingChanged` signal. `null` means unknown.

- [ ] **Step 1:** Write `ClipboardParserTest`:
  - `decodesPercentEncodedUri` (`file:///home/u/%D0%94%D0%BE%D0%BA/a%20b.pdf` → `/home/u/Док/a b.pdf`);
  - `skipsCommentsAndNonFileSchemes`;
  - `handlesCrLf`.
- [ ] **Step 2:** Write `NotifierTest.returnsNullWithoutBus`.
- [ ] **Step 3:** Run. Expected: FAIL.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** Run `./gradlew :app:test`. Expected: PASS.
- [ ] **Step 6:** Commit "clipboard, portal file picker, notifications, appearance".

### Task 7: Single instance and CLI

**Files:**
- Create: `system/instance/InstanceSocket.kt`, `system/instance/LaunchArgs.kt`
- Modify: `Main.kt`
- Test: `InstanceSocketTest.kt`, `LaunchArgsTest.kt`

**Interfaces:**
- `sealed interface LaunchCommand { object Show; object Overlay; data class Chat(val id: String); data class Reminder(val id: String); object Version; object CheckEnvironment }`.
- `object LaunchArgs { fun parse(args: Array<String>): LaunchCommand }`. Unknown args → `Show`.
- `class InstanceSocket(path: File)`:
  - `fun tryForward(command: LaunchCommand): Boolean` connects via `UnixDomainSocketAddress` and writes one JSON line;
  - `fun listen(scope: CoroutineScope, onCommand: (LaunchCommand) -> Unit): Closeable`;
  - stale file (connect refused) is deleted, then bind.
- `Main.kt`:
  - `Version` prints `md3-agent <version>` and exits;
  - `CheckEnvironment` runs Task 13's report and exits;
  - otherwise forwards and exits if another instance answered, else starts the app and listens.

- [ ] **Step 1:** Write `LaunchArgsTest` (`--chat abc`, `--reminder r1`, `--overlay`, unknown).
- [ ] **Step 2:** Write `InstanceSocketTest`:
  - `secondLaunchForwardsArguments` (listener receives `Chat("abc")`);
  - `staleSocketIsReplaced` (pre-created file, no listener → `tryForward` false, then `listen` binds).
- [ ] **Step 3:** Run, see FAIL, implement, run, see PASS.
- [ ] **Step 4:** Commit "single instance socket and command line".

### Task 8: Reminders, alarms, timers

**Files:**
- Create: `system/reminders/ReminderScheduler.kt`, `system/reminders/SystemdTimers.kt`, `system/reminders/AlarmWindow.kt` (always-on-top Compose window with Stop and Snooze)
- Modify: `data/reminders/ReminderRepository.kt` (uses `ReminderScheduler`), `tools/reminders/SetAlarmTool.kt`, `SetTimerTool.kt` (use the scheduler with `sound = true` instead of `ClockApp`)
- Test: `ReminderSchedulerTest.kt`, `SystemdTimersTest.kt`

**Interfaces:**
- `class ReminderScheduler(repo: ReminderDao, notifier: Notifier, timers: SystemdTimers?, clock: () -> Long, scope: CoroutineScope)`:
  - `fun schedule(reminder: ReminderEntity)`;
  - `fun cancel(id: String)`;
  - `suspend fun onStart()` fires overdue, unfired reminders once with `missed = true` and marks them fired;
  - `fun fire(id: String)`;
  - `val alarms: StateFlow<List<ActiveAlarm>>` (alarms needing a window).
- `class SystemdTimers(unitDir: File, launcher: String, runner: (List<String>) -> ShellResult)`:
  - `fun available(): Boolean` (`systemctl --user is-system-running` exits with `running` or `degraded`);
  - `fun install(id: String, atEpochMs: Long)` writes `md3-agent-reminder-<id>.timer` and `.service` into `~/.config/systemd/user/`, then `daemon-reload` and `enable --now`;
  - `fun remove(id: String)`.
- Timer text: `OnCalendar=<yyyy-MM-dd HH:mm:ss>` in local time, `Persistent=true`, `AccuracySec=1s`.
- Service text: `Type=oneshot`, `ExecStart=<launcher> --reminder <id>`.
- `ReminderEntity` gains `sound: Boolean` (alarm and timer), plus `missed` in the notification text.

- [ ] **Step 1:** Write `SystemdTimersTest`:
  - `timerUnitHasCalendarAndPersistent`;
  - `serviceUnitRunsLauncherWithReminderId`;
  - `removeDisablesAndDeletesUnits` (fake runner records commands).
- [ ] **Step 2:** Write `ReminderSchedulerTest`:
  - `missedReminderFiresOnceOnStart` (`onStart` twice → one notification);
  - `fireMarksReminderFired`;
  - `alarmFiringAddsActiveAlarm`.
- [ ] **Step 3:** Run, see FAIL, implement, run, see PASS.
- [ ] **Step 4:** Commit "reminders with systemd timers and in-app fallback, alarm window".

### Task 9: UI foundation (resources, theme, components)

**Files:**
- Copy and adapt: `ui/theme/**`, `ui/components/**`, `ui/avatar/**`, `ui/physics/**`, `ui/pictures/**`, `ui/math/**`, `ui/text/**`, `ui/haptics/**`
- Create: `ui/res/Drawables.kt`; `app/src/main/resources/drawable/*.xml` (vendor icons, launcher foreground); `app/src/main/resources/font/google_sans.ttf`
- Test: `ui/ScreensRenderTest.kt` (first cases)

**Interfaces:**
- `@Composable fun vectorResource(name: String): ImageVector` loads `drawable/<name>.xml` via `loadXmlImageVector` and caches it. `R.drawable.x` usages become `vectorResource("x")`.
- Haptics: `HapticEngine` becomes a no-op class with the same API.
- `ui/math`: `FormulaDrawables` renders with `org.scilab.forge.jlatexmath.TeXFormula.createBufferedImage` to an `ImageBitmap` (`toComposeImageBitmap()`), with an LRU cache of the same size.
- Theme:
  - `AppTheme(dark: Boolean?, accent: Color?)` where null means `SystemAppearance` and then the user setting;
  - font `FontFamily(Font(resource = "font/google_sans.ttf"))`;
  - `SystemClock.uptimeMillis()` → `System.nanoTime() / 1_000_000`.

- [ ] **Step 1:** Copy, apply replacements, compile.
- [ ] **Step 2:** `ScreensRenderTest.rendersAvatarAndShapes` (light and dark) writes `build/screens/foundation-{light,dark}.png`.
- [ ] **Step 3:** Run `./gradlew :app:test`, look at the PNGs.
- [ ] **Step 4:** Commit "ui foundation: theme, components, avatar, math".

### Task 10: Chat, home, files, models screens

**Files:**
- Copy and adapt: `feature/chat/**` (drop `InAppCamera.kt` and `CameraCapture.kt`), `feature/home/**`, `feature/files/**`, `feature/models/**`, `feature/playground/**` (drop the sensor input, keep pointer input), `ui/files/**`
- Modify: `feature/chat/ChatInput.kt` (`AttachSource { PHOTOS, FILES }`), `feature/chat/ChatScreen.kt`
- Test: extend `ScreensRenderTest`

**Interfaces:**
- `ChatViewModel.attach(files: List<File>)` (replaces `List<Uri>`). `ChatViewModel.attachPng(bytes: ByteArray)` for pasted images, saved to `paths.cache/paste-<time>.png` and then attached.
- `ChatInput` key handling via `onPreviewKeyEvent`:
  - Enter without Shift calls `onSend` when `canSend`;
  - Shift+Enter inserts a newline;
  - Ctrl+V calls `ClipboardReader.read()`: `Files` → attach, `Image` → `attachPng`, `Text` → default paste.
- Drag and drop on the chat screen: `Modifier.dragAndDropTarget` accepting `DragData.FilesList` → attach (URIs parsed with `ClipboardParser.uriList`).
- Open and share: `ui/files/OpenFile.kt` → `xdg-open <path>` via `ProcessBuilder`; share → copy path to the clipboard.
- `ViewModel` creation via JetBrains `viewModel { … }` with `AppContainer` from a `CompositionLocal`.

- [ ] **Step 1:** Copy, adapt, compile.
- [ ] **Step 2:** `ScreensRenderTest`:
  - `rendersChatWithMessages` (fake repository with user and assistant messages, a chart, a code block, LaTeX) at 420×900 and 1280×800;
  - `rendersAttachMenu`.
- [ ] **Step 3:** Run, look at the PNGs: composer transparent with the top fade, attach menu items "Фото" and "Файлы".
- [ ] **Step 4:** Commit "chat, home, files and model screens".

### Task 11: Settings screens, environment screen

**Files:**
- Copy and adapt: `feature/settings/**`. Drop `DeviceAccessScreen.kt` and `DeviceAccessViewModel.kt`, and the phone-use and wake-word sections.
- Create: `feature/settings/EnvironmentScreen.kt`, `system/environment/EnvironmentReport.kt`, `system/environment/Distro.kt`
- Test: `DistroTest.kt`, `EnvironmentReportTest.kt`

**Interfaces:**
- `data class Distro(val id: String, val idLike: List<String>, val pretty: String)`; `fun Distro.Companion.read(osRelease: File = File("/etc/os-release")): Distro`.
- `enum class PackageManager { APT, DNF, ZYPPER, PACMAN, UNKNOWN }`; `fun Distro.packageManager(): PackageManager` by id or idLike (debian/ubuntu → APT, fedora/rhel → DNF, suse/opensuse → ZYPPER, arch → PACMAN).
- `enum class Component(val program: String) { BWRAP("bwrap"), PYTHON("python3"), PYTHON_VENV("python3 -m venv"), WL_PASTE("wl-paste"), XCLIP("xclip"), PORTAL("xdg-desktop-portal"), SECRET_SERVICE("org.freedesktop.secrets"), NOTIFICATIONS("org.freedesktop.Notifications"), SYSTEMD_USER("systemctl --user") }`.
- `fun Component.packageName(pm: PackageManager): String?` with the package table:

  | Component | apt | dnf | zypper | pacman |
  |---|---|---|---|---|
  | `bwrap` | bubblewrap | bubblewrap | bubblewrap | bubblewrap |
  | `python3` | python3 | python3 | python3 | python |
  | venv | python3-venv | python3 | python3 | python |
  | `wl-paste` | wl-clipboard | wl-clipboard | wl-clipboard | wl-clipboard |
  | `xclip` | xclip | xclip | xclip | xclip |
  | portal | xdg-desktop-portal | xdg-desktop-portal | xdg-desktop-portal | xdg-desktop-portal |

- `data class ComponentStatus(val component: Component, val found: Boolean, val detail: String?)`; `class EnvironmentReport(...) { suspend fun collect(): List<ComponentStatus>; fun format(statuses, distro): String }`.

- [ ] **Step 1:** Write `DistroTest`:
  - `ubuntuMapsToApt`;
  - `mintIdLikeUbuntuMapsToApt`;
  - `tumbleweedMapsToZypper`;
  - `endeavourIdLikeArchMapsToPacman`;
  - `missingFileGivesUnknown`.
- [ ] **Step 2:** Write `EnvironmentReportTest.formatNamesPackageForMissingComponent` (missing bwrap on apt → line contains `sudo apt install bubblewrap`).
- [ ] **Step 3:** Run, see FAIL, implement, run, see PASS.
- [ ] **Step 4:** Port the settings screens. Tool settings show the hint under disabled `run_command` and `run_python` modes. "Shortcuts" shows the `md3-agent --overlay` command when GlobalShortcuts is unavailable.
- [ ] **Step 5:** Render test `rendersSettingsAndEnvironment`. Look at the PNGs.
- [ ] **Step 6:** Commit "settings and environment screens".

### Task 12: Main window, adaptive navigation, overlay, shortcuts, voice

**Files:**
- Copy and adapt: `ui/navigation/**` (JetBrains navigation-compose), `feature/main/**`, `AppContainer.kt`
- Create: `app/App.kt` (`application {}` with the main window and the overlay window), `system/shortcuts/GlobalShortcuts.kt`, `voice/DesktopVoiceRecorder.kt`, `voice/DesktopSpeechPlayer.kt`
- Delete from copy: `assistant/**` (except what the overlay reuses from `AssistantOverlay.kt` content), `system/control/**`, `feature/phoneuse/**`, `system/lifecycle/AppVisibility.kt` (replaced by a window-focus `StateFlow`)
- Test: render tests, `VoiceRecorderTest.kt`

**Interfaces:**
- `AppContainer(paths: AppPaths)`: same members as Android minus phone, screen context, camera and Shizuku, plus `clipboard`, `filePicker`, `notifier`, `appearance`, `reminderScheduler`, `environment`. Tool list = Android list minus `ReadScreenTool`, `PhoneScreenTool`, `PhoneScreenshotTool`, `PhoneActionTool`.
- Layout: width ≥ 840 dp → `NavigationRail` + list pane (360 dp) + detail. Below that → current phone navigation.
- Window shortcuts via `Window(onKeyEvent)`: Ctrl+N new chat, Ctrl+K search, Ctrl+Comma settings, Escape → back, or stop streaming when the chat is focused and streaming.
- Overlay: `Window(undecorated = true, alwaysOnTop = true, transparent = true)`, 480×560, opened by `LaunchCommand.Overlay`. "Expand" opens the chat in the main window and closes the overlay.
- `GlobalShortcuts(bus)`: portal `org.freedesktop.portal.GlobalShortcuts` `CreateSession` + `BindShortcuts` with id `overlay`, preferred trigger `CTRL+ALT+space`, and the `Activated` signal. `val available: Boolean`.
- Voice:
  - `class DesktopVoiceRecorder { fun start(); fun level(): Float; fun stop(): File; fun cancel() }` uses `TargetDataLine` at 16 kHz mono 16-bit PCM and writes WAV. Under 0.5 s → `RecordingTooShortException`. No mixer line → `available = false`, dictation button hidden.
  - `DesktopSpeechPlayer` plays provider TTS requested with `response_format = "wav"` through `javax.sound.sampled.Clip`.
  - `SystemSpeechSynthesizer` uses `spd-say` if present, else voice output is provider-only.

- [ ] **Step 1:** Write `VoiceRecorderTest.writesValidWavHeader` (`WavWriter` on synthetic PCM → RIFF header, sizes, 16000 Hz). Run, see FAIL, implement, run, see PASS.
- [ ] **Step 2:** Port navigation, main and container. Compile.
- [ ] **Step 3:** Render tests:
  - `rendersWideLayout` (1280×800, rail + list + chat);
  - `rendersNarrowLayout` (420×900);
  - `rendersOverlay`.

  Look at the PNGs.
- [ ] **Step 4:** Commit "main window, adaptive layout, overlay, shortcuts, voice".

### Task 13: --check-environment and the about screen

**Files:**
- Modify: `Main.kt`, `feature/settings/AboutScreen.kt`

**Interfaces:**
- Consumes: `EnvironmentReport.collect/format`, `Distro.read`.
- `md3-agent --check-environment` prints `format(...)` and exits 0. It needs no display: no AWT is touched (`java.awt.headless=true` is set before anything loads).

- [ ] **Step 1:** Run `./gradlew :app:run --args='--check-environment'`. Expected: lists distro "Arch Linux" and every component found or missing, exit 0.
- [ ] **Step 2:** Run `./gradlew :app:run --args='--version'`. Expected: `md3-agent 1.0.0`.
- [ ] **Step 3:** Commit "environment check from the command line".

### Task 14: Import from phone (Linux side and Android export)

**Files:**
- Create (Linux): `data/transfer/TransferFormat.kt`, `data/transfer/ExportCrypto.kt`, `data/transfer/Importer.kt`, `feature/settings/ImportScreen.kt`
- Create (Android, `~/Projects/MD3-Agent`): `data/transfer/TransferFormat.kt`, `data/transfer/ExportCrypto.kt` (same code), `data/transfer/Exporter.kt`, plus an export entry in the Android settings and strings (en, ru)
- Test (both repos): `ExportCryptoTest.kt`. Linux: `ImporterTest.kt`

**Interfaces:**
- `@Serializable data class TransferFile(val format: Int = 1, val createdAt: Long, val salt: String, val iterations: Int = 600_000, val providers: List<ProviderSettingsDto>, val agents: List<AgentProfile>, val mcpServers: List<McpServerSettings>, val memories: List<MemoryDto>, val settings: JsonObject, val chats: List<ChatDto>? = null)`.
  - Secrets inside the DTOs (API keys, header values, OAuth tokens) are `EncryptedField(iv: String, data: String)`.
  - Attachments are base64 inside `ChatDto`. Chats are included only when checked.
- `object ExportCrypto`:
  - `fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey` (PBKDF2WithHmacSHA256, 256-bit);
  - `fun seal(key: SecretKey, plain: String): EncryptedField`;
  - `fun open(key: SecretKey, field: EncryptedField): String` (AES/GCM/NoPadding, 12-byte IV, 128-bit tag).
- `class Importer(settings, db, memories, attachments) { suspend fun preview(file: File, password: CharArray): ImportPreview; suspend fun apply(preview: ImportPreview, overwrite: Set<String>) }`.
  - `preview` decrypts everything up front, so a wrong password fails before any write.
  - `ImportPreview` lists new items and conflicting ids.
  - An unknown `format` throws `UnsupportedTransferFormat(format)`.

- [ ] **Step 1:** Write `ExportCryptoTest`:
  - `roundTrip`;
  - `wrongPasswordFailsWithoutPartialImport` (preview with a wrong password throws `AEADBadTagException`, DB untouched);
  - `truncatedFileFails`;
  - `unknownFormatRejected`.
- [ ] **Step 2:** Write `ImporterTest.conflictsNeedOverwriteToReplace`.
- [ ] **Step 3:** Run, see FAIL, implement, run, see PASS (Linux).
- [ ] **Step 4:** Android: add `Exporter` and "Export for PC" in settings (password dialog, chats checkbox, system save picker). Run `./gradlew :app:testDebugUnitTest` and `:app:assembleRelease` in MD3-Agent.
- [ ] **Step 5:** Commit in each repo: "import from phone" / "export settings, keys and chats for the desktop app".

### Task 15: Packaging (deb, rpm, AppImage, PKGBUILD)

**Files:**
- Modify: `app/build.gradle.kts` (`nativeDistributions { targetFormats(Deb, Rpm); packageName = "md3-agent"; linux { iconFile; shortcut = true; menuGroup = "Utility"; debMaintainer; appCategory = "utils" } ; modules(...) }`)
- Create:
  - `packaging/md3-agent.desktop` (`Exec=md3-agent %U`, `StartupWMClass=MD3 Agent`, `Categories=Utility;Chat;`);
  - `packaging/icons/hicolor/{48,128,256,512}/md3-agent.png`;
  - `packaging/appimage/build-appimage.sh`;
  - `PKGBUILD`;
  - `packaging/README.md`.

**Interfaces:**
- jlink modules: `java.base, java.desktop, java.logging, java.naming, java.net.http, java.prefs, java.sql, jdk.crypto.ec, jdk.unsupported, jdk.zipfs`. Check with `./gradlew suggestRuntimeModules`.
- `build-appimage.sh`: `createDistributable` output → `AppDir/usr/`, plus AppRun, `.desktop` and icon. Runs `appimagetool` from PATH. If it is missing, prints how to get it and exits 2. It never downloads anything by itself.
- PKGBUILD:
  - `pkgname=md3-agent`;
  - builds `packageUberJarForCurrentOS`;
  - installs the jar to `/usr/share/md3-agent/md3-agent.jar`, the launcher `/usr/bin/md3-agent` (`exec java -jar /usr/share/md3-agent/md3-agent.jar "$@"`), the desktop file and the icons;
  - `depends=('java-runtime>=21')`;
  - `optdepends=('bubblewrap: sandbox mode' 'python: run_python' 'wl-clipboard: paste files on Wayland' 'xclip: paste files on X11' 'xdg-desktop-portal: system file picker')`.
- deb `Recommends` and rpm `Recommends` carry the same set (bubblewrap, python3, python3-venv on deb, wl-clipboard, xclip, xdg-desktop-portal), via `linux { debPackageVersion; rpmPackageVersion }` plus jpackage `--linux-package-deps` where supported, else documented.

- [ ] **Step 1:** Run `./gradlew :app:packageDeb :app:packageRpm`. Expected: files under `app/build/compose/binaries/main/{deb,rpm}/`. `rpm` needs `rpmbuild`; if it is missing, record it and continue.
- [ ] **Step 2:** Run `makepkg -f` in a temp copy. Expected: `md3-agent-1.0.0-1-any.pkg.tar.zst`. Then `tar -tf` lists `/usr/bin/md3-agent`.
- [ ] **Step 3:** Run `packaging/appimage/build-appimage.sh`. Expected: an AppImage, or exit 2 with instructions if `appimagetool` is missing.
- [ ] **Step 4:** Smoke: `dpkg-deb -c` and `rpm -qlp` list the launcher. Run the extracted launcher with `--version` and `--check-environment`. Container install tests run only if podman or docker exists. Otherwise record that they were skipped.
- [ ] **Step 5:** Commit "deb, rpm, appimage and arch packaging".

### Task 16: Final verification

- [ ] **Step 1:** Run `./gradlew :app:test`. Expected: all tests pass. Record the count.
- [ ] **Step 2:** Run `ScreensRenderTest` and review every PNG (wide and narrow, light and dark, settings, environment, overlay, attach menu, import).
- [ ] **Step 3:** Real run without touching the user's desktop: start the app with `--check-environment` only, then use a headless harness `HeadlessAgentRun`:
  - test-scope main, uses `AppContainer` with a temp `AppPaths` and the user's provider settings imported from env `MD3_TEST_PROVIDER_URL`, `MD3_TEST_API_KEY`, `MD3_TEST_MODEL`;
  - skipped if unset.

  It sends a prompt that calls `run_command` in System and Sandbox mode, `run_python`, `get_weather`, `memory_add` and `set_reminder` (10 s ahead), with auto-approval in the harness. It asserts every tool result is non-error and that the reminder fires (fake notifier).
- [ ] **Step 4:** Write `README.md` (install per distro, dependencies, flags, data paths, known limits: XWayland, no Flatpak, AppImage needs glibc).
- [ ] **Step 5:** Commit "readme and final checks".
