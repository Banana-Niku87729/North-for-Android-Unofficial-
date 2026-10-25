package dev.rec877.north

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DmModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun conversationPagePreservesOpaqueCursorAndRequestState() {
        val page = json.decodeFromString<Envelope<DmConversationPage>>("""
            {"data":{"items":[{"id":"c1","group":false,"participants":[
                {"id":"me","name":"自分"},{"id":"other","name":"相手"}],
                "lastMessage":null,"unreadCount":3,"request":true}],
                "nextCursor":"opaque+/=署名","requestCount":1}}
        """).data
        assertEquals("opaque+/=署名", page.nextCursor)
        assertEquals("相手", page.items.single().title("me"))
        assertTrue(page.items.single().request)
        assertEquals(3, page.items.single().unreadCount)
    }

    @Test fun messagesDecodeMediaRepliesReadStatusAndUnknownFields() {
        val page = json.decodeFromString<Envelope<DmMessagePage>>("""
            {"data":{"items":[{"id":"m1","conversationId":"c1",
                "sender":{"id":"other","name":"相手"},"text":"こんにちは😀",
                "createdAt":"2026-10-25T03:00:00Z","read":true,
                "media":[{"id":"a1","url":"/media/private.webp","kind":"PHOTO"}],
                "replyTo":{"id":"m0","deleted":true,"sender":null,"text":"","hasMedia":false},
                "reactions":[],"futureField":true}],"nextCursor":null}}
        """).data
        val message = page.items.single()
        assertEquals("こんにちは😀", message.text)
        assertTrue(message.read)
        assertTrue(message.replyTo!!.deleted)
        assertEquals("/media/private.webp", message.media.single().url)
        assertNull(page.nextCursor)
    }

    @Test fun groupTitleAndMinimalConversationAreSupported() {
        val conversation = json.decodeFromString<DmConversation>("""
            {"id":"group","group":true,"participants":[],"name":"開発チーム"}
        """)
        assertEquals("開発チーム", conversation.title("me"))
        assertEquals(0, conversation.unreadCount)
        assertFalse(conversation.request)
        assertEquals("メッセージ", conversation.copy(name = null).title("me"))
    }
}
