package com.sujon.remotlinkssm.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Base64

class DeviceKeyManager {
    private val alias = "remotelink_device_identity"
    private val keyStoreType = "AndroidKeyStore"

    fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                keyStoreType
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .build()
            )
            generator.generateKeyPair()
        }

        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    fun publicKeyBase64(): String = Base64.getEncoder().encodeToString(getOrCreateKeyPair().public.encoded)
}
