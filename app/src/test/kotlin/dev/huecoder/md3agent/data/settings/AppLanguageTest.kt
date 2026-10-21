package dev.huecoder.md3agent.data.settings

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.text.Strings
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {
    @Test
    fun systemFollowsTheStartLocaleOthersOverrideIt() {
        val start = Locale.forLanguageTag("en-US")
        assertEquals(start, AppLanguage.SYSTEM.locale(start))
        assertEquals("ru", AppLanguage.RUSSIAN.locale(start).language)
        assertEquals("en", AppLanguage.ENGLISH.locale(Locale.forLanguageTag("ru-RU")).language)
    }

    @Test
    fun switchingTheLocaleSwitchesStrings() {
        Strings.locale = AppLanguage.RUSSIAN.locale()
        assertEquals("Язык", Strings.get(R.string.appearance_section_language))
        Strings.locale = AppLanguage.ENGLISH.locale()
        assertEquals("Language", Strings.get(R.string.appearance_section_language))
    }
}

class SystemLocaleTest {
    @Test
    fun languageComesFromTheEnvironmentEvenWithoutAGeneratedLocale() {
        val fallback = Locale.ENGLISH
        assertEquals("ru", dev.huecoder.md3agent.ui.text.systemLocale(mapOf("LANG" to "ru_RU.UTF-8"), fallback).language)
        assertEquals("de", dev.huecoder.md3agent.ui.text.systemLocale(mapOf("LANG" to "ru_RU.UTF-8", "LC_MESSAGES" to "de_DE.UTF-8"), fallback).language)
        assertEquals("fr", dev.huecoder.md3agent.ui.text.systemLocale(mapOf("LC_ALL" to "fr_FR", "LANG" to "ru_RU.UTF-8"), fallback).language)
        assertEquals(fallback, dev.huecoder.md3agent.ui.text.systemLocale(mapOf("LANG" to "C.UTF-8"), fallback))
        assertEquals(fallback, dev.huecoder.md3agent.ui.text.systemLocale(emptyMap(), fallback))
    }
}
