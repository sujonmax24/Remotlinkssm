package com.sujon.remotlinkssm.signaling

import org.json.JSONObject

/**
 * Small, transport-neutral envelope for the signaling server.
 * The server should only route messages; it must not need the private key.
 */
data class SignalingMessage(
    val type: Type,
    val sessionId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val timestamp: Long,
    val payload: String,
    val signature: String
) {
    enum class Type { HELLO, OFFER, ANSWER, ICE, END, REJECT }

    fun canonicalBytes(): ByteArray = canonical().toByteArray(Charsets.UTF_8)

    fun canonical(): String = listOf(
        type.name,
        sessionId,
        senderDeviceId,
        recipientDeviceId,
        timestamp.toString(),
        payload
    ).joinToString("|")

    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("type", type.name)
        put("sessionId", sessionId)
        put("senderDeviceId", senderDeviceId)
        put("recipientDeviceId", recipientDeviceId)
        put("timestamp", timestamp)
        put("payload", payload)
        put("signature", signature)
    }.toString()

    companion object {
        fun fromJson(raw: String): SignalingMessage {
            val json = JSONObject(raw)
            require(json.optInt("v") == 1) { "Unsupported signaling version" }
            return SignalingMessage(
                type = Type.valueOf(json.getString("type")),
                sessionId = json.getString("sessionId"),
                senderDeviceId = json.getString("senderDeviceId"),
                recipientDeviceId = json.getString("recipientDeviceId"),
                timestamp = json.getLong("timestamp"),
                payload = json.getString("payload"),
                signature = json.getString("signature")
            )
        }
    }
}
