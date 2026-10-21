package dev.huecoder.md3agent.system.shell

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.system.environment.Programs
import dev.huecoder.md3agent.system.paths.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ShellAccess(private val paths: AppPaths) {
    val workspace: File get() = paths.workspace

    @Volatile
    private var sandboxProbe: Pair<AccessStatus, String?>? = null

    fun shell(mode: ShellMode, network: Boolean = true): Shell = when (mode) {
        ShellMode.SYSTEM -> SystemShell(workspace)
        ShellMode.SANDBOX -> SandboxShell(workspace, network)
    }

    suspend fun status(mode: ShellMode): AccessStatus = when (mode) {
        ShellMode.SYSTEM -> AccessStatus.READY
        ShellMode.SANDBOX -> probeSandbox().first
    }

    /** Why [status] is not READY, phrased for the model and the settings screen. */
    fun unavailableReason(mode: ShellMode): String? = if (mode == ShellMode.SANDBOX) sandboxProbe?.second else null

    /** Forget the last sandbox check, e.g. after the user installed bubblewrap. */
    fun recheck() {
        sandboxProbe = null
    }

    private suspend fun probeSandbox(): Pair<AccessStatus, String?> = sandboxProbe ?: withContext(Dispatchers.IO) {
        val bwrap = Programs.find("bwrap")
        val result = when {
            bwrap == null -> AccessStatus.NOT_INSTALLED to "The sandbox needs bubblewrap (bwrap), which is not installed. Install the bubblewrap package or switch run_command to System mode."
            else -> try {
                val probe = ProcessRunner.run(listOf(bwrap.path, "--ro-bind", "/", "/", "true"), PROBE_TIMEOUT_MS)
                if (probe.exitCode == 0) {
                    AccessStatus.READY to null
                } else {
                    AccessStatus.UNAVAILABLE to "bubblewrap cannot create a sandbox here: ${probe.stderr.trim().ifEmpty { "exit code ${probe.exitCode}" }}. " +
                        "On Ubuntu 24.04 and newer, AppArmor blocks unprivileged user namespaces unless bwrap has a profile allowing them. Switch run_command to System mode or allow bwrap."
                }
            } catch (e: IOException) {
                AccessStatus.UNAVAILABLE to "bubblewrap could not be started: ${e.message}"
            }
        }
        result.also { sandboxProbe = it }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 3_000L
    }
}
