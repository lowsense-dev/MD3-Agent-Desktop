package dev.huecoder.md3agent.prompt

import java.io.File
import java.net.InetAddress

/** Facts about this computer for the system prompt, read from the usual Linux files. */
class DeviceInfoProvider(
    private val osRelease: File = File("/etc/os-release"),
    private val productName: File = File("/sys/class/dmi/id/product_name"),
    private val powerSupply: File = File("/sys/class/power_supply"),
    private val hostname: () -> String = { runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("computer") },
    private val kernel: () -> String = { System.getProperty("os.version") },
) {
    fun deviceName(): String = readTrimmed(productName)?.takeUnless { it in PLACEHOLDER_PRODUCTS } ?: hostname()

    fun osVersion(): String {
        val pretty = runCatching { osRelease.readLines() }.getOrDefault(emptyList())
            .firstOrNull { it.startsWith("PRETTY_NAME=") }
            ?.substringAfter('=')?.trim()?.trim('"')
            ?.takeIf { it.isNotEmpty() }
        return "${pretty ?: "Linux"}, kernel ${kernel()}"
    }

    /** Null on a machine without a battery. */
    fun batteryDescription(): String? {
        val battery = powerSupply.listFiles()?.sortedBy { it.name }?.firstOrNull { it.name.startsWith("BAT") } ?: return null
        val level = readTrimmed(battery.resolve("capacity"))?.toIntOrNull()?.takeIf { it in 0..100 } ?: return null
        return if (readTrimmed(battery.resolve("status")) == "Charging") "$level%, charging" else "$level%"
    }

    private fun readTrimmed(file: File): String? = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        // What many desktop boards report instead of a real model name.
        val PLACEHOLDER_PRODUCTS = setOf("To Be Filled By O.E.M.", "System Product Name", "Default string", "None")
    }
}
