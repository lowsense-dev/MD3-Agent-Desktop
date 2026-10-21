package dev.huecoder.md3agent.ui.text

import dev.huecoder.md3agent.R
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    private val english = """
        <resources>
            <string name="stop">Don\'t\nstop</string>
            <string name="quoted">"  keep spaces  "</string>
            <string name="only_english">Only here</string>
            <string name="count">%1${'$'}s of %2${'$'}d</string>
            <plurals name="actions">
                <item quantity="one">%1${'$'}d action</item>
                <item quantity="other">%1${'$'}d actions</item>
            </plurals>
        </resources>
    """.trimIndent()

    private val russian = """
        <resources>
            <string name="stop">Не \@останавливайся</string>
            <string name="count">%1${'$'}s из %2${'$'}d</string>
            <plurals name="actions">
                <item quantity="one">%1${'$'}d действие</item>
                <item quantity="few">%1${'$'}d действия</item>
                <item quantity="many">%1${'$'}d действий</item>
                <item quantity="other">%1${'$'}d действия</item>
            </plurals>
        </resources>
    """.trimIndent()

    private val en = StringTable(english, null, Locale.ENGLISH)
    private val ru = StringTable(english, russian, Locale.forLanguageTag("ru"))

    @Test
    fun unescapesApostropheAndNewline() {
        assertEquals("Don't\nstop", en.get("stop"))
        assertEquals("Не @останавливайся", ru.get("stop"))
    }

    @Test
    fun keepsSpacesInsideQuotes() {
        assertEquals("  keep spaces  ", en.get("quoted"))
    }

    @Test
    fun russianPluralForms() {
        assertEquals("1 действие", ru.plural("actions", 1, 1))
        assertEquals("3 действия", ru.plural("actions", 3, 3))
        assertEquals("5 действий", ru.plural("actions", 5, 5))
        assertEquals("21 действие", ru.plural("actions", 21, 21))
        assertEquals("11 действий", ru.plural("actions", 11, 11))
        assertEquals("1 action", en.plural("actions", 1, 1))
        assertEquals("2 actions", en.plural("actions", 2, 2))
    }

    @Test
    fun fallsBackToEnglishWhenKeyMissingInRussian() {
        assertEquals("Only here", ru.get("only_english"))
    }

    @Test
    fun formatsPositionalArgs() {
        assertEquals("a из 3", ru.get("count", "a", 3))
    }

    @Test
    fun generatedIdsResolveAgainstBundledResources() {
        Strings.locale = Locale.ENGLISH
        assertEquals("Couldn't load the model list", Strings.get(R.string.provider_models_fetch_failed))
    }
}
