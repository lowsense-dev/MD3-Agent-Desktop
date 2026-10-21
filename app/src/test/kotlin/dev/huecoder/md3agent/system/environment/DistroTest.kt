package dev.huecoder.md3agent.system.environment

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DistroTest {
    private fun distro(text: String) = Distro.read(createTempDirectory("md3-os").toFile().resolve("os-release").apply { writeText(text) })

    @Test
    fun ubuntuMapsToApt() = assertEquals(PackageManager.APT, distro("ID=ubuntu\nID_LIKE=debian\nPRETTY_NAME=\"Ubuntu 24.04 LTS\"").packageManager())

    @Test
    fun mintIdLikeUbuntuMapsToApt() = assertEquals(PackageManager.APT, distro("ID=linuxmint\nID_LIKE=\"ubuntu debian\"").packageManager())

    @Test
    fun fedoraMapsToDnf() = assertEquals(PackageManager.DNF, distro("ID=fedora").packageManager())

    @Test
    fun tumbleweedMapsToZypper() = assertEquals(PackageManager.ZYPPER, distro("ID=\"opensuse-tumbleweed\"\nID_LIKE=\"opensuse suse\"").packageManager())

    @Test
    fun endeavourIdLikeArchMapsToPacman() = assertEquals(PackageManager.PACMAN, distro("ID=endeavouros\nID_LIKE=arch").packageManager())

    @Test
    fun missingFileGivesUnknown() {
        val missing = Distro.read(createTempDirectory("md3-os").toFile().resolve("nope"))
        assertEquals(PackageManager.UNKNOWN, missing.packageManager())
        assertEquals("Linux", missing.pretty)
    }

    @Test
    fun prettyNameIsUnquoted() = assertEquals("Arch Linux", distro("NAME=\"Arch Linux\"\nPRETTY_NAME=\"Arch Linux\"\nID=arch").pretty)
}
