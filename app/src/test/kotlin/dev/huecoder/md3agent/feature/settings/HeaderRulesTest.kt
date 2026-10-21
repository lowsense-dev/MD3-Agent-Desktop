package dev.huecoder.md3agent.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderRulesTest {
    @Test
    fun `names follow the http token rules`() {
        listOf("X-Api-Key", "anthropic-beta", "x_custom.v2", "").forEach { assertTrue(it, HeaderRules.nameValid(it)) }
        listOf("Bad Name", "Ключ", "a:b", "x(y)").forEach { assertFalse(it, HeaderRules.nameValid(it)) }
    }

    @Test
    fun `values must be one line of printable ascii`() {
        assertTrue(HeaderRules.valueValid("Bearer abc.DEF-123\ttail"))
        assertFalse(HeaderRules.valueValid("line\r\nInjected: 1"))
        assertFalse(HeaderRules.valueValid("ключ"))
    }
}
