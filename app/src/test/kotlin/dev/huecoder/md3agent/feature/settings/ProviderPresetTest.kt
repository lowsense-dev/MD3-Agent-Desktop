package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.data.settings.ApiFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderPresetTest {
    private val blank = ProviderForm(id = "p", isNew = true)

    @Test
    fun presetFillsFormatUrlAndName() {
        val form = blank.withPreset(ProviderPreset.OPENROUTER)
        assertEquals(ApiFormat.OPENAI, form.format)
        assertEquals("https://openrouter.ai/api/v1", form.baseUrl)
        assertEquals("OpenRouter", form.name)
        assertEquals(ProviderPreset.OPENROUTER, form.preset)
    }

    @Test
    fun switchingPresetsReplacesPresetNameButKeepsTypedName() {
        assertEquals("DeepSeek", blank.withPreset(ProviderPreset.GOOGLE).withPreset(ProviderPreset.DEEPSEEK).name)
        assertEquals("Work key", blank.copy(name = "Work key").withPreset(ProviderPreset.ANTHROPIC).name)
    }

    @Test
    fun editedUrlNoLongerMatchesAPreset() {
        assertEquals(ProviderPreset.DEEPSEEK, blank.withPreset(ProviderPreset.DEEPSEEK).copy(baseUrl = "https://api.deepseek.com/v1/").preset)
        assertNull(blank.withPreset(ProviderPreset.DEEPSEEK).copy(baseUrl = "https://proxy.example/v1").preset)
    }
}
