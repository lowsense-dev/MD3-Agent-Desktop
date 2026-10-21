package dev.huecoder.md3agent.system.clipboard

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClipboardReaderTest {
    private val commands = mutableListOf<List<String>>()

    private fun reader(awt: ClipboardContent?, wayland: Boolean, outputs: Map<String, String>) = ClipboardReader(
        awt = { awt },
        wayland = wayland,
        run = { command ->
            commands += command
            outputs[command.joinToString(" ")]?.toByteArray()
        },
    )

    @Test
    fun awtFilesWinWithoutRunningAnything() = runBlocking {
        val files = ClipboardContent.Files(listOf(File("/tmp/x")))
        assertEquals(files, reader(files, wayland = true, emptyMap()).read())
        assertEquals(emptyList(), commands)
    }

    @Test
    fun filesComeFromWlPasteWhenAwtOnlySeesText() = runBlocking {
        val result = reader(
            ClipboardContent.Text("file:///tmp/a%20b.txt"),
            wayland = true,
            mapOf("wl-paste --list-types" to "text/plain\ntext/uri-list\n", "wl-paste --no-newline --type text/uri-list" to "file:///tmp/a%20b.txt\n"),
        ).read()
        assertEquals(ClipboardContent.Files(listOf(File("/tmp/a b.txt"))), result)
    }

    @Test
    fun imageComesFromXclipOnX11() = runBlocking {
        val result = reader(
            null,
            wayland = false,
            mapOf("xclip -selection clipboard -t TARGETS -o" to "TARGETS\nimage/png\n", "xclip -selection clipboard -t image/png -o" to "PNGDATA"),
        ).read()
        val image = assertIs<ClipboardContent.Image>(result)
        assertContentEquals("PNGDATA".toByteArray(), image.bytes)
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun textStaysTextWhenNoFilesOrImages() = runBlocking {
        val text = ClipboardContent.Text("hello")
        assertEquals(text, reader(text, wayland = true, mapOf("wl-paste --list-types" to "text/plain\n")).read())
    }

    @Test
    fun textComesFromWlPasteWhenAwtSeesNothing() = runBlocking {
        val result = reader(
            null,
            wayland = true,
            mapOf("wl-paste --list-types" to "text/plain;charset=utf-8\nUTF8_STRING\n", "wl-paste --no-newline --type text/plain;charset=utf-8" to "привет из Wayland"),
        ).read()
        assertEquals(ClipboardContent.Text("привет из Wayland"), result)
    }
}
