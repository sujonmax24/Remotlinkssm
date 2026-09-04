package com.sujon.remotlinkssm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_devices")
data class TrustedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val pairedAt: Long,
    val lastConnectedAt: Long? = null,
    val capabilities: String = "camera,microphone,screen,remote_control"
)
