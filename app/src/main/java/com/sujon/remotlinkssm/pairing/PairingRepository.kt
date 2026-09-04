package com.sujon.remotlinkssm.pairing

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.sujon.remotlinkssm.data.local.TrustedDeviceDao
import com.sujon.remotlinkssm.data.local.TrustedDeviceEntity
import com.sujon.remotlinkssm.security.DeviceKeyManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PairingRepository(
    private val context: Context,
    private val dao: TrustedDeviceDao,
    private val keyManager: DeviceKeyManager = DeviceKeyManager()
) {
    fun observeTrustedDevices(): Flow<List<TrustedDeviceEntity>> = dao.observeAll()

    fun localIdentity(): DeviceIdentityQr = DeviceIdentityQr(
        deviceId = localDeviceId(),
        deviceName = localDeviceName(),
        publicKey = keyManager.publicKeyBase64()
    )

    fun newPairingLink(signalingEndpoint: String): PairingLink = PairingLink.create(
        deviceId = localDeviceId(),
        deviceName = localDeviceName(),
        publicKey = keyManager.publicKeyBase64(),
        signalingEndpoint = signalingEndpoint
    )

    suspend fun trustLink(link: PairingLink) {
        require(link.deviceId != localDeviceId()) { "Cannot trust this device itself" }
        dao.upsert(
            TrustedDeviceEntity(
                deviceId = link.deviceId,
                deviceName = link.deviceName,
                publicKey = link.publicKey,
                pairedAt = System.currentTimeMillis(),
                lastConnectedAt = null
            )
        )
    }

    suspend fun trustIdentity(identity: DeviceIdentityQr) {
        require(identity.deviceId != localDeviceId()) { "Cannot trust this device itself" }
        dao.upsert(
            TrustedDeviceEntity(
                deviceId = identity.deviceId,
                deviceName = identity.deviceName,
                publicKey = identity.publicKey,
                pairedAt = System.currentTimeMillis(),
                lastConnectedAt = null
            )
        )
    }

    suspend fun revoke(deviceId: String) = dao.revoke(deviceId)

    suspend fun markConnected(deviceId: String) {
        dao.markConnected(deviceId, System.currentTimeMillis())
    }

    private fun localDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

    private fun localDeviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ") { it.trim() }
            .trim()
            .ifBlank { "Android Device" }
}
