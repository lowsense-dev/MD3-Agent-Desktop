package dev.huecoder.md3agent.system.files

import dev.huecoder.md3agent.system.dbus.FileFilter
import org.freedesktop.dbus.types.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePickerTest {
    @Test
    fun requestPathFollowsPortalConvention() {
        assertEquals("/org/freedesktop/portal/desktop/request/1_42/md3abc", FilePicker.requestPath("1_42", "md3abc"))
    }

    @Test
    fun imagesOnlyAddsAnImageFilter() {
        val options = FilePicker.openOptions(token = "t", multiple = true, imagesOnly = true)
        assertEquals(true, (options["multiple"] as Variant<*>).value)
        @Suppress("UNCHECKED_CAST")
        val filters = (options["filters"] as Variant<*>).value as List<FileFilter>
        assertTrue(filters.single().rules.single().pattern == "image/*")
    }

    @Test
    fun anyFileHasNoFilter() {
        assertTrue("filters" !in FilePicker.openOptions(token = "t", multiple = false, imagesOnly = false))
    }

    @Test
    fun urisBecomeFiles() {
        assertEquals(listOf(java.io.File("/home/u/a b.png")), FilePicker.filesFrom(listOf("file:///home/u/a%20b.png", "https://x/y")))
    }
}

class FilePickerMarshallingTest {
    @Test
    fun optionsSerializeOverTheBus() {
        val connection = dev.huecoder.md3agent.system.dbus.SessionBus.connect().connection
        org.junit.Assume.assumeTrue("no session bus", connection != null)
        // A service that does not exist: the call is encoded and sent, nothing opens, and the bus answers ServiceUnknown.
        val chooser = connection!!.getRemoteObject("org.md3agent.DoesNotExist", "/org/freedesktop/portal/desktop", dev.huecoder.md3agent.system.dbus.FileChooser::class.java)
        val error = runCatching { chooser.OpenFile("", "t", FilePicker.openOptions(token = "t", multiple = true, imagesOnly = true)) }.exceptionOrNull()
        assertTrue(error?.toString().orEmpty().contains("ServiceUnknown"), "unexpected: $error")
    }
}
