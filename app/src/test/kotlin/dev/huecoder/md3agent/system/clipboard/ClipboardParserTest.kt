package dev.huecoder.md3agent.system.clipboard

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardParserTest {
    @Test
    fun decodesPercentEncodedUri() {
        assertEquals(
            listOf(File("/home/u/Док/a b.pdf")),
            ClipboardParser.uriList("file:///home/u/%D0%94%D0%BE%D0%BA/a%20b.pdf"),
        )
    }

    @Test
    fun skipsCommentsAndNonFileSchemes() {
        val text = "# copied by Dolphin\nhttps://example.com/x.png\nfile:///tmp/one.txt\nsmb://server/share/f\n"
        assertEquals(listOf(File("/tmp/one.txt")), ClipboardParser.uriList(text))
    }

    @Test
    fun handlesCrLf() {
        assertEquals(listOf(File("/tmp/a"), File("/tmp/b")), ClipboardParser.uriList("file:///tmp/a\r\nfile:///tmp/b\r\n"))
    }

    @Test
    fun acceptsLocalhostHostAndGnomeCopiedFilesHeader() {
        // GNOME's x-special/gnome-copied-files puts "copy" or "cut" on the first line.
        assertEquals(listOf(File("/tmp/c d")), ClipboardParser.uriList("copy\nfile://localhost/tmp/c%20d"))
    }

    @Test
    fun plainTextIsNotAFileList() {
        assertEquals(emptyList(), ClipboardParser.uriList("just some words\nand more"))
    }
}
