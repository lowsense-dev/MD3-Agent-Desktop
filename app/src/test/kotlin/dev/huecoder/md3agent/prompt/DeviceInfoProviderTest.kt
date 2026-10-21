package dev.huecoder.md3agent.prompt

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceInfoProviderTest {
    private val root = createTempDirectory("md3-sys").toFile()

    private fun provider() = DeviceInfoProvider(
        osRelease = root.resolve("os-release"),
        productName = root.resolve("product_name"),
        powerSupply = root.resolve("power_supply"),
        hostname = { "box" },
        kernel = { "6.9.1" },
    )

    @Test
    fun osVersionUsesPrettyNameAndKernel() {
        root.resolve("os-release").writeText("NAME=\"Fedora Linux\"\nPRETTY_NAME=\"Fedora Linux 44 (Workstation Edition)\"\n")
        assertEquals("Fedora Linux 44 (Workstation Edition), kernel 6.9.1", provider().osVersion())
    }

    @Test
    fun osVersionWithoutOsReleaseIsJustLinux() {
        assertEquals("Linux, kernel 6.9.1", provider().osVersion())
    }

    @Test
    fun deviceNameFromDmiOrHostname() {
        assertEquals("box", provider().deviceName())
        root.resolve("product_name").writeText("ThinkPad X1 Carbon Gen 12\n")
        assertEquals("ThinkPad X1 Carbon Gen 12", provider().deviceName())
    }

    @Test
    fun batteryFromSysfs() {
        assertNull(provider().batteryDescription())
        root.resolve("power_supply/BAT0").apply { mkdirs() }.also {
            it.resolve("capacity").writeText("73\n")
            it.resolve("status").writeText("Charging\n")
        }
        assertEquals("73%, charging", provider().batteryDescription())
    }
}
