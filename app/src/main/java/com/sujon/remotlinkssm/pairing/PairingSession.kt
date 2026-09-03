package com.sujon.remotlinkssm.pairing

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

private const val CODE_LENGTH = 6
private const val CODE_ALPHABET = "0123456789"
private const val SESSION_TTL_MS = 5 * 60 * 1000L

/** QR payload used only for first-time pairing bootstrap. */
data class PairingSession(
    val sessionId: String,
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val codeHash: String,
    val createdAt: Long
) {
    fun toQrPayload(): String = JSONObject().apply {
        put("v", 1)
        put("type", "remotelink_pair")
        put("sessionId", sessionId)
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("publicKey", publicKey)
        put("codeHash", codeHash)
        put("createdAt", createdAt)
    }.toString()

    companion object {
        fun fromQrPayload(payload: String): PairingSession {
            val json = JSONObject(payload)
            require(json.optString("type") == "remotelink_pair") { "Invalid RemoteLink QR code" }
            require(json.optInt("v") == 1) { "Unsupported pairing version" }
            val createdAt = json.getLong("createdAt")
            val age = System.currentTimeMillis() - createdAt
            require(age in 0..SESSION_TTL_MS) { "Pairing QR has expired. Generate a new QR code." }
            return PairingSession(
                sessionId = json.getString("sessionId"),
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                publicKey = json.getString("publicKey"),
                codeHash = json.getString("codeHash"),
                createdAt = createdAt
            )
        }
    }
}

object PairingCode {
    fun generate(): String {
        val random = SecureRandom()
        return buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
        }
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
