package dev.huecoder.md3agent.tools.system

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.python.PythonRuntime
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import java.io.File

class RunPythonTool(
    private val python: PythonRuntime,
    private val workspace: File,
) : Tool {
    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = (MAX_TIMEOUT_S + TIMEOUT_GRACE_S) * MS_PER_SECOND

    override val spec = ToolSpec(
        name = NAME,
        description = "Runs a Python 3 script on the user's computer and returns everything it printed. " +
            "Use it for calculations, parsing and converting data, and working with files. " +
            "It runs with the agent's own virtual environment when there is one, so packages installed there with pip (through run_command in System mode: ${python.venvPip} install <package>) are importable; otherwise only the system Python and its installed packages. " +
            "Save charts with matplotlib savefig into the workspace and show them with send_files. " +
            "Each call starts with fresh globals, so save anything you need later to a file. " +
            "The working directory is the agent workspace ${workspace.path}, files saved there can be shown to the user with send_files.",
        parameters = objectSchema(required = listOf(CODE)) {
            stringProperty(CODE, "Python source to run, print the results you need")
            integerProperty("timeout_seconds", "How long the script may run, default $DEFAULT_TIMEOUT_S", 1, MAX_TIMEOUT_S.toInt())
        },
    )

    override fun promptNote(settings: ToolSettings): String = when (settings.access.shell) {
        ShellMode.SYSTEM -> "$NAME runs as the user, with the same access as their own programs."
        ShellMode.SANDBOX -> "$NAME runs in a bubblewrap sandbox that only sees the workspace (and the system files Python needs); the rest of the home folder is hidden."
    }

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val code = arguments.requireString(CODE)
        val timeoutSeconds = (arguments.optionalInt("timeout_seconds")?.toLong() ?: DEFAULT_TIMEOUT_S).coerceIn(1, MAX_TIMEOUT_S)
        val result = python.run(code, workspace, timeoutSeconds, settings.access.shell, settings.access.sandboxNetwork)
        val output = result.output.ifBlank { "(no output)" }.let {
            if (it.length > MAX_OUTPUT_CHARS) it.take(MAX_OUTPUT_CHARS) + "\n…[output truncated at $MAX_OUTPUT_CHARS characters]" else it
        }
        val text = if (result.timedOut) "$output\nStopped after $timeoutSeconds s" else output
        return ToolOutcome(text, isError = result.failed || result.timedOut)
    }

    companion object {
        const val NAME = "run_python"
        private const val CODE = "code"
        private const val DEFAULT_TIMEOUT_S = 30L
        private const val MAX_TIMEOUT_S = 300L
        private const val TIMEOUT_GRACE_S = 10L
        private const val MS_PER_SECOND = 1_000L
        private const val MAX_OUTPUT_CHARS = 20_000
    }
}
