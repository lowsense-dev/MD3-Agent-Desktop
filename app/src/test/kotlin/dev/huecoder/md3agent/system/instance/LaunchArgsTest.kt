package dev.huecoder.md3agent.system.instance

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchArgsTest {
    @Test
    fun parsesEveryCommand() {
        assertEquals(LaunchCommand.Chat("abc"), LaunchArgs.parse(arrayOf("--chat", "abc")))
        assertEquals(LaunchCommand.Reminder("r1"), LaunchArgs.parse(arrayOf("--reminder", "r1")))
        assertEquals(LaunchCommand.Overlay, LaunchArgs.parse(arrayOf("--overlay")))
        assertEquals(LaunchCommand.Version, LaunchArgs.parse(arrayOf("--version")))
        assertEquals(LaunchCommand.CheckEnvironment, LaunchArgs.parse(arrayOf("--check-environment")))
    }

    @Test
    fun unknownOrIncompleteArgsJustShowTheWindow() {
        assertEquals(LaunchCommand.Show, LaunchArgs.parse(emptyArray()))
        assertEquals(LaunchCommand.Show, LaunchArgs.parse(arrayOf("--chat")))
        assertEquals(LaunchCommand.Show, LaunchArgs.parse(arrayOf("%U")))
        assertEquals(LaunchCommand.Show, LaunchArgs.parse(arrayOf("--nonsense")))
    }

    @Test
    fun commandsRoundTripThroughTheSocketLine() {
        listOf(LaunchCommand.Show, LaunchCommand.Overlay, LaunchCommand.Chat("a b"), LaunchCommand.Reminder("r1")).forEach {
            assertEquals(it, LaunchArgs.decode(LaunchArgs.encode(it)))
        }
    }
}
