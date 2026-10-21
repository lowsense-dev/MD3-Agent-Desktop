package dev.huecoder.md3agent.system.environment

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EnvironmentReportTest {
    private val ubuntu = Distro("ubuntu", listOf("debian"), "Ubuntu 24.04 LTS")

    @Test
    fun formatNamesPackageForMissingComponent() {
        val text = EnvironmentReport.format(listOf(ComponentStatus(Component.BWRAP, found = false, detail = null)), ubuntu)
        assertContains(text, "Ubuntu 24.04 LTS")
        assertContains(text, "sudo apt install bubblewrap")
    }

    @Test
    fun foundComponentsHaveNoInstallHint() {
        val text = EnvironmentReport.format(listOf(ComponentStatus(Component.PYTHON, found = true, detail = "Python 3.12.3")), ubuntu)
        assertContains(text, "Python 3.12.3")
        assertFalse(text.contains("install"))
    }

    @Test
    fun packageNamesDifferPerDistro() {
        assertEquals("python3-venv", Component.PYTHON_VENV.packageName(PackageManager.APT))
        assertEquals("python", Component.PYTHON.packageName(PackageManager.PACMAN))
        assertEquals("wl-clipboard", Component.WL_PASTE.packageName(PackageManager.DNF))
    }

    @Test
    fun installCommandPerPackageManager() {
        assertEquals("sudo dnf install bubblewrap", PackageManager.DNF.installCommand("bubblewrap"))
        assertEquals("sudo zypper install bubblewrap", PackageManager.ZYPPER.installCommand("bubblewrap"))
        assertEquals("sudo pacman -S bubblewrap", PackageManager.PACMAN.installCommand("bubblewrap"))
        assertEquals("bubblewrap", PackageManager.UNKNOWN.installCommand("bubblewrap"))
    }
}
