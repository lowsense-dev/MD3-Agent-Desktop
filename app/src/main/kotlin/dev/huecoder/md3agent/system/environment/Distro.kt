package dev.huecoder.md3agent.system.environment

import java.io.File

/** The running distribution, from os-release, to name the right package in install hints. */
data class Distro(val id: String, val idLike: List<String>, val pretty: String) {
    companion object {
        fun read(osRelease: File = File("/etc/os-release")): Distro {
            val fields = runCatching { osRelease.readLines() }.getOrDefault(emptyList())
                .mapNotNull { line -> line.substringBefore('=', "").takeIf { it.isNotBlank() }?.let { it to line.substringAfter('=').trim().trim('"', '\'') } }
                .toMap()
            return Distro(
                id = fields["ID"].orEmpty().lowercase(),
                idLike = fields["ID_LIKE"].orEmpty().lowercase().split(' ').filter { it.isNotBlank() },
                pretty = fields["PRETTY_NAME"]?.takeIf { it.isNotBlank() } ?: fields["NAME"]?.takeIf { it.isNotBlank() } ?: "Linux",
            )
        }
    }
}

enum class PackageManager {
    APT, DNF, ZYPPER, PACMAN, UNKNOWN;

    fun installCommand(pkg: String): String = when (this) {
        APT -> "sudo apt install $pkg"
        DNF -> "sudo dnf install $pkg"
        ZYPPER -> "sudo zypper install $pkg"
        PACMAN -> "sudo pacman -S $pkg"
        UNKNOWN -> pkg
    }
}

fun Distro.packageManager(): PackageManager {
    val family = listOf(id) + idLike
    return when {
        family.any { it in setOf("debian", "ubuntu") } -> PackageManager.APT
        family.any { it in setOf("fedora", "rhel", "centos") } -> PackageManager.DNF
        family.any { it == "suse" || it.startsWith("opensuse") } -> PackageManager.ZYPPER
        family.any { it == "arch" } -> PackageManager.PACMAN
        else -> PackageManager.UNKNOWN
    }
}
