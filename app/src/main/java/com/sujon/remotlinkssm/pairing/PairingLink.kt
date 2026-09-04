package com.sujon.remotlinkssm.pairing

import android.net.Uri
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private const val LINK_TTL_MS = 10 * 60 * 1000L
private const val SCHEME = "remotelink"
private const val HOST = "pair"

/** One-click pairing invitation. It identifies the Camera Device and expires quickly. */
data class PairingLink(
    val sessionId: String,
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val signalingEndpoint: String,
    val token: String,
    val createdAt: Long
) {
    fun toUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendQueryParameter("v", "1")
        .appendQueryParameter("sid", sessionId)
        .appendQueryParameter("deviceId", deviceId)
        .appendQueryParameter("deviceName", deviceName)
        .appendQueryParameter("publicKey", publicKey)
        .appendQueryParameter("ws", signalingEndpoint)
        .appendQueryParameter("token", token)
        .appendQueryParameter("createdAt", createdAt.toString())
        .build()

    fun toShareText(): String = "Join my RemoteLink Camera Device:\n${toUri()}"

    companion object {
        fun create(deviceId: String, deviceName: String, publicKey: String, signalingEndpoint: String): PairingLink {
            require(signalingEndpoint.startsWith("wss://")) { "A secure wss:// signaling endpoint is required." }
            return PairingLink(
                sessionId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                deviceName = deviceName,
                publicKey = publicKey,
                signalingEndpoint = signalingEndpoint,
                token = randomToken(),
                createdAt = System.currentTimeMillis()
            )
        }

        fun fromUri(uri: Uri): PairingLink {
            require(uri.scheme.equals(SCHEME, ignoreCase = true) && uri.host == HOST) {
                "Invalid RemoteLink invitation"
            }
            require(uri.getQueryParameter("v") == "1") { "Unsupported invitation version" }
            val createdAt = uri.getQueryParameter("createdAt")?.toLongOrNull()
                ?: error("Invalid invitation timestamp")
            require(System.currentTimeMillis() - createdAt in 0..LINK_TTL_MS) {
                "This RemoteLink invitation has expired. Generate a new link."
            }
            val endpoint = uri.getQueryParameter("ws").orEmpty()
            require(endpoint.startsWith("wss://")) { "Invitation has no secure signaling endpoint." }
            return PairingLink(
                sessionId = uri.getQueryParameter("sid").orEmpty().also { require(it.isNotBlank()) },
                deviceId = uri.getQueryParameter("deviceId").orEmpty().also { require(it.isNotBlank()) },
                deviceName = uri.getQueryParameter("deviceName").orEmpty().also { require(it.isNotBlank()) },
                publicKey = uri.getQueryParameter("publicKey").orEmpty().also { require(it.isNotBlank()) },
                signalingEndpoint = endpoint,
                token = uri.getQueryParameter("token").orEmpty().also { require(it.isNotBlank()) },
                createdAt = createdAt
            )
        }

        private fun randomToken(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
