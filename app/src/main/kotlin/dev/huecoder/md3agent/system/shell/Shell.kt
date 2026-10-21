package dev.huecoder.md3agent.system.shell

import dev.huecoder.md3agent.system.environment.Programs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.nio.file.Files

interface Shell {
    suspend fun run(command: String, timeoutMs: Long): ShellResult
}

enum class AccessStatus { READY, UNKNOWN, NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, UNAVAILABLE }

internal fun userShell(): String = Programs.find("bash")?.path ?: Programs.find("sh")?.path ?: "/bin/sh"

/** The user's own shell: a login shell, so PATH additions from their profile (~/.local/bin and so on) apply. */
class SystemShell(private val workspace: File) : Shell {
    override suspend fun run(command: String, timeoutMs: Long): ShellResult = runInterruptible(Dispatchers.IO) {
        workspace.mkdirs()
        ProcessRunner.run(listOf(userShell(), "-lc", command), timeoutMs, workdir = workspace)
    }
}

class SandboxShell(private val workspace: File, private val network: Boolean) : Shell {
    override suspend fun run(command: String, timeoutMs: Long): ShellResult = runInterruptible(Dispatchers.IO) {
        workspace.mkdirs()
        ProcessRunner.run(SandboxArgs.build(workspace, network, listOf(userShell(), "-c", command)), timeoutMs, workdir = workspace)
    }
}

/** bwrap arguments for the sandbox: system dirs read-only, only the workspace writable, everything else unshared. */
object SandboxArgs {
    private val TOP_LEVEL = listOf("/bin", "/sbin", "/lib", "/lib32", "/lib64", "/libx32")

    fun build(workspace: File, network: Boolean, command: List<String>, readOnly: List<File> = emptyList()): List<String> = buildList {
        add("bwrap")
        addAll(listOf("--ro-bind", "/usr", "/usr"))
        // Merged-/usr distros have these as symlinks into /usr; older layouts have real directories.
        for (dir in TOP_LEVEL) {
            val path = File(dir).toPath()
            when {
                Files.isSymbolicLink(path) -> addAll(listOf("--symlink", Files.readSymbolicLink(path).toString(), dir))
                Files.isDirectory(path) -> addAll(listOf("--ro-bind", dir, dir))
            }
        }
        addAll(listOf("--ro-bind", "/etc", "/etc"))
        // resolv.conf often points here (systemd-resolved); without it the network has no DNS.
        addAll(listOf("--ro-bind-try", "/run/systemd/resolve", "/run/systemd/resolve"))
        addAll(listOf("--proc", "/proc", "--dev", "/dev", "--tmpfs", "/tmp"))
        for (file in readOnly) addAll(listOf("--ro-bind", file.path, file.path))
        addAll(listOf("--bind", workspace.path, workspace.path, "--chdir", workspace.path))
        addAll(listOf("--setenv", "HOME", workspace.path, "--setenv", "TMPDIR", "/tmp"))
        add("--unshare-all")
        if (network) add("--share-net")
        addAll(listOf("--die-with-parent", "--new-session", "--"))
        addAll(command)
    }
}
