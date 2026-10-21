package dev.huecoder.md3agent.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException

class SettingsRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class CountingCipher : TextCipher {
        var decryptions = 0
        var broken = false

        override fun encrypt(plainText: String) = PREFIX + plainText

        override fun decrypt(encoded: String): String {
            decryptions++
            if (broken) throw GeneralSecurityException("keystore unavailable")
            return encoded.removePrefix(PREFIX)
        }

        private companion object {
            const val PREFIX = "sealed:"
        }
    }

    private val cipher = CountingCipher()

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(temp.root, "settings.preferences_pb")
    }

    private fun TestScope.repository(store: DataStore<Preferences> = store()) = SettingsRepository(store, cipher, backgroundScope)

    private val provider = ProviderProfile(id = "p1", name = "Local", baseUrl = "http://localhost", apiKey = "secret")

    @Test
    fun `many subscribers cost one decryption per stored blob`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.updateTools { it.copy(access = it.access.copy(sandboxNetwork = false)) }
        repository.updateVoice { it.copy(language = "ru") }
        runCurrent()
        cipher.decryptions = 0

        val subscribers = List(SUBSCRIBERS) { backgroundScope.launch { repository.settings.first() } }
        runCurrent()
        subscribers.forEach { it.join() }

        assertEquals(0, cipher.decryptions)
        assertEquals(listOf(provider), repository.settings.first().providers)
    }

    @Test
    fun `changing chat settings decrypts nothing`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.updateTools { it.copy(access = it.access.copy(sandboxNetwork = false)) }
        runCurrent()
        cipher.decryptions = 0

        repository.updateChat { it.copy(showTokenUsage = false) }
        runCurrent()

        assertEquals(0, cipher.decryptions)
        assertFalse(repository.settings.first().chat.showTokenUsage)
    }

    @Test
    fun `changing tools decrypts only the tools blob once`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.updateTools { it.copy(access = it.access.copy(sandboxNetwork = true)) }
        runCurrent()
        cipher.decryptions = 0

        repository.updateTools { it.copy(access = it.access.copy(sandboxNetwork = false)) }
        runCurrent()
        repeat(SUBSCRIBERS) { repository.settings.first() }

        assertTrue("decrypted ${cipher.decryptions} times", cipher.decryptions <= 1)
    }

    @Test
    fun `current reads the value that was just written`() = runTest {
        val repository = repository()

        repository.updateTools { it.copy(access = it.access.copy(sandboxNetwork = false)) }

        assertFalse(repository.current().tools.access.sandboxNetwork)
        repository.updateTools { it.copy(access = it.access.copy(sandboxNetwork = true)) }
        assertTrue(repository.current().tools.access.sandboxNetwork)
    }

    @Test
    fun `a failed decryption is retried instead of remembered`() = runTest {
        val store = store()
        val writer = repository(store)
        writer.upsertProvider(provider)
        runCurrent()

        cipher.broken = true
        val reader = repository(store)
        assertTrue(reader.current().providers.isEmpty())

        cipher.broken = false
        assertEquals(listOf(provider), reader.current().providers)
    }

    @Test
    fun `an edit during a failed decryption leaves the stored secrets alone`() = runTest {
        val store = store()
        val writer = repository(store)
        writer.upsertProvider(provider)
        writer.updateVoice { it.copy(language = "ru") }

        cipher.broken = true
        val reader = repository(store)
        reader.upsertProvider(provider.copy(id = "p2"))
        reader.updateVoice { it.copy(language = "en") }

        cipher.broken = false
        assertEquals(listOf(provider), reader.current().providers)
        assertEquals("ru", reader.current().voice.language)
    }

    @Test
    fun `a refused write reports itself`() = runTest {
        val store = store()
        repository(store).upsertProvider(provider)
        cipher.broken = true
        val reader = repository(store)
        val reported = backgroundScope.async { reader.writeFailures.first() }
        runCurrent()

        assertFalse("provider write", reader.upsertProvider(provider.copy(id = "p2")))
        assertFalse("voice write", reader.updateVoice { it.copy(language = "en") })
        runCurrent()
        assertTrue("reported", reported.isCompleted)
    }

    @Test
    fun `providers that no longer decode are kept, not replaced by defaults`() = runTest {
        val store = store()
        val unreadable = cipher.encrypt(PROVIDERS_FROM_NEWER_BUILD)
        store.edit { it[stringPreferencesKey(PROVIDERS_KEY)] = unreadable }
        val repository = repository(store)

        assertTrue(repository.current().providers.isEmpty())
        assertFalse(repository.upsertProvider(provider))
        assertEquals(unreadable, store.data.first()[stringPreferencesKey(PROVIDERS_KEY)])
    }

    @Test
    fun `plain settings that no longer decode are kept on write`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey(CHAT_KEY)] = CORRUPT_JSON }
        val repository = repository(store)

        assertFalse(repository.updateChat { it.copy(showTokenUsage = false) })
        assertEquals(CORRUPT_JSON, store.data.first()[stringPreferencesKey(CHAT_KEY)])
    }

    @Test
    fun `a provider saved with a model list by an older build still loads`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey(PROVIDERS_KEY)] = cipher.encrypt(PROVIDERS_WITH_MODEL_LIST) }

        assertEquals(listOf(provider), repository(store).current().providers)
    }

    @Test
    fun `a provider alone selects no model`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)

        assertNull(repository.current().activeSelection)

        repository.setActiveSelection(ModelSelection("p1", "m"))
        assertEquals(ModelSelection("p1", "m"), repository.current().activeSelection)
    }

    @Test
    fun `deleting the active provider leaves no selection`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.upsertProvider(provider.copy(id = "p2", name = "Other"))
        repository.setActiveSelection(ModelSelection("p1", "m"))

        repository.deleteProvider("p1")

        assertNull(repository.current().activeSelection)
        assertEquals(listOf("p2"), repository.current().providers.map { it.id })
    }

    private companion object {
        const val SUBSCRIBERS = 20
        const val PROVIDERS_KEY = "providers_encrypted"
        const val CHAT_KEY = "chat"
        const val CORRUPT_JSON = "{not json"
        const val PROVIDERS_FROM_NEWER_BUILD = """[{"id":"p1","name":"Local","baseUrl":"http://localhost","apiKey":"secret","format":"SOME_FUTURE_FORMAT"}]"""
        const val PROVIDERS_WITH_MODEL_LIST =
            """[{"id":"p1","name":"Local","baseUrl":"http://localhost","apiKey":"secret","models":["m","n"],"defaultModel":"m"}]"""
    }
}
