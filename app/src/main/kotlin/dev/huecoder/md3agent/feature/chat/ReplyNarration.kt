package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.voice.ReplySpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReplyNarration(private val speaker: ReplySpeaker) {
    private var armed = false
    private var armedOverId: String? = null
    private var followingId: String? = null
    private val _listening = MutableStateFlow(false)

    /** True while a reply is awaited or being spoken; only then does the narration need the chat's replies. */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    fun arm(latestReplyId: String?) {
        armed = true
        armedOverId = latestReplyId
        _listening.value = true
    }

    fun disarm() {
        armed = false
        followingId = null
        _listening.value = false
    }

    fun onLatest(reply: ChatItem.Reply) {
        if (armed && (reply.id != armedOverId || reply.isActive)) {
            armed = false
            followingId = reply.id
        }
        if (reply.id != followingId) return
        val text = reply.spokenText()
        if (reply.isActive) {
            speaker.follow(reply.id, text)
        } else {
            speaker.finish(reply.id, text)
            followingId = null
            _listening.value = armed
        }
    }
}

fun ChatItem.Reply.spokenText(): String = parts.filterIsInstance<ReplyPart.Text>().joinToString("\n\n") { it.content }
