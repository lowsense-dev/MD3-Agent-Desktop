package dev.huecoder.md3agent.system.python

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.system.environment.Programs
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.system.shell.ProcessRunner
import dev.huecoder.md3agent.system.shell.SandboxArgs
import dev.huecoder.md3agent.system.shell.ShellAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class PythonResult(val output: String, val failed: Boolean, val timedOut: Boolean)

/**
 * Runs scripts with the agent's own venv when it can make one (so pip packages can be added), else the system python3.
 * Each script is a separate process, killed with everything it started when it runs too long or the call is cancelled.
 */
class PythonRuntime(private val paths: AppPaths, private val shells: ShellAccess) {
    private val venvLock = Mutex()

    @Volatile
    private var venvFailed = false

    val venvPip: String get() = paths.venv.resolve("bin/pip").path

    /** Whether a usable python3 exists at all. */
    fun available(): Boolean = Programs.find("python3") != null

    suspend fun run(code: String, workdir: File, timeoutSeconds: Long, mode: ShellMode, network: Boolean): PythonResult {
        val python = interpreter() ?: return PythonResult("python3 is not installed. Install the python3 package (python on Arch).", failed = true, timedOut = false)
        return runInterruptible(Dispatchers.IO) {
            workdir.mkdirs()
            val script = File(workdir, ".md3-script-${UUID.randomUUID()}.py")
            try {
                script.writeText(code)
                val command = listOf(python.path, "-u", script.path)
                val venv = paths.venv.takeIf { python.path.startsWith(it.path) }
                val full = if (mode == ShellMode.SANDBOX) SandboxArgs.build(workdir, network, command, readOnly = listOfNotNull(venv)) else command
                val result = ProcessRunner.run(full, timeoutSeconds * MS_PER_SECOND, workdir = workdir, environment = ENVIRONMENT)
                val output = listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n").trimEnd()
                PythonResult(output, failed = result.exitCode != 0, timedOut = result.timedOut)
            } finally {
                script.delete()
            }
        }
    }

    private suspend fun interpreter(): File? {
        val system = Programs.find("python3") ?: return null
        val venvPython = paths.venv.resolve("bin/python3")
        if (venvPython.canExecute()) return venvPython
        if (venvFailed) return system
        return venvLock.withLock {
            if (venvPython.canExecute()) return@withLock venvPython
            val created = runInterruptible(Dispatchers.IO) {
                paths.venv.parentFile?.mkdirs()
                ProcessRunner.run(listOf(system.path, "-m", "venv", paths.venv.path), VENV_TIMEOUT_MS)
            }
            if (created.exitCode == 0 && venvPython.canExecute()) {
                venvPython
            } else {
                // Debian and Ubuntu ship venv separately (python3-venv); scripts still run, only pip installs are unavailable.
                Log.w(TAG, "Could not create the agent venv: ${created.stderr.trim()}")
                paths.venv.deleteRecursively()
                venvFailed = true
                system
            }
        }
    }

    private companion object {
        const val TAG = "PythonRuntime"
        const val MS_PER_SECOND = 1_000L
        const val VENV_TIMEOUT_MS = 120_000L
        val ENVIRONMENT = mapOf("MPLBACKEND" to "Agg", "PYTHONIOENCODING" to "utf-8", "PYTHONDONTWRITEBYTECODE" to "1")
    }
}
