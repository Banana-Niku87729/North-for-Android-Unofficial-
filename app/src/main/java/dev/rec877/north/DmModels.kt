package dev.rec877.north

import kotlinx.serialization.Serializable

@Serializable
data class DmConversation(
    val id: String,
    val name: String? = null,
    val group: Boolean = false,
    val participants: List<User> = emptyList(),
    val lastMessage: DmMessage? = null,
    val unreadCount: Int = 0,
    val request: Boolean = false,
    val updatedAt: String = "",
) {
    fun title(viewerId: String?) = name?.takeIf { it.isNotBlank() }
        ?: participants.filter { it.id != viewerId }.joinToString { it.name.ifBlank { "@${it.handle}" } }
            .ifBlank { "メッセージ" }
}

@Serializable
data class DmMessage(
    val id: String,
    val conversationId: String,
    val sender: User,
    val text: String = "",
    val createdAt: String,
    val editedAt: String? = null,
    val read: Boolean = false,
    val system: Boolean = false,
    val media: List<Media> = emptyList(),
    val replyTo: DmReplyTo? = null,
    val tweet: Post? = null,
)

@Serializable
data class DmReplyTo(val id: String, val deleted: Boolean = false, val text: String = "")

@Serializable
data class DmConversationPage(
    val items: List<DmConversation>, val nextCursor: String? = null, val requestCount: Int = 0,
)

@Serializable
data class DmMessagePage(
    val items: List<DmMessage>, val nextCursor: String? = null, val conversation: DmConversation? = null,
)
