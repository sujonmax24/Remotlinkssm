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

    fun newSession(): Pair<PairingSession, String> {
        val code = PairingCode.generate()
        val session = PairingSession(
            sessionId = UUID.randomUUID().toString(),
            deviceId = localDeviceId(),
            deviceName = localDeviceName(),
            publicKey = keyManager.publicKeyBase64(),
            codeHash = PairingCode.sha256(code),
            createdAt = System.currentTimeMillis()
        )
        return session to code
    }

    suspend fun trust(session: PairingSession) {
        dao.upsert(
            TrustedDeviceEntity(
                deviceId = session.deviceId,
                deviceName = session.deviceName,
                publicKey = session.publicKey,
                pairedAt = System.currentTimeMillis(),
                lastConnectedAt = null
            )
        )
    }

    suspend fun revoke(deviceId: String) = dao.revoke(deviceId)

    suspend fun markConnected(deviceId: String) {
        val devices = dao.observeAll()
        // Actual transport will update this atomically in the connection repository.
        // This method is intentionally not used until signaling/WebRTC is available.
        @Suppress("UNUSED_VARIABLE")
        val ignored = devices
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
