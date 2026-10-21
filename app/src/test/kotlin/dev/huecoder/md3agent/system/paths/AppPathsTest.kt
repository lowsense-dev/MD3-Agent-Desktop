package dev.huecoder.md3agent.system.paths

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPathsTest {
    private val home = File("/home/u")

    @Test
    fun usesXdgVariablesWhenSet() {
        val paths = AppPaths(
            env = mapOf("XDG_DATA_HOME" to "/d", "XDG_CONFIG_HOME" to "/c", "XDG_STATE_HOME" to "/s", "XDG_CACHE_HOME" to "/k", "XDG_RUNTIME_DIR" to "/run/user/1000"),
            home = home,
            uid = 1000,
        )
        assertEquals(File("/d/md3-agent"), paths.data)
        assertEquals(File("/c/md3-agent"), paths.config)
        assertEquals(File("/s/md3-agent"), paths.state)
        assertEquals(File("/k/md3-agent"), paths.cache)
        assertEquals(File("/run/user/1000"), paths.runtime)
        assertEquals(File("/d/md3-agent/workspace"), paths.workspace)
        assertEquals(File("/d/md3-agent/md3agent.db"), paths.database)
    }

    @Test
    fun fallsBackToHomeDefaults() {
        val paths = AppPaths(env = emptyMap(), home = home, uid = 1000)
        assertEquals(File("/home/u/.local/share/md3-agent"), paths.data)
        assertEquals(File("/home/u/.config/md3-agent"), paths.config)
        assertEquals(File("/home/u/.local/state/md3-agent"), paths.state)
        assertEquals(File("/home/u/.cache/md3-agent"), paths.cache)
    }

    @Test
    fun relativeXdgValuesAreIgnored() {
        val paths = AppPaths(env = mapOf("XDG_DATA_HOME" to "relative/dir"), home = home, uid = 1000)
        assertEquals(File("/home/u/.local/share/md3-agent"), paths.data)
    }

    @Test
    fun downloadsComeFromUserDirsFile() {
        val config = kotlin.io.path.createTempDirectory("md3-cfg").toFile()
        config.resolve("user-dirs.dirs").writeText("# comment\nXDG_DOWNLOAD_DIR=\"${'$'}HOME/Загрузки\"\n")
        val paths = AppPaths(env = mapOf("XDG_CONFIG_HOME" to config.path), home = home, uid = 1000)
        assertEquals(File("/home/u/Загрузки"), paths.downloads)
    }

    @Test
    fun downloadsDefaultToHomeDownloads() {
        val paths = AppPaths(env = mapOf("XDG_CONFIG_HOME" to "/nonexistent"), home = home, uid = 1000)
        assertEquals(File("/home/u/Downloads"), paths.downloads)
    }

    @Test
    fun runtimeFallsBackToTmpWithUid() {
        val paths = AppPaths(env = emptyMap(), home = home, uid = 1234)
        assertEquals(File("/tmp/md3-agent-1234"), paths.runtime)
    }
}
