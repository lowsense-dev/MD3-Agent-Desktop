package dev.huecoder.md3agent.system.environment

import java.io.File

/** Finds programs on PATH the way a shell would. */
object Programs {
    fun find(name: String, path: String? = System.getenv("PATH")): File? =
        (path ?: DEFAULT_PATH).split(File.pathSeparatorChar)
            .filter { it.isNotEmpty() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }

    private const val DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin"
}
