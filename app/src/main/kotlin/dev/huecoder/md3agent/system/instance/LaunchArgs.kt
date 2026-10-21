package dev.huecoder.md3agent.system.instance

sealed interface LaunchCommand {
    data object Show : LaunchCommand
    data object Overlay : LaunchCommand
    data class Chat(val id: String) : LaunchCommand
    data class Reminder(val id: String) : LaunchCommand
    data object Version : LaunchCommand
    data object CheckEnvironment : LaunchCommand
}

/** Command-line flags, and the one-line form a second launch sends to the running window. */
object LaunchArgs {
    fun parse(args: Array<String>): LaunchCommand {
        val value = { flag: String -> args.indexOf(flag).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.takeUnless { it.startsWith("--") } }
        return when {
            "--version" in args -> LaunchCommand.Version
            "--check-environment" in args -> LaunchCommand.CheckEnvironment
            "--overlay" in args -> LaunchCommand.Overlay
            value("--chat") != null -> LaunchCommand.Chat(value("--chat")!!)
            value("--reminder") != null -> LaunchCommand.Reminder(value("--reminder")!!)
            else -> LaunchCommand.Show
        }
    }

    fun encode(command: LaunchCommand): String = when (command) {
        is LaunchCommand.Chat -> "chat\t${command.id}"
        is LaunchCommand.Reminder -> "reminder\t${command.id}"
        LaunchCommand.Overlay -> "overlay"
        else -> "show"
    }

    fun decode(line: String): LaunchCommand? {
        val kind = line.substringBefore('\t')
        val arg = line.substringAfter('\t', "")
        return when (kind) {
            "show" -> LaunchCommand.Show
            "overlay" -> LaunchCommand.Overlay
            "chat" -> arg.takeIf { it.isNotEmpty() }?.let(LaunchCommand::Chat)
            "reminder" -> arg.takeIf { it.isNotEmpty() }?.let(LaunchCommand::Reminder)
            else -> null
        }
    }
}
