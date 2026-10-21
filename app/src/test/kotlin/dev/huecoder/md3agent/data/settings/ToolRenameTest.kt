package dev.huecoder.md3agent.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRenameTest {
    @Test
    fun `a permission saved for memory_save carries over to memory_add`() {
        val migrated = ToolSettings(permissions = mapOf("memory_save" to ToolPermission.OFF, "web_search" to ToolPermission.ASK)).migrated()

        assertEquals(mapOf("memory_add" to ToolPermission.OFF, "web_search" to ToolPermission.ASK), migrated.permissions)
    }

    @Test
    fun `an agent override for memory_save applies to memory_add`() {
        val agent = AgentProfile(toolPermissions = mapOf("memory_save" to ToolPermission.ASK))

        assertEquals(ToolPermission.ASK, agent.tools(ToolSettings()).permissions["memory_add"])
    }
}
