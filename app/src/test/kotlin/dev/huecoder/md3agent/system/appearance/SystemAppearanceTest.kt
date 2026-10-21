package dev.huecoder.md3agent.system.appearance

import dev.huecoder.md3agent.system.dbus.SessionBus
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemAppearanceTest {
    @Test
    fun colorSchemeValuesMapToDarkMode() {
        assertEquals(true, SystemAppearance.darkFromColorScheme(1))
        assertEquals(false, SystemAppearance.darkFromColorScheme(2))
        assertEquals(null, SystemAppearance.darkFromColorScheme(0))
        assertEquals(null, SystemAppearance.darkFromColorScheme(7))
    }

    @Test
    fun unknownWithoutBus() {
        assertEquals(null, SystemAppearance(SessionBus.unavailable()).readDarkMode())
    }
}
