package com.twinlore.app.data.ai

/** Who authored a message. Serialized by name; maps to Gemini roles user/model. */
enum class AiRole { USER, MODEL }

/**
 * One chat message. [imagePath] is an absolute path to a PNG copied into the
 * session's image dir, or null. [text] may be empty when only an image was sent.
 */
data class AiMessage(
    val role: AiRole = AiRole.USER,
    val text: String = "",
    val imagePath: String? = null,
    val timestamp: Long = 0L,
)

/** The full conversation for one vault. */
data class AiSession(
    val vaultId: String = "",
    val messages: List<AiMessage> = emptyList(),
)
