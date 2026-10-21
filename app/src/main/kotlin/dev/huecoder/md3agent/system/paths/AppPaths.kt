package dev.huecoder.md3agent.system.paths

import java.io.File

/** Where the app keeps its files, following the XDG Base Directory spec with its documented defaults. */
class AppPaths(
    private val env: Map<String, String> = System.getenv(),
    private val home: File = File(System.getProperty("user.home")),
    private val uid: Int = currentUid(),
) {
    val data: File get() = base("XDG_DATA_HOME", ".local/share").resolve(APP)
    val config: File get() = base("XDG_CONFIG_HOME", ".config").resolve(APP)
    val state: File get() = base("XDG_STATE_HOME", ".local/state").resolve(APP)
    val cache: File get() = base("XDG_CACHE_HOME", ".cache").resolve(APP)
    val runtime: File get() = absolute("XDG_RUNTIME_DIR") ?: File("/tmp/$APP-$uid")
    val workspace: File get() = data.resolve("workspace")
    val attachments: File get() = data.resolve("attachments")
    val uploads: File get() = data.resolve("uploads")
    val venv: File get() = data.resolve("venv")
    val database: File get() = data.resolve("md3agent.db")

    /** The user's Downloads folder as set in user-dirs.dirs (it is localized, e.g. ~/Загрузки), else ~/Downloads. */
    val downloads: File
        get() {
            val dirs = base("XDG_CONFIG_HOME", ".config").resolve("user-dirs.dirs")
            val line = runCatching { dirs.readLines() }.getOrDefault(emptyList()).firstOrNull { it.trim().startsWith("XDG_DOWNLOAD_DIR=") }
            val value = line?.substringAfter('=')?.trim()?.trim('"')?.replace("\$HOME", home.path)
            return value?.takeIf { it.startsWith("/") }?.let(::File) ?: home.resolve("Downloads")
        }

    private fun base(variable: String, default: String): File = absolute(variable) ?: home.resolve(default)

    // The spec says relative values are invalid and must be ignored.
    private fun absolute(variable: String): File? = env[variable]?.takeIf { it.startsWith("/") }?.let(::File)

    companion object {
        private const val APP = "md3-agent"

        fun currentUid(): Int = runCatching {
            java.nio.file.Files.getAttribute(java.nio.file.Path.of("/proc/self"), "unix:uid") as Int
        }.getOrDefault(0)
    }
}
