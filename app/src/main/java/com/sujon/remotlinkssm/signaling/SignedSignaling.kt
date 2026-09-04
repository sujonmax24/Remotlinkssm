package com.sujon.remotlinkssm.signaling

import com.sujon.remotlinkssm.security.DeviceKeyManager
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SignedSignaling(
    private val localDeviceId: String,
    private val keyManager: DeviceKeyManager = DeviceKeyManager()
) {
    fun create(
        type: SignalingMessage.Type,
        sessionId: String,
        recipientDeviceId: String,
        payload: String,
        timestamp: Long = System.currentTimeMillis()
    ): SignalingMessage {
        val unsigned = SignalingMessage(
            type = type,
            sessionId = sessionId,
            senderDeviceId = localDeviceId,
            recipientDeviceId = recipientDeviceId,
            timestamp = timestamp,
            payload = payload,
            signature = ""
        )
        return unsigned.copy(signature = keyManager.sign(unsigned.canonicalBytes()))
    }

    fun verify(message: SignalingMessage, expectedPublicKeyBase64: String, maxAgeMs: Long = 60_000): Boolean {
        if (message.recipientDeviceId != localDeviceId) return false
        if (kotlin.math.abs(System.currentTimeMillis() - message.timestamp) > maxAgeMs) return false
        return runCatching {
            val publicKey = decodePublicKey(expectedPublicKeyBase64)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(message.copy(signature = "").canonicalBytes())
            verifier.verify(Base64.getDecoder().decode(message.signature))
        }.getOrDefault(false)
    }

    private fun decodePublicKey(value: String): PublicKey {
        val bytes = Base64.getDecoder().decode(value)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }
}
