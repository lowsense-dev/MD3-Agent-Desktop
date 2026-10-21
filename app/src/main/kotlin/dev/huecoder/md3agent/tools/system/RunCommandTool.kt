package dev.huecoder.md3agent.tools.system

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.system.shell.ShellAccess
import dev.huecoder.md3agent.system.shell.ShellResult
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class RunCommandTool(private val access: ShellAccess) : Tool {
    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = MAX_TIMEOUT_S * MS_PER_SECOND + TIMEOUT_GRACE_MS

    override val spec = ToolSpec(
        name = NAME,
        description = "Runs a shell command on the user's computer and returns its exit code, stdout and stderr. " +
            "Each call is a fresh non-interactive process: no TTY, no prompts, state does not carry over between calls. " +
            "Chain steps with && in one command. See the device access notes for which shell runs it and what it can reach.",
        parameters = objectSchema(required = listOf("command")) {
            stringProperty("command", "Shell command line")
            integerProperty("timeout_seconds", "How long to wait before killing the command, default $DEFAULT_TIMEOUT_S", 1, MAX_TIMEOUT_S.toInt())
        },
    )

    override fun promptNote(settings: ToolSettings): String = when (settings.access.shell) {
        ShellMode.SYSTEM -> "$NAME runs bash -lc as the user (sh if bash is missing), with the same access as their own terminal; it starts in the workspace ${access.workspace.path}. " +
            "There is no sudo password prompt, so commands that need root fail. Double check anything destructive."
        ShellMode.SANDBOX -> "$NAME runs bash inside a bubblewrap sandbox: system programs are readable, only the workspace ${access.workspace.path} is writable and the rest of the home folder is hidden" +
            if (settings.access.sandboxNetwork) "; the network is reachable." else "; there is no network."
    }

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val command = arguments.requireString("command")
        val timeoutSeconds = (arguments.optionalInt("timeout_seconds") ?: DEFAULT_TIMEOUT_S.toInt()).toLong().coerceIn(1, MAX_TIMEOUT_S)
        val mode = settings.access.shell
        val status = access.status(mode)
        if (status != AccessStatus.READY) return ToolOutcome.error(access.unavailableReason(mode) ?: "The ${mode.name.lowercase()} shell is not available")
        val result = access.shell(mode, settings.access.sandboxNetwork).run(command, timeoutSeconds * MS_PER_SECOND)
        return ToolOutcome(result.describe(timeoutSeconds, settings.access.shell), isError = result.exitCode == null)
    }

    private fun ShellResult.describe(timeoutSeconds: Long, mode: ShellMode): String = buildString {
        when {
            !timedOut -> appendLine("Exit code: ${exitCode ?: "unknown"}")
            else -> appendLine("Killed the command and everything it started after $timeoutSeconds s without finishing")
        }
        if (stdout.isNotEmpty()) append("stdout:\n").appendLine(stdout.clipped())
        if (stderr.isNotEmpty()) append("stderr:\n").appendLine(stderr.clipped())
        if (stdout.isEmpty() && stderr.isEmpty()) appendLine("No output")
    }.trimEnd()

    private fun String.clipped(): String {
        if (length <= MAX_STREAM_CHARS) return trimEnd()
        val half = MAX_STREAM_CHARS / 2
        return take(half) + "\n… ${length - MAX_STREAM_CHARS} characters cut …\n" + takeLast(half).trimEnd()
    }

    companion object {
        const val NAME = "run_command"
        private const val DEFAULT_TIMEOUT_S = 60L
        private const val MAX_TIMEOUT_S = 300L
        private const val MS_PER_SECOND = 1_000L
        private const val TIMEOUT_GRACE_MS = 10_000L
        private const val MAX_STREAM_CHARS = 12_000
    }
}
